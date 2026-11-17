package com.nuvio.app.features.appupdate

/**
 * Pemasangan pembaruan di dalam aplikasi.
 *
 * Aplikasi tidak pernah memasang APK yang tidak diminta kebijakan versi, dan
 * tidak pernah memasang berkas yang sidiknya tidak sama dengan yang dikirim
 * server. Implementasi Android memeriksa dua hal sebelum membuka installer:
 * SHA-256 berkas terhadap konfigurasi, dan sertifikat penanda tangan terhadap
 * sertifikat aplikasi ini.
 */
internal expect object AppUpdateInstaller {
    /** Platform ini bisa memasang pembaruan sendiri. */
    fun isSupported(): Boolean

    /** Izin "pasang dari sumber tidak dikenal" sudah diberikan. */
    fun canInstall(): Boolean

    /** Buka pengaturan izin pemasangan untuk aplikasi ini. */
    fun openPermissionSettings()

    /**
     * Unduh APK ke penyimpanan internal aplikasi dan verifikasi sidiknya.
     * Mengembalikan path berkas yang sudah lolos verifikasi.
     */
    suspend fun download(
        config: AppUpdateConfig,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String>

    /** Buka installer sistem untuk berkas yang sudah diverifikasi. */
    fun install(path: String): Result<Unit>
}
