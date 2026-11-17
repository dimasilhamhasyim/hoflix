package com.nuvio.app.features.streams

import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.network.createApiHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Rantai `play-info → claim → redeem → master HLS` yang dijalankan di perangkat.
 *
 * Dipakai hanya saat bridge mengirim `hfxResolve`: IDLIX memblokir
 * `/api/watch/...` untuk sebagian IP, dan IP perangkat tidak terkena blokir itu.
 * Playlist-nya tetap diteruskan lewat proxy bridge (`/idlix_pl`) karena token
 * `?t=` pada master harus ditempel ke setiap URI anak, dan pemutar tidak
 * melakukannya sendiri.
 */
internal object HfxIdlixResolver {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val GATE_WAIT_CAP_MS = 25_000L
    private const val MAX_SUBTITLE_TRACKS = 40

    suspend fun resolve(
        spec: HfxResolveSpec,
        addonName: String,
        addonId: String,
    ): List<StreamItem> {
        if (!spec.isSupported) return emptyList()
        val client = createApiHttpClient()
        try {
            var cookie = sessionCookie(client, spec)
            var gateResult = getJsonWithStatus(client, spec, spec.playInfoUrl)
            // Cookie IDLIX berumur 15 menit. Bridge menyegarkan saat diminta,
            // jadi satu percobaan ulang dengan sesi baru menyelamatkan play
            // yang dulu langsung gagal 401 di jalur perangkat.
            if ((gateResult.first == 401 || gateResult.first == 403) && cookie.isNotBlank()) {
                cookie = sessionCookie(client, spec, forceRefresh = true)
                gateResult = getJsonWithStatus(client, spec, spec.playInfoUrl)
            }
            val gate = gateResult.second ?: return emptyList()
            val gateToken = gate.string("gateToken") ?: gate.string("token") ?: return emptyList()
            val claim = awaitClaim(client, spec, cookie, gate, gateToken) ?: return emptyList()
            val claimValue = claim.string("claim") ?: claim.string("token") ?: return emptyList()
            val redeemed = postJson(
                client = client,
                spec = spec,
                url = "${spec.apiBase}/api/watch/session/redeem",
                cookies = cookie,
                body = buildJsonObject {
                    put("claim", claimValue)
                    put("mode", "browser")
                },
            ) ?: return emptyList()
            val master = findMasterUrl(redeemed) ?: return emptyList()
            val subtitles = subtitleTracks(redeemed)
            val token = encodeUrl(master)
            val heights = masterHeights(client, spec, token)
            if (heights.isEmpty()) {
                // Master tanpa RESOLUTION: teruskan utuh, pemutar yang memilih.
                return listOf(
                    entry(
                        spec = spec,
                        addonName = addonName,
                        addonId = addonId,
                        url = playlistUrl(spec, token, 0),
                        height = 0,
                        subtitles = subtitles,
                    )
                )
            }
            return heights.map { height ->
                entry(
                    spec = spec,
                    addonName = addonName,
                    addonId = addonId,
                    url = playlistUrl(spec, token, height),
                    height = height,
                    subtitles = subtitles,
                )
            }
        } catch (error: Exception) {
            return emptyList()
        } finally {
            client.close()
        }
    }

    /**
     * URL playlist lewat bridge. `ps` = token play: pengganti tanda tangan HMAC
     * yang hanya dipegang server, sehingga `/idlix_pl` tetap menolak URL dari
     * luar sesi putar ini.
     */
    private fun playlistUrl(spec: HfxResolveSpec, token: String, height: Int): String {
        val suffix = if (height > 0) "&q=$height" else ""
        val play = if (spec.playToken.isNotBlank()) "&ps=${spec.playToken}" else ""
        return "${spec.playlistBase}/idlix_pl?u=$token$suffix$play"
    }

