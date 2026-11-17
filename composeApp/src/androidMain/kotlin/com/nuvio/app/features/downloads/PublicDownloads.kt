package com.nuvio.app.features.downloads

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Final home of a downloaded episode: the phone's own Download/HOFLIX folder,
 * where any file manager can see it — the same place MovieBox puts its files.
 *
 * Android 10+ goes through MediaStore, so no storage permission is needed.
 * Older devices fall back to the private folder when the permission was not
 * granted, which keeps the download usable instead of failing it.
 */
internal object PublicDownloads {
    const val FOLDER = "HOFLIX"

    fun isEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 || hasLegacyWritePermission(context)

    /** Copies [source] into the public folder and returns the playable URI. */
    fun publish(context: Context, source: File, displayName: String): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            return publishToMediaStore(context, source, displayName)
        }
        if (!hasLegacyWritePermission(context)) {
            return Uri.fromFile(source)
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER,
        )
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Tidak bisa membuat folder unduhan")
        }
        val target = uniqueTarget(directory, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf("video/mp4"),
            null,
        )
        return Uri.fromFile(target)
    }

    fun delete(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return runCatching {
            when (uri.scheme) {
                "content" -> context.contentResolver.delete(uri, null, null) >= 0
                else -> uri.path?.let { File(it).delete() } ?: false
            }
        }.getOrDefault(false)
    }

    fun exists(context: Context, uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return runCatching {
            when (uri.scheme) {
                "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true }
                    ?: false
                else -> uri.path?.let { File(it).isFile } ?: false
            }
        }.getOrDefault(false)
    }

    private fun publishToMediaStore(context: Context, source: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Tidak bisa menulis ke folder unduhan")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, 256 * 1024) }
            } ?: throw IOException("Tidak bisa membuka file unduhan")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        return uri
    }

    private fun uniqueTarget(directory: File, displayName: String): File {
        var candidate = File(directory, displayName)
        if (!candidate.exists()) return candidate
        val base = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        var index = 2
        while (candidate.exists()) {
            val name = if (extension.isBlank()) "$base ($index)" else "$base ($index).$extension"
            candidate = File(directory, name)
            index += 1
        }
        return candidate
    }

    private fun hasLegacyWritePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 && context.checkSelfPermission(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
}
