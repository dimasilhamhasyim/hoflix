package com.nuvio.app.features.auth

import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.network.createApiHttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Jembatan ke layanan HOFLIX untuk hal yang tidak boleh terbuka lewat klien:
 * sesi akun (login/daftar/keluar) dan penyimpanan avatar unggahan.
 */
internal object AccountGateway {
    const val BASE_URL: String = "https://hoflix-api.siho.my.id"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy { createApiHttpClient() }

    /**
     * Hasil masuk/daftar: sesi HOFLIX + sesi Supabase yang sudah ditukar bridge.
     *
     * Password turunan tidak lagi melewati jaringan. Dulu satu dump storage
     * memberi akses permanen ke akun Supabase, karena password itu
     * deterministik dan tidak bisa dicabut. Token sesi bisa diputus.
     */
    data class AccountGrant(
        val token: String,
        val supabaseEmail: String,
        val supabaseSessionJson: String,
        val displayName: String,
    )

    /**
     * Daftar akun. Bridge yang membuat akun IDLIX, lalu mengembalikan kredensial
     * Supabase turunan untuk sesi sinkron aplikasi.
     *
     * [turnstileToken] berasal dari widget verifikasi yang dirender di form;
     * IDLIX menolak pendaftaran tanpanya.
     */
    suspend fun registerAccount(
        email: String,
        password: String,
        username: String = "",
        turnstileToken: String = "",
    ): Result<AccountGrant> =
        identityCall("$BASE_URL/hf/auth/register", email, password, username, turnstileToken)

    /** Masuk dengan akun IDLIX; identitas HOFLIX sama persis dengan akun itu. */
    suspend fun signInAccount(identifier: String, password: String): Result<AccountGrant> =
        identityCall("$BASE_URL/hf/auth/login", identifier, password)

    /**
     * Daftar sesi IDLIX yang masih aktif untuk akun ini.
     *
     * Sesi HOFLIX = cookie IDLIX di server, jadi daftar ini yang menjelaskan
     * kenapa akun bisa kena batas sign-in: tiap login lama menambah satu sesi.
     */
    data class AccountSession(
        val id: String,
        val current: Boolean,
        val createdAt: Long,
        val lastUsed: Long,
    )

    suspend fun listSessions(token: String): Result<List<AccountSession>> = runCatching {
        val response = http.get("$BASE_URL/hf/auth/sessions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.UserAgent, UploadUserAgent)
            timeout {
                requestTimeoutMillis = IdentityTimeoutMillis
                socketTimeoutMillis = IdentityTimeoutMillis
                connectTimeoutMillis = 8_000
            }
        }
        if (!response.status.isSuccess()) error("sessions ${response.status.value}")
        parseSessions(response.bodyAsText())
    }

