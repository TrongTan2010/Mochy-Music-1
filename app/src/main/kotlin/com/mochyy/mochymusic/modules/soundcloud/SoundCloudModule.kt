package com.mochyy.mochymusic.modules.soundcloud

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.CatalogTrack
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.NO_ACCOUNT_LABEL
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.module.TrackMatcher
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * SoundCloud built-in playback source (no account required).
 * Dynamically extracts and caches SoundCloud's public web client_id, searches api-v2 tracks,
 * filters out 30-second snips/geoblocks, and resolves progressive MP3 or HLS Opus streams.
 */
class SoundCloudModule : SourceModule {

    override val info = ModuleInfo(
        id = Id,
        name = "SoundCloud",
        version = "1.0",
        description = "Nguồn phát nhạc thứ 2 từ SoundCloud (MP3 / HLS Opus), không cần đăng nhập. Hỗ trợ phát trực tiếp và tự động dự phòng khi YouTube lỗi.",
    )

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }
    private val matches = ConcurrentHashMap<String, ScTrack>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val clientIdLock = Any()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override val choices = listOf(
        ModuleChoice(
            id = ChoiceQuality,
            label = "Chất lượng SoundCloud",
            options = listOf(
                StreamOption(OptionAuto, "Tự động (Tốt nhất)", "Ưu tiên MP3 Progressive (tải offline được), tự chuyển HLS Opus khi cần"),
                StreamOption(OptionOpus, "HLS Opus (Chất lượng cao)", "Âm thanh Opus 48 kHz sắc nét, tiết kiệm băng thông"),
                StreamOption(OptionMp3, "MP3 Progressive", "Luồng MP3 nguyên tệp 44.1 kHz, tua nhanh và hỗ trợ tải offline"),
            ),
        ),
    )

    override fun accountState(): AccountState {
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn(NO_ACCOUNT_LABEL)
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) {
            AccountState.Expired(reason)
        } else {
            AccountState.SignedIn(NO_ACCOUNT_LABEL)
        }
    }

    override suspend fun verifyAccount(): AccountState {
        val clientId = obtainClientId(forceRefresh = true)
        return if (clientId != null) {
            host.storage.set("unreachable", null)
            AccountState.SignedIn("$NO_ACCOUNT_LABEL · SoundCloud sẵn sàng")
        } else {
            val reason = "Không lấy được kết nối SoundCloud"
            markUnreachable(reason)
            AccountState.Expired(reason)
        }
    }

    override fun signOut() {
        matches.clear()
        misses.clear()
        host.storage.set("client_id", null)
        host.storage.set("client_id_at", null)
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(
        track: TrackQuery,
        picks: Map<String, String>,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult {
        val key = "${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Tìm bài hát", false, "Không tìm thấy trên SoundCloud"))
            return ResolveResult.NotFound("Not on SoundCloud")
        }

        report(DiagnosticStep("Kết nối SoundCloud", null, "Đang lấy khóa truy cập…"))
        var clientId = obtainClientId(forceRefresh = false)
        if (clientId == null) {
            report(DiagnosticStep("Kết nối SoundCloud", false, "SoundCloud không phản hồi"))
            markUnreachable("SoundCloud không phản hồi")
            return ResolveResult.Unavailable("SoundCloud client_id unavailable")
        }
        report(DiagnosticStep("Kết nối SoundCloud", true, "Sẵn sàng"))

        report(DiagnosticStep("Tìm bài hát", null, "Đang tìm trên SoundCloud…"))
        val scTrack = matches[key] ?: run {
            val query = "${TrackMatcher.core(track.title)} ${track.artists.firstOrNull().orEmpty()}".trim()
            var response = runCatching {
                host.http(HttpRequest(searchUrl(query, clientId!!), headers = apiHeaders))
            }.getOrNull()

            // Refresh client_id once if SoundCloud rotated it
            if (response != null && (response.code == 401 || response.code == 403)) {
                clientId = obtainClientId(forceRefresh = true)
                if (clientId != null) {
                    response = runCatching {
                        host.http(HttpRequest(searchUrl(query, clientId!!), headers = apiHeaders))
                    }.getOrNull()
                }
            }

            if (response == null) {
                report(DiagnosticStep("Tìm bài hát", false, "SoundCloud không phản hồi"))
                markUnreachable("SoundCloud không phản hồi")
                return ResolveResult.Unavailable("SoundCloud unreachable")
            }
            if (!response.ok) {
                report(DiagnosticStep("Tìm bài hát", false, "SoundCloud lỗi HTTP ${response.code}"))
                return ResolveResult.Unavailable("SoundCloud HTTP ${response.code}")
            }

            host.storage.set("unreachable", null)
            val candidates = parseSearchTracks(response.text)
            val best = TrackMatcher.ranked(track, candidates.map { it.catalog }).firstOrNull()
                ?.let { scored -> candidates.firstOrNull { it.catalog === scored.track } }
                ?: candidates.firstOrNull { looseTitleMatch(track.title, it.catalog.title) }

            if (best == null) {
                rememberMiss(key)
                report(DiagnosticStep("Tìm bài hát", false, "Không có bản phát đầy đủ cho “${track.title}”"))
                return ResolveResult.NotFound("No matching full track on SoundCloud")
            }
            rememberMatch(key, best)
            best
        }
        report(DiagnosticStep("Tìm bài hát", true, "${scTrack.catalog.title} · ${scTrack.catalog.artist}"))

        report(DiagnosticStep("Lấy luồng phát", null, "Đang mở luồng SoundCloud…"))
        val pick = picks[ChoiceQuality] ?: OptionAuto
        val orderedTranscodings = orderTranscodings(scTrack.transcodings, pick)
        for (transcoding in orderedTranscodings) {
            val resolvedUrl = resolveTranscodingUrl(transcoding.url, clientId!!, scTrack.trackAuthorization)
                ?: continue
            val isProgressive = transcoding.protocol == "progressive"
            val isOpus = transcoding.mimeType.contains("opus", ignoreCase = true)
            val codec = when {
                isOpus -> "OPUS"
                transcoding.mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
                transcoding.mimeType.contains("mp4", ignoreCase = true) || transcoding.mimeType.contains("aac", ignoreCase = true) -> "AAC"
                else -> "MP3"
            }
            val sampleRate = if (isOpus) 48_000 else 44_100
            val qualityLabel = when {
                isOpus -> "HLS Opus"
                isProgressive -> "MP3 128"
                else -> "HLS $codec"
            }
            report(DiagnosticStep("Lấy luồng phát", true, qualityLabel))
            return ResolveResult.Found(
                SourceStream(
                    url = resolvedUrl,
                    format = if (isProgressive) StreamFormat.Progressive else StreamFormat.Hls,
                    headers = streamHeaders,
                    sourceLabel = "SoundCloud",
                    qualityLabel = qualityLabel,
                    codec = codec,
                    sampleRateHz = sampleRate,
                    matched = MatchedTrack(
                        id = scTrack.catalog.id,
                        title = scTrack.catalog.title,
                        artist = scTrack.catalog.artist,
                        album = scTrack.catalog.album,
                        durationMs = scTrack.catalog.durationMs,
                        confidence = TrackMatcher.score(track, scTrack.catalog),
                    ),
                )
            )
        }

        // Cached match may have stale transcoding/auth; drop it so next attempt re-fetches fresh
        matches.remove(key)
        report(DiagnosticStep("Lấy luồng phát", false, "Không mở được luồng phát"))
        return ResolveResult.Unavailable("Could not resolve SoundCloud stream URL")
    }

    private fun obtainClientId(forceRefresh: Boolean): String? = synchronized(clientIdLock) {
        if (!forceRefresh) {
            val cached = host.storage.get("client_id")
            val cachedAt = host.storage.get("client_id_at")?.toLongOrNull() ?: 0L
            if (!cached.isNullOrBlank() && System.currentTimeMillis() - cachedAt < ClientIdTtlMs) {
                return@synchronized cached
            }
        }
        val fresh = fetchClientIdFromWeb()
        if (!fresh.isNullOrBlank()) {
            host.storage.set("client_id", fresh)
            host.storage.set("client_id_at", System.currentTimeMillis().toString())
        }
        fresh
    }

    private fun fetchClientIdFromWeb(): String? {
        val homeResp = runCatching {
            host.http(HttpRequest("https://soundcloud.com", headers = webHeaders))
        }.getOrNull() ?: return null
        if (!homeResp.ok) return null

        val scriptUrls = ScriptUrlPattern.findAll(homeResp.text)
            .map { it.groupValues[1] }
            .toList()
            .asReversed()
            .take(5)

        for (scriptUrl in scriptUrls) {
            val jsResp = runCatching {
                host.http(HttpRequest(scriptUrl, headers = webHeaders))
            }.getOrNull() ?: continue
            if (!jsResp.ok) continue
            val match = ClientIdPattern.find(jsResp.text) ?: continue
            val candidate = match.groupValues[1]
            if (candidate.length in 24..40) return candidate
        }
        return null
    }

    private fun resolveTranscodingUrl(
        transcodingUrl: String,
        clientId: String,
        trackAuthorization: String?,
    ): String? {
        val separator = if ('?' in transcodingUrl) '&' else '?'
        val url = buildString {
            append(transcodingUrl)
            append(separator)
            append("client_id=").append(enc(clientId))
            if (!trackAuthorization.isNullOrBlank()) {
                append("&track_authorization=").append(enc(trackAuthorization))
            }
        }
        val resp = runCatching { host.http(HttpRequest(url, headers = apiHeaders)) }.getOrNull() ?: return null
        if (!resp.ok) return null
        val obj = runCatching { json.parseToJsonElement(resp.text) as? JsonObject }.getOrNull() ?: return null
        return obj.string("url")?.takeIf { it.startsWith("http") }
    }

    private fun parseSearchTracks(text: String): List<ScTrack> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val collection = root["collection"] as? JsonArray ?: return emptyList()
        return collection.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            if (obj.string("kind") != "track") return@mapNotNull null
            val policy = obj.string("policy").orEmpty()
            // Skip 30-second Go+ previews ("SNIP") and blocked tracks ("BLOCK")
            if (policy.equals("SNIP", ignoreCase = true) || policy.equals("BLOCK", ignoreCase = true)) {
                return@mapNotNull null
            }
            if ((obj["streamable"] as? JsonPrimitive)?.booleanOrNull == false) return@mapNotNull null

            val durationMs = (obj["duration"] as? JsonPrimitive)?.longOrNull ?: 0L
            val fullDurationMs = (obj["full_duration"] as? JsonPrimitive)?.longOrNull ?: durationMs
            // If playable duration is less than half of full_duration, it's a preview snip
            if (durationMs < 15_000L || (fullDurationMs > 0L && durationMs * 2 < fullDurationMs)) {
                return@mapNotNull null
            }

            val media = obj["media"] as? JsonObject ?: return@mapNotNull null
            val transcodingsArr = media["transcodings"] as? JsonArray ?: return@mapNotNull null
            val transcodings = transcodingsArr.mapNotNull { tElem ->
                val tObj = tElem as? JsonObject ?: return@mapNotNull null
                if ((tObj["snipped"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
                val url = tObj.string("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                val format = tObj["format"] as? JsonObject ?: return@mapNotNull null
                val protocol = format.string("protocol") ?: return@mapNotNull null
                val mimeType = format.string("mime_type").orEmpty()
                if (protocol != "progressive" && protocol != "hls") return@mapNotNull null
                ScTranscoding(url = url, protocol = protocol, mimeType = mimeType)
            }
            if (transcodings.isEmpty()) return@mapNotNull null

            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val title = obj.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val user = obj["user"] as? JsonObject
            val publisher = obj["publisher_metadata"] as? JsonObject
            val artist = publisher?.string("artist")?.takeIf { it.isNotBlank() }
                ?: user?.string("username")?.takeIf { it.isNotBlank() }
                ?: ""
            val album = publisher?.string("album_title")?.takeIf { it.isNotBlank() }
            val trackAuth = obj.string("track_authorization")

            ScTrack(
                catalog = CatalogTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                ),
                transcodings = transcodings,
                trackAuthorization = trackAuth,
            )
        }
    }

    private fun orderTranscodings(transcodings: List<ScTranscoding>, pick: String): List<ScTranscoding> =
        when (pick) {
            OptionOpus -> transcodings.sortedBy { t ->
                when {
                    t.protocol == "hls" && t.mimeType.contains("opus", ignoreCase = true) -> 0
                    t.protocol == "progressive" -> 1
                    else -> 2
                }
            }
            OptionMp3 -> transcodings.sortedBy { t ->
                when {
                    t.protocol == "progressive" && t.mimeType.contains("mpeg", ignoreCase = true) -> 0
                    t.protocol == "progressive" -> 1
                    else -> 2
                }
            }
            else -> transcodings.sortedBy { t ->
                // Auto: prefer progressive MP3 (fast seek + offline download support), then HLS Opus, then other HLS
                when {
                    t.protocol == "progressive" -> 0
                    t.protocol == "hls" && t.mimeType.contains("opus", ignoreCase = true) -> 1
                    else -> 2
                }
            }
        }

    private fun looseTitleMatch(wantedTitle: String, candidateTitle: String): Boolean {
        val core = TrackMatcher.core(wantedTitle).lowercase().trim()
        if (core.length < 3) return false
        return candidateTitle.lowercase().contains(core)
    }

    private fun rememberMatch(key: String, value: ScTrack) {
        if (matches.size >= MaxCacheEntries) matches.clear()
        matches[key] = value
    }

    private fun rememberMiss(key: String) {
        if (misses.size >= MaxCacheEntries) misses.clear()
        misses[key] = System.currentTimeMillis()
    }

    private fun markUnreachable(reason: String) {
        host.storage.set("unreachable", reason)
        host.storage.set("unreachable_at", System.currentTimeMillis().toString())
    }

    private fun searchUrl(query: String, clientId: String): String =
        "$ApiBase/search/tracks?q=${enc(query)}&client_id=${enc(clientId)}&limit=12&linked_partitioning=1"

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private class ScTrack(
        val catalog: CatalogTrack,
        val transcodings: List<ScTranscoding>,
        val trackAuthorization: String?,
    )

    private class ScTranscoding(
        val url: String,
        val protocol: String,
        val mimeType: String,
    )

    companion object {
        const val Id = "soundcloud"
        const val ChoiceQuality = "quality"
        const val OptionAuto = "auto"
        const val OptionOpus = "opus"
        const val OptionMp3 = "mp3"

        private const val ApiBase = "https://api-v2.soundcloud.com"
        private const val ClientIdTtlMs = 6L * 60 * 60 * 1000
        private const val MissMemoryMs = 20L * 60 * 1000
        private const val UnreachableRetryMs = 5L * 60 * 1000
        private const val MaxCacheEntries = 256

        private val ScriptUrlPattern = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
        private val ClientIdPattern = Regex("""client_id\s*[:=]\s*["']([a-zA-Z0-9]{24,40})["']""")

        private const val UserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
        private val webHeaders = mapOf(
            "User-Agent" to UserAgent,
            "Accept" to "text/html,application/javascript,*/*;q=0.8",
        )
        private val apiHeaders = mapOf(
            "User-Agent" to UserAgent,
            "Accept" to "application/json",
            "Origin" to "https://soundcloud.com",
            "Referer" to "https://soundcloud.com/",
        )
        private val streamHeaders = mapOf("User-Agent" to UserAgent)
    }
}
