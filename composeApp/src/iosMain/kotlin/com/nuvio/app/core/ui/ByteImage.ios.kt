package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.nuvio.app.features.profiles.AvatarCropOutputSize
import com.nuvio.app.features.profiles.AvatarCropRect
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

@Composable
actual fun rememberProfileImagePicker(onPicked: (ProfileImagePickResult) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onPicked)
    // Pemilih galeri khusus iOS belum dipasang di build ini; panggilan
    // dilaporkan sebagai batal supaya UI tidak menggantung.
    return { latest.value(ProfileImagePickResult.Cancelled) }
}

internal actual fun cropAvatarImage(source: ByteArray, crop: AvatarCropRect): ByteArray? = runCatching {
    val image = Image.makeFromEncoded(source)
    val side = minOf(crop.size, image.width, image.height)
    if (side <= 0) return null
    val surface = Surface.makeRasterN32Premul(AvatarCropOutputSize, AvatarCropOutputSize)
    surface.canvas.drawImageRect(
        image,
        Rect.makeXYWH(
            crop.left.coerceIn(0, image.width - side).toFloat(),
            crop.top.coerceIn(0, image.height - side).toFloat(),
            side.toFloat(),
            side.toFloat(),
        ),
        Rect.makeWH(AvatarCropOutputSize.toFloat(), AvatarCropOutputSize.toFloat()),
    )
    surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 90)?.bytes
}.getOrNull()
