package com.nuvio.app.features.streams

import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.features.auth.AccountGateway
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Pemanasan sumber IDLIX di bridge.
 *
 * Gate 15 detik milik IDLIX tidak bisa dihilangkan tanpa menembus token
 * bertanda tangan server, jadi yang dilakukan: bayar sekali di latar belakang,
 * sebelum tombol Putar ditekan. Resolve di bridge di-key per identitas, karena
 * itu warm **wajib** memakai token sesi yang sama dengan play nanti — warm
 * sebagai tamu tidak mempercepat play akun (dan sebaliknya).
 */
internal object HfxWarm {
    private const val BaseUrl = AccountGateway.BASE_URL
    private const val StartupThrottleMs = 20L * 60L * 1000L
    private const val EtaTimeoutMs = 3_000L
    private const val WarmTimeoutMs = 8_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy { createApiHttpClient() }

    private var lastStartupWarm: TimeMark? = null

    private fun authHeader(): String? =
        AuthStorage.loadIdentityToken()?.takeIf { it.isNotBlank() }

    /** Sisa waktu sebelum sumber siap; 0 berarti sudah siap atau bukan IDLIX. */
    suspend fun etaMs(contentId: String): Long {
        if (!contentId.startsWith("idlix:")) return 0L
        return runCatching {
            val bearer = authHeader()
            val response = http.get("$BaseUrl/warm/eta.json") {
                parameter("id", contentId)
                if (bearer != null) header(HttpHeaders.Authorization, "Bearer $bearer")
                timeout {
                    requestTimeoutMillis = EtaTimeoutMs
                    socketTimeoutMillis = EtaTimeoutMs
                    connectTimeoutMillis = 2_000
                }
            }
            if (!response.status.isSuccess()) return 0L
            val payload = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return 0L
            payload["etaMs"]?.jsonPrimitive?.longOrNull ?: 0L
        }.getOrDefault(0L)
    }

    /** Minta bridge menyiapkan daftar judul. Diam kalau gagal — ini bonus. */
    suspend fun requestWarm(ids: List<String>): Int {
        val wanted = ids.filter { it.startsWith("idlix:") }.distinct().take(6)
        if (wanted.isEmpty()) return 0
        return runCatching {
            val bearer = authHeader()
            val response = http.get("$BaseUrl/warm.json") {
                parameter("ids", wanted.joinToString(","))
                if (bearer != null) header(HttpHeaders.Authorization, "Bearer $bearer")
                timeout {
                    requestTimeoutMillis = WarmTimeoutMs
                    socketTimeoutMillis = WarmTimeoutMs
                    connectTimeoutMillis = 3_000
                }
            }
            if (!response.status.isSuccess()) return 0
            val payload = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return 0
            payload["queued"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
        }.getOrDefault(0)
    }

    /**
     * Warm item "Lanjut Tonton" sekali per 20 menit.
     *
     * Tanpa throttle, setiap kembali ke Beranda memicu resolve baru padahal
     * hasil sebelumnya masih berlaku 540 detik di bridge.
     */
    suspend fun warmStartup(ids: List<String>) {
        if (ids.isEmpty()) return
        val previous = lastStartupWarm
        if (previous != null && previous.elapsedNow().inWholeMilliseconds < StartupThrottleMs) return
        lastStartupWarm = TimeSource.Monotonic.markNow()
        requestWarm(ids)
    }
}