    suspend fun revokeSession(token: String, id: String): Result<List<AccountSession>> = runCatching {
        val response = http.post("$BASE_URL/hf/auth/sessions/revoke") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.UserAgent, UploadUserAgent)
            timeout {
                requestTimeoutMillis = IdentityTimeoutMillis
                socketTimeoutMillis = IdentityTimeoutMillis
                connectTimeoutMillis = 8_000
            }
            setBody("{\"id\":${quote(id)}}")
        }
        if (!response.status.isSuccess()) error("revoke ${response.status.value}")
        parseSessions(response.bodyAsText())
    }

    /** Keluar dari semua sesi lain; sesi yang sedang dipakai tetap hidup. */
    suspend fun logoutOtherSessions(token: String): Result<Int> = runCatching {
        val response = http.post("$BASE_URL/hf/auth/sessions/logout-others") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.UserAgent, UploadUserAgent)
            timeout {
                requestTimeoutMillis = IdentityTimeoutMillis
                socketTimeoutMillis = IdentityTimeoutMillis
                connectTimeoutMillis = 8_000
            }
            setBody("{}")
        }
        if (!response.status.isSuccess()) error("logout-others ${response.status.value}")
        val payload = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        payload?.get("ended")?.jsonPrimitive?.intOrNull ?: 0
    }

    private fun parseSessions(text: String): List<AccountSession> {
        val payload = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        val rows = payload["sessions"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val id = row["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            AccountSession(
                id = id,
                current = row["current"]?.jsonPrimitive?.booleanOrNull == true,
                createdAt = row["createdAt"]?.jsonPrimitive
                    ?.content?.toLongOrNull() ?: 0L,
                lastUsed = row["lastUsed"]?.jsonPrimitive
                    ?.content?.toLongOrNull() ?: 0L,
            )
        }
    }

    /** Ringkasan data HOFLIX yang sudah menempel pada sebuah email. */
    data class LegacyAccount(
        val profiles: Int,
        val watchProgress: Int,
        val watchedItems: Int,
        val libraryItems: Int,
    ) {
        val hasData: Boolean
            get() = profiles > 0 || watchProgress > 0 || watchedItems > 0 || libraryItems > 0
    }

    /**
     * Tanya apakah email ini sudah punya akun HOFLIX lama.
     *
     * Hasil `null` berarti tidak ada data lama (atau server tidak bisa
     * memastikan) — form tetap boleh lanjut tanpa pemberitahuan.
     */
    suspend fun lookupLegacyAccount(email: String): Result<LegacyAccount?> = runCatching {
        val response = http.get("$BASE_URL/hf/auth/legacy?email=${email.encodeURLParameter()}") {
            header(HttpHeaders.UserAgent, UploadUserAgent)
            timeout {
                requestTimeoutMillis = LegacyLookupTimeoutMillis
                socketTimeoutMillis = LegacyLookupTimeoutMillis
                connectTimeoutMillis = 8_000
            }
        }
        if (!response.status.isSuccess()) return@runCatching null
        val payload = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: return@runCatching null
        if (payload["exists"]?.jsonPrimitive?.booleanOrNull != true) return@runCatching null
        val account = LegacyAccount(
            profiles = payload["profiles"]?.jsonPrimitive?.intOrNull ?: 0,
            watchProgress = payload["watch_progress"]?.jsonPrimitive?.intOrNull ?: 0,
            watchedItems = payload["watched_items"]?.jsonPrimitive?.intOrNull ?: 0,
            libraryItems = payload["library_items"]?.jsonPrimitive?.intOrNull ?: 0,
        )
        account.takeIf { it.hasData }
    }

    /**
     * Keluar dari sesi IDLIX di bridge.
     *
     * Sesi IDLIX adalah cookie di server; kalau hanya sesi lokal yang dihapus,
     * sesi lama tetap hidup dan tiap login menambah sesi baru sampai akun kena
     * batas sign-in. Kegagalan jaringan tidak boleh menggagalkan logout lokal.
     */
    suspend fun signOutAccount(token: String): Result<Unit> = runCatching {
        if (token.isBlank()) return@runCatching
        runCatching {
            http.post("$BASE_URL/hf/auth/logout") {
                header(HttpHeaders.Authorization, "Bearer $token")
                timeout {
                    requestTimeoutMillis = 8_000
                    socketTimeoutMillis = 8_000
                    connectTimeoutMillis = 5_000
                }
            }
        }
        Unit
    }

    /**
     * Tanya apakah username IDLIX masih kosong, sebelum tombol Daftar ditekan.
     *
     * `Result.failure` berarti tidak bisa dipastikan (IDLIX tidak menjawab) —
     * form membiarkan user lanjut dan server yang memutuskan saat submit.
     */
    enum class UsernameAvailability { AVAILABLE, TAKEN }

    suspend fun checkUsername(username: String): Result<UsernameAvailability> = runCatching {
        val response = http.get("$BASE_URL/hf/auth/username?u=${username.encodeURLParameter()}") {
            header(HttpHeaders.UserAgent, UploadUserAgent)
            timeout {
                requestTimeoutMillis = UsernameCheckTimeoutMillis
                socketTimeoutMillis = UsernameCheckTimeoutMillis
                connectTimeoutMillis = 6_000
            }
        }
        if (!response.status.isSuccess()) error("username check ${response.status.value}")
        val payload = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: error("username payload")
        if (payload["available"]?.jsonPrimitive?.booleanOrNull == false) {
            UsernameAvailability.TAKEN
        } else {
            UsernameAvailability.AVAILABLE
        }
    }

    private suspend fun identityCall(
        url: String,
        identifier: String,
        password: String,
        username: String = "",
        turnstileToken: String = "",
    ): Result<AccountGrant> = runCatching {
        val body = buildString {
            append('{')
            append("\"identifier\":").append(quote(identifier)).append(',')
            append("\"email\":").append(quote(identifier)).append(',')
            append("\"password\":").append(quote(password))
            if (username.isNotBlank()) {
                append(',').append("\"username\":").append(quote(username))
            }
            if (turnstileToken.isNotBlank()) {
                append(',').append("\"turnstile_token\":").append(quote(turnstileToken))
            }
            append('}')
        }
        val response = http.post(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, UploadUserAgent)
            timeout {
                requestTimeoutMillis = IdentityTimeoutMillis
                socketTimeoutMillis = IdentityTimeoutMillis
                connectTimeoutMillis = UploadConnectTimeoutMillis
            }
            setBody(body)
        }
        val text = response.bodyAsText()
        val payload = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        if (!response.status.isSuccess()) {
            val code = payload?.get("error")?.jsonPrimitive?.content ?: "identity_failed"
            val detail = payload?.get("message")?.jsonPrimitive?.content
            throw SignupRejected(if (detail.isNullOrBlank()) code else "$code|$detail")
        }
        val supabase = payload?.get("supabase") as? JsonObject
        val supabaseEmail = supabase?.get("email")?.jsonPrimitive?.content.orEmpty()
        val supabaseSession = supabase?.get("session") as? JsonObject
        if (supabaseEmail.isBlank() || supabaseSession == null) {
            throw SignupRejected("identity_no_backend")
        }
        val user = payload?.get("user") as? JsonObject
        AccountGrant(
            token = payload?.get("token")?.jsonPrimitive?.content.orEmpty(),
            supabaseEmail = supabaseEmail,
            supabaseSessionJson = supabaseSession.toString(),
            displayName = user?.get("displayName")?.jsonPrimitive?.content.orEmpty(),
        )
    }

    /**
     * Cek sesi IDLIX masih hidup di bridge sekaligus menyegarkan cookie-nya.
     * Dipakai segera setelah login supaya token mati tidak ikut tersimpan.
     */
    suspend fun verifyIdentity(token: String): Boolean {
        if (token.isBlank()) return false
        return runCatching {
            val response = http.get("$BASE_URL/hf/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.UserAgent, UploadUserAgent)
                timeout {
                    requestTimeoutMillis = IdentityTimeoutMillis
                    socketTimeoutMillis = IdentityTimeoutMillis
                    connectTimeoutMillis = UploadConnectTimeoutMillis
                }
            }
            response.status.isSuccess()
        }.getOrDefault(false)
    }

    /**
     * Apakah bridge **menolak** token ini — `null` kalau tidak bisa dipastikan.
     *
     * Dipakai pemeriksaan saat aplikasi dibuka: gangguan jaringan tidak boleh
     * dianggap sesi mati, karena itu akan menghapus token yang sebenarnya sah.
     */
    suspend fun identityRejected(token: String): Boolean? {
        if (token.isBlank()) return true
        return runCatching {
            val response = http.get("$BASE_URL/hf/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.UserAgent, UploadUserAgent)
                timeout {
                    requestTimeoutMillis = IdentityTimeoutMillis
                    socketTimeoutMillis = IdentityTimeoutMillis
                    connectTimeoutMillis = UploadConnectTimeoutMillis
                }
            }
            when {
                response.status.isSuccess() -> false
                response.status.value == 401 || response.status.value == 403 -> true
                else -> null
            }
        }.getOrNull()
    }

    suspend fun uploadAvatar(bytes: ByteArray): Result<String> = runCatching {
        var lastFailure: Throwable? = null
        for (attempt in 0 until UploadAttempts) {
            if (attempt > 0) delay(UploadRetryDelayMs)
            val outcome = runCatching { uploadAvatarOnce(bytes) }
            outcome.getOrNull()?.let { return@runCatching it }
            val failure = outcome.exceptionOrNull() ?: IllegalStateException("upload failed")
            lastFailure = failure
            if (failure is AvatarUploadRejected && !failure.retryable) break
        }
        throw lastFailure ?: IllegalStateException("upload failed")
    }

    private suspend fun uploadAvatarOnce(bytes: ByteArray): String {
        val response = try {
            http.post("$BASE_URL/avatars/upload") {
                contentType(ContentType.Image.JPEG)
                header(HttpHeaders.UserAgent, UploadUserAgent)
                // Server menolak unggahan tanpa sesi: dulu siapa pun bisa
                // mengisi disk VPS dan file-nya tampil di domain sendiri.
                AuthStorage.loadIdentityToken()?.takeIf { it.isNotBlank() }?.let { token ->
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                timeout {
                    requestTimeoutMillis = UploadTimeoutMillis
                    socketTimeoutMillis = UploadTimeoutMillis
                    connectTimeoutMillis = UploadConnectTimeoutMillis
                }
                setBody(bytes)
            }
        } catch (timeout: HttpRequestTimeoutException) {
            throw AvatarUploadRejected(UploadDetailTimeout, retryable = true)
        } catch (failure: Exception) {
            val detail = if (failure.message?.contains("timeout", ignoreCase = true) == true) {
                UploadDetailTimeout
            } else {
                UploadDetailNetwork
            }
            throw AvatarUploadRejected(detail, retryable = true)
        }
        val code = response.status.value
        if (!response.status.isSuccess()) {
            throw AvatarUploadRejected(
                message = "HTTP $code",
                retryable = code >= 500 || code == 429,
            )
        }
        val payload = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: throw AvatarUploadRejected(UploadDetailNetwork, retryable = false)
        return payload["url"]?.jsonPrimitive?.content
            ?: throw AvatarUploadRejected(UploadDetailNetwork, retryable = false)
    }

    /**
     * Minta server menghapus file avatar lama.
     *
     * Server hanya menghapus bila file itu tidak lagi dirujuk profil mana pun
     * (nama file = hash isi, jadi bisa dipakai bersama). Kegagalan diabaikan:
     * prune harian di server tetap membersihkan file yatim.
     */
    suspend fun deleteAvatar(url: String): Result<Unit> = runCatching {
        val marker = "/avatars/uploads/"
        val index = url.indexOf(marker)
        if (index < 0) return@runCatching
        val name = url.substring(index + marker.length).substringBefore('?').trim()
        if (name.isEmpty()) return@runCatching
        http.delete("$BASE_URL/avatars/uploads/$name") {
            header(HttpHeaders.UserAgent, UploadUserAgent)
            AuthStorage.loadIdentityToken()?.takeIf { it.isNotBlank() }?.let { token ->
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            timeout {
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
        }
        Unit
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}

internal class SignupRejected(val code: String) : Exception(code)

/** Penolakan unggah avatar; [retryable] menentukan apakah layak dicoba lagi. */
internal class AvatarUploadRejected(message: String, val retryable: Boolean) : Exception(message)

internal const val UploadUserAgent = "HOFLIX-Android/3.4"
internal const val UploadTimeoutMillis = 120_000L
internal const val IdentityTimeoutMillis = 45_000L
internal const val LegacyLookupTimeoutMillis = 12_000L
internal const val UsernameCheckTimeoutMillis = 8_000L
internal const val UploadConnectTimeoutMillis = 20_000L
private const val UploadAttempts = 2
private const val UploadRetryDelayMs = 1_200L
internal const val UploadDetailTimeout = "timeout"
internal const val UploadDetailNetwork = "jaringan"
