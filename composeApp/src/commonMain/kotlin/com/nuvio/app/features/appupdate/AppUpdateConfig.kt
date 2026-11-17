package com.nuvio.app.features.appupdate

import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.network.createApiHttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Konfigurasi versi dari bridge (`GET /hf/app/config.json`).
 *
 * Isinya hanya kebijakan rilis — versi terbaru, versi wajib, alamat APK, dan
 * sidiknya. Semua perilaku fitur tetap di kode; berkas ini tidak boleh dipakai
 * untuk mematikan atau menyalakan fitur, karena itu jalur yang berbeda.
 */
internal data class AppUpdateConfig(
    val latest: String = "",
    val latestCode: Int = 0,
    val minCode: Int = 0,
    val blockUnversioned: Boolean = false,
    val apkUrl: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0,
    val notes: String = "",
    val publishedAt: String = "",
) {
    /** Alamat unduhan hanya diterima kalau benar-benar https. */
    val downloadable: Boolean get() = apkUrl.startsWith("https://") && latestCode > 0

    fun blocks(installedCode: Int): Boolean = minCode > 0 && installedCode < minCode
}

/**
 * Sinyal global: server pernah menolak versi yang sedang berjalan (HTTP 426).
 *
 * Pemeriksaan saat start bisa ketinggalan — cache masih segar, atau fetch
 * konfigurasi gagal karena sinyal buruk. Kalau server sendiri sudah menolak,
 * tidak ada gunanya membiarkan pengguna masuk ke katalog kosong: sinyal ini
 * memindahkan gerbang ke layar perbarui tanpa menunggu cache kedaluwarsa.
 */
internal object AppUpdateSignal {
    private val _blocked = MutableStateFlow<AppUpdateConfig?>(null)
    val blocked: StateFlow<AppUpdateConfig?> = _blocked.asStateFlow()

    fun report(config: AppUpdateConfig) {
        if (config.latestCode > 0 || config.minCode > 0) _blocked.value = config
    }

    fun clear() {
        _blocked.value = null
    }
}

/**
 * Sumber tunggal kebijakan versi di sisi aplikasi.
 *
 * Aturan yang dipegang: **gagal jaringan tidak pernah mengunci aplikasi.**
 * Memblokir butuh bukti yang masih segar — jawaban server sekarang, atau cache
 * yang umurnya di bawah 24 jam. Cache tua + sinyal buruk = lanjut, karena
 * operator bisa saja sudah menurunkan `minCode` dan kita tidak tahu.
 */
internal object AppUpdateController {
    private const val ConfigUrl = "https://hoflix-api.siho.my.id/hf/app/config.json"
    private const val CacheKey = "hf_app_update_config_v1"
    private const val CacheMaxAgeMs = 24L * 60 * 60 * 1000
    private const val TimeoutMs = 8_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy { createApiHttpClient() }

    /** Kapan konfigurasi terakhir benar-benar diambil dari jaringan. */
    private var lastFetchAtMs = 0L

    /** Jarak minimum antar pemeriksaan paksa (resume beruntun tidak menghajar bridge). */
    private const val ForceFetchGapMs = 60_000L

    /** Versi yang sedang berjalan, dari build config. */
    val installedCode: Int get() = AppVersionConfig.VERSION_CODE
    val installedName: String get() = AppVersionConfig.VERSION_NAME

