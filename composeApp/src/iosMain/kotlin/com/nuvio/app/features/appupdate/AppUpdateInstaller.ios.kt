package com.nuvio.app.features.appupdate

/**
 * iOS tidak memasang pembaruan dari dalam aplikasi: berkas IPA harus lewat
 * TestFlight atau sideload. Kabijakan versi tetap dihormati — layar perbarui
 * tetap muncul dan mengarahkan pengguna ke tautan unduhan.
 */
internal actual object AppUpdateInstaller {
    actual fun isSupported(): Boolean = false

    actual fun canInstall(): Boolean = false

    actual fun openPermissionSettings() = Unit

    actual suspend fun download(
        config: AppUpdateConfig,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = Result.failure(
        IllegalStateException("Pembaruan dalam aplikasi belum tersedia di iOS."),
    )

    actual fun install(path: String): Result<Unit> = Result.failure(
        IllegalStateException("Pembaruan dalam aplikasi belum tersedia di iOS."),
    )
}