    private fun entry(
        spec: HfxResolveSpec,
        addonName: String,
        addonId: String,
        url: String,
        height: Int,
        subtitles: List<StreamSubtitle>,
    ): StreamItem = StreamItem(
        name = "HOFLIX",
        title = if (height > 0) "HOFLIX · ${height}p" else "HOFLIX",
        description = "Diputar dari perangkat",
        url = url,
        addonName = addonName,
        addonId = addonId,
        streamType = "hls",
        behaviorHints = StreamBehaviorHints(
            bingeGroup = "hoflix-idlix-device",
            qualityHeight = height.takeIf { it > 0 },
            adaptive = true,
            audioLabel = "audio",
            audioOriginal = true,
            proxyHeaders = spec.playToken
                .takeIf { it.isNotBlank() }
                ?.let { StreamProxyHeaders(request = mapOf("X-HF-Play" to it)) },
        ),
        externalSubtitles = subtitles,
    )

    private suspend fun awaitClaim(
        client: HttpClient,
        spec: HfxResolveSpec,
        cookies: String,
        gate: JsonObject,
        gateToken: String,
    ): JsonObject? {
        val serverNow = gate.long("serverNow") ?: 0L
        val unlockAt = gate.long("unlockAt") ?: 0L
        val waitMs = if (serverNow > 0 && unlockAt > serverNow) unlockAt - serverNow else 0L
        val deadline = nowMillis() + minOf(GATE_WAIT_CAP_MS, waitMs + 6_000L)
        var payload: JsonObject? = null
        while (true) {
            payload = postJson(
                client = client,
                spec = spec,
                url = "${spec.apiBase}/api/watch/session/claim",
                cookies = cookies,
                body = buildJsonObject { put("gateToken", gateToken) },
            ) ?: return null
            val kind = payload.string("kind") ?: ""
            if (kind.isNotEmpty() && kind != "pending") return payload
            if (nowMillis() > deadline) return payload
            val remaining = (payload.long("remainingMs") ?: 0L) / 1000.0
            delay(((remaining + 0.3).coerceIn(0.4, 2.0) * 1000).toLong())
        }
    }

    /**
     * Cookie sesi IDLIX milik perangkat ini.
     *
     * Bridge memegang sesi (login terjadi di sana), jadi cookie diambil lewat
     * endpoint ber-token. Gagal mengambil sesi bukan kegagalan play: resolver
     * lanjut sebagai tamu (720).
     */
    private suspend fun sessionCookie(
        client: HttpClient,
        spec: HfxResolveSpec,
        forceRefresh: Boolean = false,
    ): String {
        val token = runCatching { AuthStorage.loadIdentityToken() }.getOrNull().orEmpty()
        if (token.isBlank()) return ""
        val body = runCatching {
            val url = buildString {
                append("${spec.playlistBase}/hf/auth/idlix-session")
                if (forceRefresh) append("?refresh=1")
            }
            val response = client.get(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/json")
            }
            if (response.status.value != 200) return ""
            response.bodyAsText()
        }.getOrNull() ?: return ""
        val root = runCatching { json.parseToJsonElement(body).jsonObjectOrNull() }.getOrNull() ?: return ""
        val cookies = root["cookies"] as? JsonObject ?: return ""
        return cookies.entries
            .mapNotNull { (name, value) ->
                val text = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                if (text.isBlank()) null else "$name=$text"
            }
            .joinToString("; ")
    }

    /**
     * Origin/Referer yang diterima IDLIX.
     *
     * `z2.idlixku.com` sudah tidak ada di allowlist mereka: semua permintaan
     * ke endpoint auth dan watch-session dibalas `403 Forbidden origin`.
     * Host-nya diambil dari `webBase` yang dikirim bridge, jadi perpindahan
     * host di sisi IDLIX tidak butuh build APK lagi.
     */
    private fun webOrigin(spec: HfxResolveSpec): String {
        val raw = spec.webBase.ifBlank { spec.apiBase }.trim().trimEnd('/')
        return raw.ifBlank { "https://api.idlixku.com" }
    }

