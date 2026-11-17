package com.nuvio.app.features.membership

import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.auth.currentDeviceClientMetadata
import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.core.sync.SyncClientIdentity
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Status keanggotaan HOFLIX dari bridge.
 *
 * Ini hanya untuk tampilan: penegakan sebenarnya ada di bridge, yang menolak
 * katalog dan stream dengan 402 kalau langganan tidak aktif. Aplikasi tidak
 * boleh menganggap dirinya sudah member hanya karena state di sini.
 */
internal object MembershipGateway {
    private const val BASE_URL = "https://hoflix-api.siho.my.id"
    private const val TimeoutMillis = 15_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy { createApiHttpClient() }

    data class Device(
        val installationId: String,
        val name: String,
        val platform: String,
        val lastSeen: String,
    )

    data class Status(
        val active: Boolean,
        val planName: String,
        val daysLeft: Int,
        val expiresAt: String,
        val devices: List<Device>,
        val maxDevices: Int,
        val priceIdr: Int,
    ) {
        val deviceSlots: String get() = "${devices.size}/$maxDevices perangkat"
    }

    sealed interface Outcome {
        data class Resolved(val status: Status, val fromCache: Boolean = false) : Outcome

        /** Server menolak sesi — bukan gangguan jaringan. */
        data class Rejected(val message: String) : Outcome

        data object Unreachable : Outcome
    }

    private const val CACHE_KEY = "hf_membership_cache_v1"
    private const val CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000

    private fun token(): String = runCatching { AuthStorage.loadIdentityToken() }.getOrNull().orEmpty()

    private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    /**
     * Status terakhir yang pernah diterima, disimpan di perangkat.
     *
     * Tanpa ini anggota yang sudah membayar terkunci di layar "tidak bisa
     * menghubungi server" setiap kali sinyal jelek — padahal langganannya masih
     * berjalan. Hanya dipakai kalau server tidak bisa dihubungi, dan hanya
     * selama statusnya masih aktif serta umurnya di bawah 24 jam.
     */
    private fun saveCache(status: Status) {
        val body = buildString {
            append('{')
            append("\"at\":").append(nowMs()).append(',')
            append("\"active\":").append(status.active).append(',')
            append("\"plan_name\":\"").append(escape(status.planName)).append("\",")
            append("\"days_left\":").append(status.daysLeft).append(',')
            append("\"expires_at\":\"").append(escape(status.expiresAt)).append("\",")
            append("\"max_devices\":").append(status.maxDevices).append(',')
            append("\"price_idr\":").append(status.priceIdr)
            append('}')
        }
        runCatching { AuthStorage.saveText(CACHE_KEY, body) }
    }

    private fun loadCache(): Pair<Status, Long>? {
        val raw = runCatching { AuthStorage.loadText(CACHE_KEY) }.getOrNull() ?: return null
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val at = obj["at"]?.jsonPrimitive?.intOrNull?.toLong() ?: return null
        val status = Status(
            active = obj["active"]?.jsonPrimitive?.booleanOrNull == true,
            planName = obj["plan_name"]?.jsonPrimitive?.content.orEmpty(),
            daysLeft = obj["days_left"]?.jsonPrimitive?.intOrNull ?: 0,
            expiresAt = obj["expires_at"]?.jsonPrimitive?.content.orEmpty(),
            devices = emptyList(),
            maxDevices = obj["max_devices"]?.jsonPrimitive?.intOrNull ?: 4,
            priceIdr = obj["price_idr"]?.jsonPrimitive?.intOrNull ?: 0,
        )
        return status to at
    }

    internal fun clearCache() {
        runCatching { AuthStorage.clearText(CACHE_KEY) }
    }

