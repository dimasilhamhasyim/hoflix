package com.nuvio.app.features.profiles

import kotlin.math.roundToInt

/**
 * Model potong avatar. Semua nilai offset memakai satuan "layar" (1f = satu
 * lebar bingkai pratinjau), jadi rumus yang sama dipakai untuk pratinjau
 * Compose maupun pemotongan bitmap di platform.
 */
internal const val AvatarCropOutputSize = 512
internal const val AvatarCropMinZoom = 1f
internal const val AvatarCropMaxZoom = 4f

internal data class AvatarCropTransform(
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    companion object {
        val Default = AvatarCropTransform(AvatarCropMinZoom, 0f, 0f)
    }
}

internal data class AvatarCropRect(val left: Int, val top: Int, val size: Int)

/**
 * Skala yang membuat gambar menutup penuh bingkai persegi (cover), dinyatakan
 * dalam satuan layar per piksel sumber.
 */
private fun coverScale(imageWidth: Int, imageHeight: Int): Float =
    maxOf(1f / imageWidth, 1f / imageHeight)

/**
 * Batas geser untuk zoom tertentu. Nol berarti gambar sudah pas di satu sumbu
 * sehingga tidak boleh digeser ke arah itu.
 */
internal fun avatarMaxOffset(imageWidth: Int, imageHeight: Int, zoom: Float): Pair<Float, Float> {
    if (imageWidth <= 0 || imageHeight <= 0) return 0f to 0f
    val scale = coverScale(imageWidth, imageHeight)
    val z = zoom.coerceIn(AvatarCropMinZoom, AvatarCropMaxZoom)
    val maxX = ((imageWidth * scale * z) - 1f) / 2f
    val maxY = ((imageHeight * scale * z) - 1f) / 2f
    return maxX.coerceAtLeast(0f) to maxY.coerceAtLeast(0f)
}

internal fun clampAvatarCropTransform(
    imageWidth: Int,
    imageHeight: Int,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
): AvatarCropTransform {
    val z = zoom.coerceIn(AvatarCropMinZoom, AvatarCropMaxZoom)
    val (maxX, maxY) = avatarMaxOffset(imageWidth, imageHeight, z)
    return AvatarCropTransform(
        zoom = z,
        offsetX = offsetX.coerceIn(-maxX, maxX),
        offsetY = offsetY.coerceIn(-maxY, maxY),
    )
}

/**
 * Persegi sumber yang sedang terlihat di dalam bingkai. Dipakai untuk memotong
 * bitmap asli, jadi hasilnya selalu berada di dalam batas gambar.
 */
internal fun resolveAvatarCropRect(
    imageWidth: Int,
    imageHeight: Int,
    transform: AvatarCropTransform,
): AvatarCropRect? {
    if (imageWidth <= 0 || imageHeight <= 0) return null
    val clamped = clampAvatarCropTransform(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        zoom = transform.zoom,
        offsetX = transform.offsetX,
        offsetY = transform.offsetY,
    )
    val scale = coverScale(imageWidth, imageHeight) * clamped.zoom
    if (scale <= 0f) return null

    val side = (1f / scale).roundToInt().coerceIn(1, minOf(imageWidth, imageHeight))
    val centerX = imageWidth / 2f - (clamped.offsetX / scale)
    val centerY = imageHeight / 2f - (clamped.offsetY / scale)
    val left = (centerX - side / 2f).roundToInt().coerceIn(0, imageWidth - side)
    val top = (centerY - side / 2f).roundToInt().coerceIn(0, imageHeight - side)
    return AvatarCropRect(left = left, top = top, size = side)
}