    private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun parse(raw: String): AppUpdateConfig? {
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        fun str(key: String): String = obj[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        return AppUpdateConfig(
            latest = str("latest"),
            latestCode = obj["latestCode"]?.jsonPrimitive?.intOrNull ?: 0,
            minCode = obj["minCode"]?.jsonPrimitive?.intOrNull ?: 0,
            blockUnversioned = obj["blockUnversioned"]?.jsonPrimitive?.booleanOrNull == true,
            apkUrl = str("apkUrl"),
            sha256 = str("sha256"),
            sizeBytes = obj["sizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L,
            notes = str("notes"),
            publishedAt = str("publishedAt"),
        )
    }

    private fun saveCache(config: AppUpdateConfig) {
        val body = buildString {
            append("{\"at\":").append(nowMs())
            append(",\"latest\":\"").append(escape(config.latest)).append('"')
            append(",\"latestCode\":").append(config.latestCode)
            append(",\"minCode\":").append(config.minCode)
            append(",\"blockUnversioned\":").append(config.blockUnversioned)
            append(",\"apkUrl\":\"").append(escape(config.apkUrl)).append('"')
            append(",\"sha256\":\"").append(escape(config.sha256)).append('"')
            append(",\"sizeBytes\":").append(config.sizeBytes)
            append(",\"notes\":\"").append(escape(config.notes)).append('"')
            append(",\"publishedAt\":\"").append(escape(config.publishedAt)).append('"')
            append('}')
        }
        runCatching { AuthStorage.saveText(CacheKey, body) }
    }

    private fun loadCache(): Pair<AppUpdateConfig, Long>? {
        val raw = runCatching { AuthStorage.loadText(CacheKey) }.getOrNull() ?: return null
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val at = obj["at"]?.jsonPrimitive?.longOrNull ?: return null
        fun str(key: String): String = obj[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val config = AppUpdateConfig(
            latest = str("latest"),
            latestCode = obj["latestCode"]?.jsonPrimitive?.intOrNull ?: 0,
            minCode = obj["minCode"]?.jsonPrimitive?.intOrNull ?: 0,
            blockUnversioned = obj["blockUnversioned"]?.jsonPrimitive?.booleanOrNull == true,
            apkUrl = str("apkUrl"),
            sha256 = str("sha256"),
            sizeBytes = obj["sizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L,
            notes = str("notes"),
            publishedAt = str("publishedAt"),
        )
        return config to at
    }

    private suspend fun fetchRemote(): AppUpdateConfig? = runCatching {
        http.get(ConfigUrl) {
            timeout { requestTimeoutMillis = TimeoutMs; connectTimeoutMillis = TimeoutMs }
        }.let { response ->
            if (!response.status.isSuccess()) return@let null
            val text = response.bodyAsText()
            parse(text)?.also {
                lastFetchAtMs = nowMs()
                saveCache(it)
            }
        }
    }.getOrNull()

    /**
     * Konfigurasi yang memblokir versi ini, atau `null` kalau boleh lanjut.
     *
     * `force = true` dipakai saat aplikasi kembali dari latar: operator bisa
     * menaikkan `minCode` kapan saja tanpa harus menunggu cache kedaluwarsa.
     * Pemeriksaan paksa dibatasi satu per menit supaya resume beruntun tidak
     * menghajar bridge.
     */
    suspend fun blockingConfig(force: Boolean = false): AppUpdateConfig? {
        val installed = installedCode
        val cached = loadCache()
        val forceAllowed = force && nowMs() - lastFetchAtMs >= ForceFetchGapMs
        if (!forceAllowed && cached != null && nowMs() - cached.second < CacheMaxAgeMs) {
            return cached.first.takeIf { it.blocks(installed) }
        }
        val remote = fetchRemote()
        if (remote != null) return remote.takeIf { it.blocks(installed) }
        // Server tidak terjangkau: cache hanya dipakai kalau masih segar.
        return cached
            ?.takeIf { nowMs() - it.second < CacheMaxAgeMs }
            ?.first
            ?.takeIf { it.blocks(installed) }
    }

    /** Konfigurasi terakhir yang diketahui, untuk layar perbarui. */
    suspend fun current(): AppUpdateConfig? = loadCache()?.first ?: fetchRemote()

    /**
     * Dipanggil saat server membalas 426 di tengah pemakaian.
     *
     * Urutannya sengaja: ambil kebijakan terbaru dulu supaya `apkUrl` dan
     * `sha256` yang ditawarkan benar-benar menunjuk rilis terakhir. Kalau
     * jaringan gagal, pakai salinan terakhir — lebih baik menampilkan layar
     * perbarui dengan berkas yang sedikit lebih tua daripada membiarkan
     * pengguna menatap katalog kosong.
     */
    suspend fun reportServerRejection() {
        val config = fetchRemote() ?: loadCache()?.first ?: return
        AppUpdateSignal.report(config)
    }

    private fun escape(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> if (char.code >= 0x20) append(char)
            }
        }
    }
}
