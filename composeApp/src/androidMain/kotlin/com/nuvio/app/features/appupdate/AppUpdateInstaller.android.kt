package com.nuvio.app.features.appupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Konteks aplikasi untuk pemasang pembaruan; dipasang sekali dari MainActivity. */
internal object AppUpdateContext {
    @Volatile
    var context: Context? = null
}

internal fun initializeAppUpdateInstaller(context: Context) {
    AppUpdateContext.context = context.applicationContext
}

internal actual object AppUpdateInstaller {
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    actual fun isSupported(): Boolean = true

    actual fun canInstall(): Boolean {
        val context = AppUpdateContext.context ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    }

    actual fun openPermissionSettings() {
        val context = AppUpdateContext.context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    actual suspend fun download(
        config: AppUpdateConfig,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val context = requireNotNull(AppUpdateContext.context) { "Konteks aplikasi belum siap." }
            require(config.apkUrl.startsWith("https://")) { "Alamat unduhan tidak aman." }
            val expected = config.sha256.trim().lowercase()
            require(expected.length == 64) {
                "Sidik pembaruan tidak tersedia. Perbarui lewat situs."
            }

            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "hoflix-${config.latestCode}.apk")
            val partial = File(dir, "hoflix-${config.latestCode}.apk.part")
            partial.delete()
            target.delete()

            http.newCall(Request.Builder().url(config.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) error("Unduhan gagal (HTTP ${response.code}).")
                val body = response.body ?: error("Server mengirim berkas kosong.")
                val total = body.contentLength().takeIf { it > 0L }
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            currentCoroutineContext().ensureActive()
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
            }

            val actual = sha256Of(partial)
            if (actual != expected) {
                partial.delete()
                error("Sidik berkas tidak cocok. Unduhan dibatalkan.")
            }
            partial.renameTo(target)
            target.absolutePath
        }
    }

    actual fun install(path: String): Result<Unit> = runCatching {
        val context = requireNotNull(AppUpdateContext.context) { "Konteks aplikasi belum siap." }
        val apk = File(path)
        check(apk.exists()) { "Berkas pembaruan tidak ditemukan." }
        check(signedBySameKey(context, path)) {
            apk.delete()
            "Pembaruan ditolak: tanda tangan berbeda dengan aplikasi ini."
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signatureDigest(signature: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * APK yang akan dipasang wajib ditandatangani kunci yang sama dengan
     * aplikasi ini. Tanpa pemeriksaan ini, berkas dari host mana pun bisa
     * dibuka di installer selama sidik SHA-256-nya cocok dengan konfigurasi —
     * dan konfigurasi itu datang dari jaringan.
     */
    private fun signedBySameKey(context: Context, path: String): Boolean = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(path, flags) ?: return false
        val candidate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            archive.signatures
        }?.firstOrNull() ?: return false
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            installed.signatures
        }?.map { signatureDigest(it) }?.toSet().orEmpty()
        current.isNotEmpty() && signatureDigest(candidate) in current
    }.getOrDefault(false)
}
