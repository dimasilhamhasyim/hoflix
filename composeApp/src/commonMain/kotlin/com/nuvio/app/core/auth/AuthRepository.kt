package com.nuvio.app.core.auth

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.storage.LocalAccountDataCleaner
import com.nuvio.app.features.auth.AccountGateway
import com.nuvio.app.features.auth.SignupRejected
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

private const val KEY_HAD_ACCOUNT_SESSION = "had_account_session"
private const val KEY_SIGNED_OUT_NOTICE = "signed_out_notice"

/** Jeda sebelum satu percobaan pemulihan sesi; memberi waktu jaringan/refresh selesai. */
private const val SESSION_RECOVERY_DELAY_MS = 1_500L

/** Jumlah percobaan refresh sebelum sesi dianggap benar-benar mati. */
private const val SESSION_REFRESH_ATTEMPTS = 2

/** Sesi Supabase datang sebagai JSON GoTrue; field tak dikenal diabaikan. */
private val grantSessionJson = Json { ignoreUnknownKeys = true; isLenient = true }

object AuthRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("AuthRepository")

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var initialized = false
    private var sessionStatusJob: Job? = null
    private var validatedRemoteUserId: String? = null
    /** Sekali per proses: token identitas diperiksa ke bridge saat aplikasi dibuka. */
    private var identityChecked = false
    private val refreshMutex = Mutex()

    fun initialize() {
        if (initialized) return
        initialized = true

        // HOFLIX mewajibkan akun. Sesi anonymous dari build lama tidak boleh
        // membuka app, jadi kuncinya dibuang dan gate jatuh ke halaman masuk.
        if (AuthStorage.loadAnonymousUserId() != null) {
            AuthStorage.clearAnonymousUserId()
        }

        // Local-only builds ship without a backend URL; creating a Supabase
        // client there throws, and the gate must still open.
        if (ServerConfigurationRepository.active.value.backendUrl.isBlank()) {
            _state.value = AuthState.Unauthenticated
            return
        }

        sessionStatusJob = scope.launch {
            if (AuthStorage.loadFlag(KEY_SIGNED_OUT_NOTICE)) {
                _error.value = getString(Res.string.auth_notice_signed_out)
            }
            SupabaseProvider.client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        val userId = user?.id.orEmpty()
                        if (userId.isBlank()) return@collect
                        // Cold start tidak lagi memanggil jaringan untuk
                        // memvalidasi sesi: plugin Supabase sudah punya satu
                        // jalur refresh, dan panggilan kedua di sini yang dulu
                        // membuat refresh token saling membunuh.
                        validatedRemoteUserId = userId
                        // Sesi harus diperpanjang sendiri selama aplikasi hidup.
                        // Tanpa ini access token mati tiap jam dan setiap buka app
                        // berakhir di halaman masuk — gejala "sering relogin".
                        if (!SupabaseProvider.client.auth.isAutoRefreshRunning) {
                            scope.launch {
                                runCatching {
                                    SupabaseProvider.client.auth.startAutoRefreshForCurrentSession()
                                }.onFailure { log.w(it) { "Supabase auto-refresh could not start" } }
                            }
                        }
                        AuthStorage.saveFlag(KEY_HAD_ACCOUNT_SESSION, true)
                        AuthStorage.saveFlag(KEY_SIGNED_OUT_NOTICE, false)
                        _state.value = AuthState.Authenticated(
                            userId = userId,
                            email = user?.email,
                            isAnonymous = false,
                        )
                        verifyIdentityOnce()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        if (!AuthStorage.loadFlag(KEY_HAD_ACCOUNT_SESSION)) {
                            _state.value = AuthState.Unauthenticated
                            return@collect
                        }
                        // Sesi hilang bisa sekadar gangguan sementara (jaringan
                        // putus, server 5xx, rotasi refresh token). Coba pulihkan
                        // sekali sebelum menyimpulkan user benar-benar keluar.
                        if (recoverLostSession()) return@collect
                        log.w { "Stored session could not be recovered; signing out without wiping local data" }
                        resetAfterLostSession()
                    }
                    is SessionStatus.Initializing -> {
                        _state.value = AuthState.Loading
                    }
                    is SessionStatus.RefreshFailure -> Unit
                }
            }
        }
    }

    /**
     * Satu percobaan memulihkan sesi setelah `NotAuthenticated`. Kegagalan
     * jaringan tidak pernah dihitung sebagai logout.
     */
    private suspend fun recoverLostSession(): Boolean = refreshMutex.withLock {
        delay(SESSION_RECOVERY_DELAY_MS)
        runCatching { SupabaseProvider.client.auth.refreshCurrentSession() }
            .onFailure { log.w(it) { "Session recovery attempt failed" } }
            .isSuccess
    }

    /**
     * Sekali per proses: pastikan token identitas masih dikenali bridge.
     *
     * Token yang ditolak (dicabut, sesi dibersihkan, atau akun keluar di
     * perangkat lain) dulu tetap terkirim di setiap permintaan, jadi app tampak
     * login padahal semua play jatuh ke tier tamu 720. Sekarang token mati
     * dibuang supaya request tidak membawa sesi yang sudah tidak sah.
     *
     * Kegagalan jaringan **tidak** dianggap token mati.
     */
    private fun verifyIdentityOnce() {
        if (identityChecked) return
        identityChecked = true
        val token = runCatching { AuthStorage.loadIdentityToken() }.getOrNull()
        if (token.isNullOrBlank()) return
        scope.launch {
            val rejected = runCatching { AccountGateway.identityRejected(token) }.getOrNull()
            if (rejected == true) {
                val stillStored = runCatching { AuthStorage.loadIdentityToken() }.getOrNull()
                if (stillStored == token) {
                    AuthStorage.clearIdentityToken()
                    log.w { "Identity token rejected by bridge; cleared so requests stop carrying a dead session" }
                }
            }
        }
    }

    /**
     * Dipakai setelah sesi email berhasil dibuat: mengesampingkan kunci
     * anonymous supaya state akun yang menang, tanpa menunggu collector sesi.
     */
    fun markSignedInFromSession(userId: String, email: String?) {
        validatedRemoteUserId = null
        AuthStorage.saveFlag(KEY_HAD_ACCOUNT_SESSION, true)
        AuthStorage.saveFlag(KEY_SIGNED_OUT_NOTICE, false)
        _state.value = AuthState.Authenticated(
            userId = userId,
            email = email,
            isAnonymous = false,
        )
    }

    /**
     * Daftar akun. Akun IDLIX dibuat lebih dulu oleh bridge; sesi Supabase yang
     * dikembalikan hanya jembatan sinkron, bukan identitas kedua.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String = "",
        turnstileToken: String = "",
    ): Result<Unit> = runCatching {
        _error.value = null
        val grant = AccountGateway
            .registerAccount(email, password, username, turnstileToken)
            .getOrElse { throw it }
        AuthStorage.saveIdentityToken(grant.token)
        // Token yang tidak dikenali bridge tidak boleh tersimpan: request
        // katalog akan membawa sesi mati dan semua play jatuh ke tier tamu.
        if (!AccountGateway.verifyIdentity(grant.token)) {
            AuthStorage.clearIdentityToken()
            log.w { "Identity token rejected by bridge after sign-up; cleared" }
        }
        signInWithGrant(grant)
    }.onFailure { e ->
        log.e(e) { "IDLIX sign-up failed" }
        _error.value = e.identityErrorDescription()
            ?: getString(Res.string.auth_sign_up_failed)
    }

    /** Masuk dengan akun IDLIX — identitas HOFLIX sama dengan akun itu. */
    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        _error.value = null
        val grant = AccountGateway.signInAccount(email, password).getOrElse { throw it }
        AuthStorage.saveIdentityToken(grant.token)
        if (!AccountGateway.verifyIdentity(grant.token)) {
            AuthStorage.clearIdentityToken()
            log.w { "Identity token rejected by bridge after sign-in; cleared" }
        }
        signInWithGrant(grant)
    }.onFailure { e ->
        log.e(e) { "IDLIX sign-in failed" }
        _error.value = e.identityErrorDescription()
            ?: getString(Res.string.auth_sign_in_failed)
    }

    private suspend fun signInWithGrant(grant: AccountGateway.AccountGrant) {
        // Sesi Supabase sudah ditukar bridge (sesi pendek + refresh token),
        // bukan password. Impor sesi itu apa adanya — token-nya bisa dicabut,
        // password turunan tidak.
        val session = grantSessionJson.decodeFromString<UserSession>(grant.supabaseSessionJson)
        SupabaseProvider.client.auth.importSession(session)
    }

    /** Pesan dari bridge (`kode|pesan`) dipakai apa adanya kalau ada. */
    private fun Throwable.identityErrorDescription(): String? {
        val rejected = generateSequence(this) { it.cause }
            .filterIsInstance<SignupRejected>()
            .firstOrNull() ?: return null
        return rejected.code.substringAfter('|', "").takeIf { it.isNotBlank() }
    }

    suspend fun signOut(): Result<Unit> {
        _error.value = null
        // Sesi IDLIX hidup di server (cookie). Tanpa panggilan ini, tiap
        // logout→login menambah sesi baru sampai akun kena batas sign-in.
        val identity = runCatching { AuthStorage.loadIdentityToken() }.getOrNull()
        if (!identity.isNullOrBlank()) {
            runCatching { AccountGateway.signOutAccount(identity) }
        }
        val anonymousRead = runCatching { AuthStorage.loadAnonymousUserId() }
        val wasAnonymous = anonymousRead.getOrNull() != null
        val anonymousClear = runCatching { AuthStorage.clearAnonymousUserId() }
        AuthStorage.saveFlag(KEY_HAD_ACCOUNT_SESSION, false)
        AuthStorage.saveFlag(KEY_SIGNED_OUT_NOTICE, false)
        AuthStorage.clearIdentityToken()
        validatedRemoteUserId = null
        val remoteSignOut = if (wasAnonymous) {
            Result.success(Unit)
        } else {
            runCatching { SupabaseProvider.client.auth.signOut() }
        }

        val fallbackSessionClear = if (remoteSignOut.isFailure) {
            runCatching { SupabaseProvider.client.auth.clearSession() }
                .onFailure { error -> log.w(error) { "Failed to clear Supabase session after sign-out failure" } }
        } else {
            Result.success(Unit)
        }
        val localCleanup = runCatching { LocalAccountDataCleaner.wipe() }
        _state.value = AuthState.Unauthenticated

        val failure = anonymousRead.exceptionOrNull()
            ?: anonymousClear.exceptionOrNull()
            ?: remoteSignOut.exceptionOrNull()
            ?: fallbackSessionClear.exceptionOrNull()
            ?: localCleanup.exceptionOrNull()
        val cancellation = remoteSignOut.exceptionOrNull() as? CancellationException
            ?: fallbackSessionClear.exceptionOrNull() as? CancellationException
        if (cancellation != null) throw cancellation
        return if (failure == null) {
            Result.success(Unit)
        } else {
            log.e(failure) { "Sign-out did not complete cleanly; all local cleanup steps were attempted" }
            _error.value = failure.message ?: runCatching {
                getString(Res.string.auth_sign_out_failed)
            }.getOrDefault("Sign out failed")
            Result.failure(failure)
        }
    }

    suspend fun prepareForServerSwitch(): Result<Unit> {
        _error.value = null
        val anonymousClear = runCatching { AuthStorage.clearAnonymousUserId() }
        AuthStorage.saveFlag(KEY_HAD_ACCOUNT_SESSION, false)
        validatedRemoteUserId = null
        val sessionClear = runCatching { SupabaseProvider.client.auth.clearSession() }
        _state.value = AuthState.Unauthenticated
        val failure = anonymousClear.exceptionOrNull() ?: sessionClear.exceptionOrNull()
        val cancellation = sessionClear.exceptionOrNull() as? CancellationException
        if (cancellation != null) throw cancellation
        return if (failure == null) Result.success(Unit) else Result.failure(failure)
    }

    fun reinitialize() {
        sessionStatusJob?.cancel()
        sessionStatusJob = null
        initialized = false
        validatedRemoteUserId = null
        _state.value = AuthState.Loading
        initialize()
    }

    suspend fun signOutIfSessionInvalid(error: Throwable, source: String): Boolean {
        val invalid = if (error.isJwtExpiredError()) refreshRejected() else error.isDeletedAccountError()
        if (!invalid) return false

        log.w(error) { "$source failed because the current Supabase account/session is no longer valid; clearing local auth" }
        clearLocalSessionAfterRemoteInvalidation()
        return true
    }

    private suspend fun refreshRejected(): Boolean = refreshMutex.withLock {
        repeat(SESSION_REFRESH_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(SESSION_RECOVERY_DELAY_MS)
            try {
                SupabaseProvider.client.auth.refreshCurrentSession()
                return@withLock false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log.w(e) { "Supabase session refresh failed (attempt ${attempt + 1})" }
                // Hanya kode refresh yang definitif boleh berujung logout.
                // Jaringan/5xx/429 = gangguan sementara, sesi dipertahankan.
                if (!e.isInvalidRefreshError()) return@withLock false
            }
        }
        true
    }

    private suspend fun clearLocalSessionAfterRemoteInvalidation() {
        AuthStorage.saveFlag(KEY_HAD_ACCOUNT_SESSION, false)
        runCatching {
            SupabaseProvider.client.auth.clearSession()
        }.onFailure { e ->
            log.w(e) { "Failed to clear Supabase session after remote invalidation; continuing local reset" }
        }
        resetAfterLostSession()
    }

    /**
     * Sesi benar-benar tidak bisa dipulihkan. Hanya sesi yang dibuang —
     * profil dan riwayat lokal tidak dihapus. Penghapusan data lokal hanya
     * terjadi saat user menekan Keluar sendiri.
     */
    private suspend fun resetAfterLostSession() {
        val notice = runCatching { getString(Res.string.auth_notice_signed_out) }.getOrNull()
        AuthStorage.clearAnonymousUserId()
        AuthStorage.clearIdentityToken()
        validatedRemoteUserId = null
        AuthStorage.saveFlag(KEY_HAD_ACCOUNT_SESSION, false)
        AuthStorage.saveFlag(KEY_SIGNED_OUT_NOTICE, true)
        _error.value = notice
        _state.value = AuthState.Unauthenticated
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        _error.value = null
        SupabaseProvider.client.functions.invoke("delete-account")
        AuthStorage.saveFlag(KEY_HAD_ACCOUNT_SESSION, false)
        SupabaseProvider.client.auth.signOut()
        validatedRemoteUserId = null
        try {
            LocalAccountDataCleaner.wipe()
        } finally {
            _state.value = AuthState.Unauthenticated
        }
    }.onFailure { e ->
        log.e(e) { "Account deletion failed" }
        _error.value = e.message ?: getString(Res.string.auth_account_deletion_failed)
    }

    fun clearError() {
        _error.value = null
    }

    private fun Throwable.safeAuthErrorDescription(): String? =
        findCause<AuthRestException>()
            ?.errorDescription
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: findCause<RestException>()
                ?.description
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
}