    private fun escape(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u" + char.code.toString(16).padStart(4, '0')) else append(char)
            }
        }
    }

    /** Id pemasangan: dipakai bridge menghitung slot perangkat (maks 4). */
    private fun deviceId(): String =
        runCatching { SyncClientIdentity.currentClientId() }.getOrNull().orEmpty()

    suspend fun fetch(): Outcome {
        val identity = token()
        if (identity.isBlank()) return Outcome.Rejected("Sesi tidak ditemukan. Masuk ulang.")
        // Nama perangkat ikut dikirim supaya daftar "Perangkat" di layar
        // keanggotaan menampilkan nama HP, bukan kata "Perangkat" semua.
        val metadata = runCatching { currentDeviceClientMetadata() }.getOrNull()
        val response = runCatching {
            http.get("$BASE_URL/hf/membership") {
                header(HttpHeaders.Authorization, "Bearer $identity")
                header(HttpHeaders.UserAgent, UploadAgent)
                deviceId().takeIf { it.isNotBlank() }?.let { header("X-HF-Device", it) }
                metadata?.deviceName?.takeIf { it.isNotBlank() }?.let { header("X-HF-Device-Name", it) }
                metadata?.platform?.takeIf { it.isNotBlank() }?.let { header("X-HF-Platform", it) }
                timeout {
                    requestTimeoutMillis = TimeoutMillis
                    socketTimeoutMillis = TimeoutMillis
                }
            }
        }.getOrNull() ?: return offline()

        val status = response.status.value
        if (status == 401 || status == 403) {
            // Sesi benar-benar ditolak — bukan soal sinyal. Cache ikut dibuang
            // supaya tidak ada anggota "hantu" yang masuk dari data lama.
            clearCache()
            return Outcome.Rejected("Sesi berakhir. Masuk ulang untuk melanjutkan.")
        }
        if (status >= 500 || status == 429) return offline()

        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        if (!response.status.isSuccess()) return offline()
        val payload = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return offline()
        val membership = payload["membership"] as? JsonObject ?: return offline()
        val price = (payload["plans"] as? JsonArray)?.firstOrNull()?.let { element ->
            (element as? JsonObject)?.get("price_idr")?.jsonPrimitive?.intOrNull
        } ?: 0
        val devices = parseDevices(payload["devices"] as? JsonArray)
        val resolved = parse(membership, price).copy(devices = devices)
        if (resolved.active) saveCache(resolved) else clearCache()
        return Outcome.Resolved(resolved)
    }

    /**
     * Server tidak bisa dihubungi: pakai status terakhir kalau masih segar dan
     * masih aktif. Anggota yang sudah bayar tidak boleh terkunci oleh sinyal.
     */
    private fun offline(): Outcome {
        val (cached, at) = loadCache() ?: return Outcome.Unreachable
        if (!cached.active) return Outcome.Unreachable
        if (nowMs() - at > CACHE_MAX_AGE_MS) return Outcome.Unreachable
        // Jam dinding tetap menang: langganan yang sudah lewat masa aktif tidak
        // boleh terbuka hanya karena cache belum kedaluwarsa.
        val expires = isoToEpochSeconds(cached.expiresAt)
        if (expires != null && expires * 1000 <= nowMs()) return Outcome.Unreachable
        return Outcome.Resolved(cached, fromCache = true)
    }

    /** `2026-12-15T14:18:08Z` / `…+00:00` → detik epoch UTC. */
    internal fun isoToEpochSeconds(value: String): Long? {
        if (value.length < 19) return null
        val year = value.substring(0, 4).toIntOrNull() ?: return null
        val month = value.substring(5, 7).toIntOrNull() ?: return null
        val day = value.substring(8, 10).toIntOrNull() ?: return null
        val hour = value.substring(11, 13).toIntOrNull() ?: return null
        val minute = value.substring(14, 16).toIntOrNull() ?: return null
        val second = value.substring(17, 19).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return daysFromCivil(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second
    }

    internal fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yearOfEra = y - era * 400
        val shiftedMonth = (month + 9) % 12
        val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097L + dayOfEra - 719_468L
    }

    suspend fun redeem(code: String): Result<Status> = runCatching {
        val identity = token()
        check(identity.isNotBlank()) { "Sesi tidak ditemukan. Masuk ulang." }
        val body = """{"code":${quote(code)}}"""
        val response = http.post("$BASE_URL/hf/membership/redeem") {
            header(HttpHeaders.Authorization, "Bearer $identity")
            header(HttpHeaders.UserAgent, UploadAgent)
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = TimeoutMillis
                socketTimeoutMillis = TimeoutMillis
            }
            setBody(body)
        }
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val payload = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        if (!response.status.isSuccess()) {
            val message = payload?.get("message")?.jsonPrimitive?.content
                ?: payload?.get("error")?.jsonPrimitive?.content
                ?: "Kupon tidak bisa dipakai."
            error(message)
        }
        val membership = payload?.get("membership") as? JsonObject ?: error("Balasan server tidak dikenali.")
        parse(membership, 0).also { if (it.active) saveCache(it) }
    }

    suspend fun revoke(installationId: String): Result<Unit> = runCatching {
        val identity = token()
        check(identity.isNotBlank()) { "Sesi tidak ditemukan." }
        // Bridge mendaftarkan endpoint ini sebagai POST; PATCH dulu selalu 405,
        // jadi tombol cabut perangkat tidak pernah berhasil.
        val response = http.post("$BASE_URL/hf/membership/devices/revoke") {
            header(HttpHeaders.Authorization, "Bearer $identity")
            header(HttpHeaders.UserAgent, UploadAgent)
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = TimeoutMillis }
            setBody("""{"installation_id":${quote(installationId)}}""")
        }
        if (!response.status.isSuccess()) error("Gagal mencabut perangkat.")
    }

    private fun parseDevices(node: JsonArray?): List<Device> =
        (node).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            Device(
                installationId = item["installation_id"]?.jsonPrimitive?.content.orEmpty(),
                name = item["device_name"]?.jsonPrimitive?.content.orEmpty(),
                platform = item["platform"]?.jsonPrimitive?.content.orEmpty(),
                lastSeen = item["last_seen_at"]?.jsonPrimitive?.content.orEmpty(),
            )
        }

    private fun parse(node: JsonObject, price: Int): Status {
        val devices = parseDevices(node["devices"] as? JsonArray)
        return Status(
            active = node["active"]?.jsonPrimitive?.booleanOrNull == true,
            planName = node["plan_name"]?.jsonPrimitive?.content.orEmpty(),
            daysLeft = node["days_left"]?.jsonPrimitive?.intOrNull ?: 0,
            expiresAt = node["expires_at"]?.jsonPrimitive?.content.orEmpty(),
            // Bridge mengirim `devices` sebagai jumlah pada state anggota, dan
            // daftar penuh pada balasan perangkat.
            devices = devices,
            maxDevices = node["max_devices"]?.jsonPrimitive?.intOrNull ?: 4,
            priceIdr = price,
        )
    }

    private fun quote(value: String): String = buildString {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u" + char.code.toString(16).padStart(4, '0')) else append(char)
            }
        }
        append('"')
    }

    private const val UploadAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
}