    private fun HttpRequestBuilder.idlixOrigin(spec: HfxResolveSpec) {
        val origin = webOrigin(spec)
        header(HttpHeaders.Origin, origin)
        header("Referer", "$origin/")
    }

    /** Sama seperti [getJsonWithStatus], status HTTP ikut dikembalikan. */
    private suspend fun getJsonWithStatus(
        client: HttpClient,
        spec: HfxResolveSpec,
        url: String,
    ): Pair<Int, JsonObject?> = runCatching {
        val response = client.get(url) {
            header(HttpHeaders.Accept, "application/json")
            idlixOrigin(spec)
        }
        val status = response.status.value
        if (status != 200) return@runCatching status to null
        status to json.parseToJsonElement(response.bodyAsText()).jsonObjectOrNull()
    }.getOrDefault(0 to null)

    private suspend fun postJson(
        client: HttpClient,
        spec: HfxResolveSpec,
        url: String,
        cookies: String,
        body: JsonObject,
    ): JsonObject? = runCatching {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json")
            idlixOrigin(spec)
            if (cookies.isNotBlank()) header(HttpHeaders.Cookie, cookies)
            setBody(body.toString())
        }
        if (response.status.value != 200) return@runCatching null
        json.parseToJsonElement(response.bodyAsText()).jsonObjectOrNull()
    }.getOrNull()

    /** Tinggi rendisi yang benar-benar ada di master, dibaca lewat proxy bridge. */
    private suspend fun masterHeights(
        client: HttpClient,
        spec: HfxResolveSpec,
        token: String,
    ): List<Int> = runCatching {
        val response = client.get(playlistUrl(spec, token, 0)) {
            header(HttpHeaders.Accept, "application/vnd.apple.mpegurl")
            if (spec.playToken.isNotBlank()) header("X-HF-Play", spec.playToken)
        }
        if (response.status.value != 200) return@runCatching emptyList<Int>()
        val text = response.bodyAsText()
        Regex("RESOLUTION=\\d+x(\\d+)")
            .findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
            .toList()
    }.getOrDefault(emptyList())

    private fun subtitleTracks(payload: JsonObject): List<StreamSubtitle> {
        val rows = payload["subtitles"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val url = obj.string("path") ?: obj.string("url") ?: return@mapNotNull null
            if (!url.startsWith("http")) return@mapNotNull null
            val lang = normalizeLang(obj.string("lang").orEmpty())
            if (lang.isBlank()) return@mapNotNull null
            StreamSubtitle(
                url = url,
                language = lang,
                name = obj.string("label") ?: obj.string("name") ?: lang,
            )
        }
            .distinctBy { it.language }
            .sortedBy { if (it.language == "id") 0 else if (it.language == "en") 1 else 2 }
            .take(MAX_SUBTITLE_TRACKS)
    }

    private fun findMasterUrl(payload: JsonObject): String? {
        val keys = listOf("url", "masterUrl", "master", "hls", "playlist", "streamUrl", "src")
        keys.forEach { key ->
            val value = payload.string(key)
            if (value != null && value.startsWith("http")) return value
        }
        payload.values.forEach { value ->
            when (value) {
                is JsonObject -> findMasterUrl(value)?.let { return it }
                is JsonArray -> value.forEach { child ->
                    if (child is JsonObject) findMasterUrl(child)?.let { return it }
                }
                else -> Unit
            }
        }
        return null
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeUrl(url: String): String =
        Base64.UrlSafe.encode(url.encodeToByteArray()).trimEnd('=')

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(name: String): Long? =
        (this[name] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
        this as? JsonObject

    private fun normalizeLang(raw: String): String {
        val code = raw.trim().lowercase().substringBefore('-').substringBefore('_')
        return when (code) {
            "in" -> "id"
            "ind" -> "id"
            "eng" -> "en"
            else -> code
        }
    }

    private val startMark = kotlin.time.TimeSource.Monotonic.markNow()

    private fun nowMillis(): Long = startMark.elapsedNow().inWholeMilliseconds
}
