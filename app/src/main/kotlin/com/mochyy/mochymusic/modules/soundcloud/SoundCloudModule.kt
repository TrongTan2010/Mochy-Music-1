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
import com.mochyy.mochymusic.module.SignInSpec
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

data class SoundCloudUser(
    val id: String,
    val username: String,
    val avatarUrl: String?,
)

data class SoundCloudTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val artworkUrl: String?,
    val playbackCount: Long = 0L,
)

/**
 * SoundCloud built-in playback source with optional OAuth sign-in.
 * Works without login via dynamically extracted public client_id, and when signed in
 * sends `Authorization: OAuth <token>` to unlock account likes & personalized access.
 */
class SoundCloudModule : SourceModule {

    override val info = ModuleInfo(
        id = Id,
        name = "SoundCloud",
        version = "1.1",
        description = "Nguồn phát nhạc thứ 2 từ SoundCloud (MP3 / HLS Opus). Hỗ trợ đăng nhập tài khoản để đồng bộ bài đã thích, tìm kiếm trực tiếp và tự động dự phòng khi YouTube lỗi.",
    )

    override val signIn = SignInSpec(
        startUrl = "https://soundcloud.com/signin",
        origins = listOf(
            "https://soundcloud.com",
            "https://secure.soundcloud.com",
            "https://accounts.google.com",
            "https://appleid.apple.com",
        ),
        sessionOrigins = setOf(
            "https://soundcloud.com",
            "https://api-v2.soundcloud.com",
            "https://api-auth.soundcloud.com",
        ),
        desktopSite = false,
        title = "Đăng nhập SoundCloud",
        subtitle = "Đăng nhập để đồng bộ bài hát đã thích và nghe nhạc trực tiếp từ tài khoản SoundCloud của bạn.",
    )

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }
    private val matches = ConcurrentHashMap<String, ScTrack>()
    private val directTracks = ConcurrentHashMap<String, ScTrack>()
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

    fun isUserSignedIn(): Boolean = !oauthToken().isNullOrBlank()

    fun currentUser(): SoundCloudUser? {
        if (!isUserSignedIn()) return null
        val username = host.storage.get(KeyUsername)?.takeIf { it.isNotBlank() } ?: return null
        val userId = host.storage.get(KeyUserId).orEmpty()
        val avatar = host.storage.get(KeyAvatarUrl)?.takeIf { it.isNotBlank() }
        return SoundCloudUser(id = userId, username = username, avatarUrl = avatar)
    }

    override fun onSignInHeader(name: String, value: String, url: String): Boolean {
        val lower = name.lowercase()
        val token = when {
            lower == "authorization" -> extractAuthToken(value)
            lower == "cookie" -> extractCookieToken(value)
            else -> null
        } ?: return false

        host.storage.set(KeyOAuthToken, token)
        host.storage.set("unreachable", null)
        return true
    }

    override fun accountState(): AccountState {
        val token = oauthToken()
        if (!token.isNullOrBlank()) {
            val username = host.storage.get(KeyUsername)?.takeIf { it.isNotBlank() }
            return AccountState.SignedIn(if (username != null) "@$username" else "Đã đăng nhập SoundCloud")
        }
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn(NO_ACCOUNT_LABEL)
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) {
            AccountState.Expired(reason)
        } else {
            AccountState.SignedIn(NO_ACCOUNT_LABEL)
        }
    }

    override suspend fun verifyAccount(): AccountState {
        val clientId = obtainClientId(forceRefresh = false) ?: obtainClientId(forceRefresh = true)
        if (clientId == null) {
            val reason = "Không lấy được kết nối SoundCloud"
            markUnreachable(reason)
            return AccountState.Expired(reason)
        }
        host.storage.set("unreachable", null)
        if (isUserSignedIn()) {
            val me = fetchMeInternal(clientId)
            if (me != null) {
                return AccountState.SignedIn("@${me.username}")
            }
            return AccountState.SignedIn("Đã đăng nhập SoundCloud")
        }
        return AccountState.SignedIn("$NO_ACCOUNT_LABEL · SoundCloud sẵn sàng")
    }

    /** Signs out the user's SoundCloud OAuth account while keeping public SoundCloud playback working. */
    fun signOutAccount() {
        host.storage.set(KeyOAuthToken, null)
        host.storage.set(KeyUserId, null)
        host.storage.set(KeyUsername, null)
        host.storage.set(KeyAvatarUrl, null)
        matches.clear()
        misses.clear()
    }

    override fun signOut() {
        signOutAccount()
        directTracks.clear()
        host.storage.set("client_id", null)
        host.storage.set("client_id_at", null)
    }

    suspend fun fetchMe(): SoundCloudUser? {
        if (!isUserSignedIn()) return null
        val clientId = obtainClientId(forceRefresh = false) ?: obtainClientId(forceRefresh = true) ?: return null
        return fetchMeInternal(clientId)
    }

    private fun fetchMeInternal(clientId: String): SoundCloudUser? {
        val url = "$ApiBase/me?client_id=${enc(clientId)}"
        val resp = runCatching { host.http(HttpRequest(url, headers = apiHeaders(includeAuth = true))) }.getOrNull()
            ?: return currentUser()
        if (!resp.ok) return currentUser()
        val obj = runCatching { json.parseToJsonElement(resp.text) as? JsonObject }.getOrNull() ?: return currentUser()
        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return currentUser()
        val username = obj.string("username")?.takeIf { it.isNotBlank() }
            ?: obj.string("full_name")?.takeIf { it.isNotBlank() }
            ?: "SoundCloud"
        val avatar = upgradeArtwork(obj.string("avatar_url"))
        host.storage.set(KeyUserId, id)
        host.storage.set(KeyUsername, username)
        host.storage.set(KeyAvatarUrl, avatar)
        return SoundCloudUser(id = id, username = username, avatarUrl = avatar)
    }

    suspend fun searchCatalog(query: String, limit: Int = 25): List<SoundCloudTrackItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        var clientId = obtainClientId(forceRefresh = false) ?: return emptyList()
        var resp = runCatching {
            host.http(HttpRequest(searchUrl(q, clientId, limit), headers = apiHeaders(includeAuth = true)))
        }.getOrNull()

        if (resp != null && (resp.code == 401 || resp.code == 403)) {
            clientId = obtainClientId(forceRefresh = true) ?: return emptyList()
            resp = runCatching {
                host.http(HttpRequest(searchUrl(q, clientId, limit), headers = apiHeaders(includeAuth = false)))
            }.getOrNull()
        }
        if (resp == null || !resp.ok) return emptyList()
        val parsed = parseSearchTracks(resp.text)
        parsed.forEach { rememberDirectTrack(it) }
        return parsed.map { it.toPublicItem() }
    }

    suspend fun fetchTrendingTracks(limit: Int = 25): List<SoundCloudTrackItem> {
        var clientId = obtainClientId(forceRefresh = false) ?: return emptyList()
        val chartsUrl = "$ApiBase/charts?kind=top&genre=soundcloud%3Agenres%3Aall-music&high_tier_only=false&limit=$limit&client_id=${enc(clientId)}"
        var resp = runCatching {
            host.http(HttpRequest(chartsUrl, headers = apiHeaders(includeAuth = true)))
        }.getOrNull()

        if (resp != null && (resp.code == 401 || resp.code == 403)) {
            clientId = obtainClientId(forceRefresh = true) ?: return emptyList()
            val retryUrl = "$ApiBase/charts?kind=top&genre=soundcloud%3Agenres%3Aall-music&high_tier_only=false&limit=$limit&client_id=${enc(clientId)}"
            resp = runCatching {
                host.http(HttpRequest(retryUrl, headers = apiHeaders(includeAuth = false)))
            }.getOrNull()
        }

        val chartTracks = if (resp != null && resp.ok) {
            parseNestedTracks(resp.text)
        } else {
            emptyList()
        }
        if (chartTracks.isNotEmpty()) {
            chartTracks.forEach { rememberDirectTrack(it) }
            return chartTracks.map { it.toPublicItem() }
        }
        // Fallback when regional charts endpoint is restricted
        return searchCatalog("trending remix hits", limit = limit)
    }

    suspend fun fetchLikedTracks(limit: Int = 40): List<SoundCloudTrackItem> {
        if (!isUserSignedIn()) return emptyList()
        val clientId = obtainClientId(forceRefresh = false) ?: obtainClientId(forceRefresh = true) ?: return emptyList()
        val user = currentUser()?.takeIf { it.id.isNotBlank() } ?: fetchMeInternal(clientId) ?: return emptyList()
        if (user.id.isBlank()) return emptyList()
        val url = "$ApiBase/users/${enc(user.id)}/track_likes?client_id=${enc(clientId)}&limit=$limit&linked_partitioning=1"
        val resp = runCatching {
            host.http(HttpRequest(url, headers = apiHeaders(includeAuth = true)))
        }.getOrNull() ?: return emptyList()
        if (!resp.ok) return emptyList()
        val parsed = parseNestedTracks(resp.text)
        parsed.forEach { rememberDirectTrack(it) }
        return parsed.map { it.toPublicItem() }
    }

    /** Direct playback by SoundCloud track ID (used by the dedicated SoundCloud hub screen). */
    suspend fun resolveDirectTrackId(scTrackId: String, picks: Map<String, String>): ResolveResult {
        var clientId = obtainClientId(forceRefresh = false) ?: obtainClientId(forceRefresh = true)
            ?: return ResolveResult.Unavailable("SoundCloud client_id unavailable")

        val scTrack = directTracks[scTrackId] ?: run {
            val url = "$ApiBase/tracks/${enc(scTrackId)}?client_id=${enc(clientId)}"
            var resp = runCatching { host.http(HttpRequest(url, headers = apiHeaders(includeAuth = true))) }.getOrNull()
            if (resp != null && (resp.code == 401 || resp.code == 403)) {
                clientId = obtainClientId(forceRefresh = true)
                    ?: return ResolveResult.Unavailable("SoundCloud client_id unavailable")
                val retryUrl = "$ApiBase/tracks/${enc(scTrackId)}?client_id=${enc(clientId)}"
                resp = runCatching { host.http(HttpRequest(retryUrl, headers = apiHeaders(includeAuth = false))) }.getOrNull()
            }
            if (resp == null || !resp.ok) return ResolveResult.NotFound("SoundCloud track $scTrackId unavailable")
            val obj = runCatching { json.parseToJsonElement(resp.text) as? JsonObject }.getOrNull()
                ?: return ResolveResult.NotFound("Invalid SoundCloud track response")
            parseTrackObject(obj)?.also { rememberDirectTrack(it) }
                ?: return ResolveResult.NotFound("SoundCloud track $scTrackId not streamable")
        }

        val pick = picks[ChoiceQuality] ?: OptionAuto
        val ordered = orderTranscodings(scTrack.transcodings, pick)
        for (transcoding in ordered) {
            val resolvedUrl = resolveTranscodingUrl(transcoding.url, clientId, scTrack.trackAuthorization)
                ?: continue
            return ResolveResult.Found(buildSourceStream(scTrack, transcoding, resolvedUrl, confidence = 1.0))
        }
        directTracks.remove(scTrackId)
        return ResolveResult.Unavailable("Could not resolve SoundCloud stream URL")
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
                host.http(HttpRequest(searchUrl(query, clientId!!, 12), headers = apiHeaders(includeAuth = true)))
            }.getOrNull()

            // Refresh client_id once if SoundCloud rotated it
            if (response != null && (response.code == 401 || response.code == 403)) {
                clientId = obtainClientId(forceRefresh = true)
                if (clientId != null) {
                    response = runCatching {
                        host.http(HttpRequest(searchUrl(query, clientId!!, 12), headers = apiHeaders(includeAuth = false)))
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
            val stream = buildSourceStream(
                scTrack = scTrack,
                transcoding = transcoding,
                resolvedUrl = resolvedUrl,
                confidence = TrackMatcher.score(track, scTrack.catalog),
            )
            report(DiagnosticStep("Lấy luồng phát", true, stream.qualityLabel))
            return ResolveResult.Found(stream)
        }

        // Cached match may have stale transcoding/auth; drop it so next attempt re-fetches fresh
        matches.remove(key)
        report(DiagnosticStep("Lấy luồng phát", false, "Không mở được luồng phát"))
        return ResolveResult.Unavailable("Could not resolve SoundCloud stream URL")
    }

    private fun buildSourceStream(
        scTrack: ScTrack,
        transcoding: ScTranscoding,
        resolvedUrl: String,
        confidence: Double,
    ): SourceStream {
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
        return SourceStream(
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
                confidence = confidence,
            ),
        )
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
        var resp = runCatching { host.http(HttpRequest(url, headers = apiHeaders(includeAuth = true))) }.getOrNull()
        if (resp != null && (resp.code == 401 || resp.code == 403) && isUserSignedIn()) {
            resp = runCatching { host.http(HttpRequest(url, headers = apiHeaders(includeAuth = false))) }.getOrNull()
        }
        if (resp == null || !resp.ok) return null
        val obj = runCatching { json.parseToJsonElement(resp.text) as? JsonObject }.getOrNull() ?: return null
        return obj.string("url")?.takeIf { it.startsWith("http") }
    }

    private fun parseSearchTracks(text: String): List<ScTrack> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val collection = root["collection"] as? JsonArray ?: return emptyList()
        return collection.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            parseTrackObject(obj)
        }
    }

    private fun parseNestedTracks(text: String): List<ScTrack> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val collection = root["collection"] as? JsonArray ?: return emptyList()
        return collection.mapNotNull { element ->
            val wrapper = element as? JsonObject ?: return@mapNotNull null
            val trackObj = (wrapper["track"] as? JsonObject) ?: wrapper
            parseTrackObject(trackObj)
        }
    }

    private fun parseTrackObject(obj: JsonObject): ScTrack? {
        if (obj.string("kind") != "track") return null
        val policy = obj.string("policy").orEmpty()
        // Skip 30-second Go+ previews ("SNIP") and blocked tracks ("BLOCK") unless user is signed in
        if (policy.equals("BLOCK", ignoreCase = true)) return null
        if (policy.equals("SNIP", ignoreCase = true) && !isUserSignedIn()) return null
        if ((obj["streamable"] as? JsonPrimitive)?.booleanOrNull == false) return null

        val durationMs = (obj["duration"] as? JsonPrimitive)?.longOrNull ?: 0L
        val fullDurationMs = (obj["full_duration"] as? JsonPrimitive)?.longOrNull ?: durationMs
        if (durationMs < 15_000L || (fullDurationMs > 0L && durationMs * 2 < fullDurationMs)) {
            return null
        }

        val media = obj["media"] as? JsonObject ?: return null
        val transcodingsArr = media["transcodings"] as? JsonArray ?: return null
        val transcodings = transcodingsArr.mapNotNull { tElem ->
            val tObj = tElem as? JsonObject ?: return@mapNotNull null
            if ((tObj["snipped"] as? JsonPrimitive)?.booleanOrNull == true && !isUserSignedIn()) return@mapNotNull null
            val url = tObj.string("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val format = tObj["format"] as? JsonObject ?: return@mapNotNull null
            val protocol = format.string("protocol") ?: return@mapNotNull null
            val mimeType = format.string("mime_type").orEmpty()
            if (protocol != "progressive" && protocol != "hls") return@mapNotNull null
            ScTranscoding(url = url, protocol = protocol, mimeType = mimeType)
        }
        if (transcodings.isEmpty()) return null

        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return null
        val title = obj.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val user = obj["user"] as? JsonObject
        val publisher = obj["publisher_metadata"] as? JsonObject
        val artist = publisher?.string("artist")?.takeIf { it.isNotBlank() }
            ?: user?.string("username")?.takeIf { it.isNotBlank() }
            ?: "SoundCloud"
        val album = publisher?.string("album_title")?.takeIf { it.isNotBlank() }
        val trackAuth = obj.string("track_authorization")
        val artwork = upgradeArtwork(obj.string("artwork_url") ?: user?.string("avatar_url"))
        val plays = (obj["playback_count"] as? JsonPrimitive)?.longOrNull ?: 0L

        return ScTrack(
            catalog = CatalogTrack(
                id = id,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
            ),
            transcodings = transcodings,
            trackAuthorization = trackAuth,
            artworkUrl = artwork,
            playbackCount = plays,
        )
    }

    private fun upgradeArtwork(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url
            .replace("-large.", "-t500x500.")
            .replace("-t200x200.", "-t500x500.")
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

    private fun rememberDirectTrack(value: ScTrack) {
        if (directTracks.size >= MaxCacheEntries) directTracks.clear()
        directTracks[value.catalog.id] = value
    }

    private fun rememberMiss(key: String) {
        if (misses.size >= MaxCacheEntries) misses.clear()
        misses[key] = System.currentTimeMillis()
    }

    private fun markUnreachable(reason: String) {
        host.storage.set("unreachable", reason)
        host.storage.set("unreachable_at", System.currentTimeMillis().toString())
    }

    private fun oauthToken(): String? = host.storage.get(KeyOAuthToken)?.takeIf { it.isNotBlank() }

    private fun apiHeaders(includeAuth: Boolean): Map<String, String> {
        val token = if (includeAuth) oauthToken() else null
        return if (token != null) baseApiHeaders + ("Authorization" to "OAuth $token") else baseApiHeaders
    }

    private fun extractAuthToken(raw: String): String? {
        val trimmed = raw.trim()
        val token = when {
            trimmed.startsWith("OAuth ", ignoreCase = true) -> trimmed.substring(6).trim()
            trimmed.startsWith("Bearer ", ignoreCase = true) -> trimmed.substring(7).trim()
            else -> return null
        }
        return token.takeIf { it.length >= 10 && !it.equals("null", true) && !it.equals("undefined", true) }
    }

    private fun extractCookieToken(cookies: String): String? {
        for (pair in cookies.split(';')) {
            val key = pair.substringBefore('=').trim()
            if (key.equals("oauth_token", ignoreCase = true)) {
                val value = pair.substringAfter('=', "").trim().trim('"')
                if (value.length >= 10 && !value.equals("null", true) && !value.equals("undefined", true)) {
                    return value
                }
            }
        }
        return null
    }

    private fun searchUrl(query: String, clientId: String, limit: Int): String =
        "$ApiBase/search/tracks?q=${enc(query)}&client_id=${enc(clientId)}&limit=$limit&linked_partitioning=1"

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private class ScTrack(
        val catalog: CatalogTrack,
        val transcodings: List<ScTranscoding>,
        val trackAuthorization: String?,
        val artworkUrl: String?,
        val playbackCount: Long,
    ) {
        fun toPublicItem() = SoundCloudTrackItem(
            id = catalog.id,
            title = catalog.title,
            artist = catalog.artist,
            album = catalog.album,
            durationMs = catalog.durationMs ?: 0L,
            artworkUrl = artworkUrl,
            playbackCount = playbackCount,
        )
    }

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

        private const val KeyOAuthToken = "oauth_token"
        private const val KeyUserId = "user_id"
        private const val KeyUsername = "username"
        private const val KeyAvatarUrl = "avatar_url"

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
        private val baseApiHeaders = mapOf(
            "User-Agent" to UserAgent,
            "Accept" to "application/json",
            "Origin" to "https://soundcloud.com",
            "Referer" to "https://soundcloud.com/",
        )
        private val streamHeaders = mapOf("User-Agent" to UserAgent)
    }
}
