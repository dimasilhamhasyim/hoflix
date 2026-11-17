package com.nuvio.app.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.nuvio.app.features.profiles.AvatarCropOutputSize
import com.nuvio.app.features.profiles.AvatarCropRect
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val MaxProfileImageEdge = 512
private const val MaxPickerSourceEdge = 1600
private const val PickerSourceQuality = 92
private const val AvatarOutputQuality = 90

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Composable
actual fun rememberProfileImagePicker(onPicked: (ProfileImagePickResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onPicked)

    val handleUri: (Uri?) -> Unit = { uri ->
        if (uri == null) {
            latest.value(ProfileImagePickResult.Cancelled)
        } else {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { readPickedImage(context, uri) }.getOrNull()
                }
                latest.value(
                    if (bytes == null) {
                        ProfileImagePickResult.Failed
                    } else {
                        ProfileImagePickResult.Picked(bytes)
                    },
                )
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> handleUri(uri) }
    val contentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> handleUri(uri) }

    return remember(context, photoPicker, contentPicker) {
        {
            val photoPickerAvailable = runCatching {
                ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
            }.getOrDefault(false)
            if (photoPickerAvailable) {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } else {
                // ROM tanpa Photo Picker (mis. Vivo lama) lebih sering berhasil
                // lewat pemilih galeri biasa daripada dokumen.
                contentPicker.launch("image/*")
            }
        }
    }
}

internal actual fun cropAvatarImage(source: ByteArray, crop: AvatarCropRect): ByteArray? = runCatching {
    val decoded = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
    val safeSize = crop.size.coerceIn(1, minOf(decoded.width, decoded.height))
    val left = crop.left.coerceIn(0, decoded.width - safeSize)
    val top = crop.top.coerceIn(0, decoded.height - safeSize)
    val square = Bitmap.createBitmap(decoded, left, top, safeSize, safeSize, null, true)
    val scaled = Bitmap.createScaledBitmap(square, AvatarCropOutputSize, AvatarCropOutputSize, true)
    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, AvatarOutputQuality, output)
    if (scaled !== square) scaled.recycle()
    if (square !== decoded) square.recycle()
    decoded.recycle()
    output.toByteArray().takeIf { it.isNotEmpty() }
}.getOrNull()

/**
 * Membaca gambar dari URI hasil pemilih. Hasilnya JPEG yang sudah diperkecil ke
 * [MaxPickerSourceEdge] agar editor potong tetap ringan di memori.
 */
private fun readPickedImage(context: Context, uri: Uri): ByteArray? {
    val resolver = context.contentResolver
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder(context, uri)
    } else {
        decodeWithBitmapFactory(context, uri)
    } ?: return null

    val oriented = applyExifRotation(context, uri, bitmap)
    val longest = maxOf(oriented.width, oriented.height)
    val scaled = if (longest > MaxPickerSourceEdge) {
        val ratio = MaxPickerSourceEdge.toFloat() / longest
        Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * ratio).toInt().coerceAtLeast(1),
            (oriented.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        oriented
    }
    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, PickerSourceQuality, output)
    if (scaled !== oriented) scaled.recycle()
    if (oriented !== bitmap) oriented.recycle()
    bitmap.recycle()
    return output.toByteArray().takeIf { it.isNotEmpty() }
}

private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > MaxPickerSourceEdge) {
            val sample = ((longest + MaxPickerSourceEdge - 1) / MaxPickerSourceEdge).coerceAtLeast(1)
            decoder.setTargetSampleSize(sample)
        }
    }
}

private fun decodeWithBitmapFactory(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > MaxPickerSourceEdge || bounds.outHeight / sample > MaxPickerSourceEdge) {
        sample *= 2
    }
    return resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** ImageDecoder sudah menegakkan orientasi EXIF; jalur lama perlu diputar sendiri. */
private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return bitmap
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)
    if (orientation == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(orientation) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    return rotated
}
