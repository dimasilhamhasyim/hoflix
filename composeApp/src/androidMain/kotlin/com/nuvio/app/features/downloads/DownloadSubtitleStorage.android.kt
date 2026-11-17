package com.nuvio.app.features.downloads

import android.util.AtomicFile
import java.io.File
import java.net.URI
import java.security.MessageDigest

internal actual class DownloadSubtitleStorage actual constructor(localVideoUri: String) {
    // Downloads end up in the public folder, so the video can be a MediaStore
    // URI that has no writable sibling directory. Subtitles are keyed by a hash
    // of that URI inside the app's own storage instead.
    private val directory: File = runCatching { URI(localVideoUri) }.getOrNull()
        ?.takeIf { it.scheme == "file" }
        ?.let { File(it.path + ".subtitles") }
        ?: File(subtitleRoot(), localVideoUri.sha256().take(24).ifBlank { "unknown" })

    actual fun read(fileName: String): String? =
        runCatching { AtomicFile(file(fileName)).readFully().decodeToString() }.getOrNull()

    actual fun write(fileName: String, text: String) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create subtitle directory" }
        val file = AtomicFile(file(fileName))
        val output = file.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    actual fun localFileUri(fileName: String): String? =
        file(fileName).takeIf { it.isFile }?.toURI()?.toString()

    actual fun remove() {
        directory.deleteRecursively()
    }

    private fun file(fileName: String): File {
        require(fileName.isNotBlank() && File(fileName).name == fileName && fileName != "." && fileName != "..")
        return File(directory, fileName)
    }
}

private fun subtitleRoot(): File =
    DownloadsPlatformDownloader.subtitleDirectory() ?: File(System.getProperty("java.io.tmpdir"), "hoflix-subtitles")

private fun String.sha256(): String = runCatching {
    MessageDigest.getInstance("SHA-256").digest(toByteArray())
        .joinToString("") { "%02x".format(it) }
}.getOrDefault("")
