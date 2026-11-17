package com.nuvio.app.features.profiles

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Matematika potong avatar murni, jadi diuji di JVM biasa tanpa Robolectric.
 */
class AvatarCropModelTest {

    @Test
    fun landscapePhotoIsCroppedToCentreSquareAtDefaultZoom() {
        val rect = assertNotNull(
            resolveAvatarCropRect(
                imageWidth = 1000,
                imageHeight = 500,
                transform = AvatarCropTransform.Default,
            ),
        )
        assertEquals(500, rect.size)
        assertEquals(250, rect.left)
        assertEquals(0, rect.top)
    }

    @Test
    fun portraitPhotoIsCroppedToCentreSquareAtDefaultZoom() {
        val rect = assertNotNull(
            resolveAvatarCropRect(
                imageWidth = 600,
                imageHeight = 900,
                transform = AvatarCropTransform.Default,
            ),
        )
        assertEquals(600, rect.size)
        assertEquals(0, rect.left)
        assertEquals(150, rect.top)
    }

    @Test
    fun zoomHalvesTheCroppedSide() {
        val rect = assertNotNull(resolveAvatarCropRect(1000, 500, AvatarCropTransform(2f, 0f, 0f)))
        assertEquals(250, rect.size)
        assertEquals(375, rect.left)
    }

    @Test
    fun offsetIsClampedToTheImageEdges() {
        val maxOffset = avatarMaxOffset(imageWidth = 1000, imageHeight = 500, zoom = 1f)
        assertEquals(0.5f, maxOffset.first, 0.0001f)
        assertEquals(0f, maxOffset.second, 0.0001f)

        val clamped = clampAvatarCropTransform(1000, 500, zoom = 1f, offsetX = 9f, offsetY = -9f)
        assertEquals(0.5f, clamped.offsetX, 0.0001f)
        assertEquals(0f, clamped.offsetY, 0.0001f)
    }

    @Test
    fun offsetMovesTheCropWindowToTheEdge() {
        val transform = clampAvatarCropTransform(1000, 500, zoom = 1f, offsetX = 0.5f, offsetY = 0f)
        val rect = assertNotNull(resolveAvatarCropRect(1000, 500, transform))
        assertEquals(500, rect.size)
        assertEquals(0, rect.left, "Geser ke kanan harus memperlihatkan tepi kiri foto")
    }

    @Test
    fun zoomIsClampedToTheSupportedRange() {
        val tooSmall = clampAvatarCropTransform(800, 800, zoom = 0.2f, offsetX = 0f, offsetY = 0f)
        val tooBig = clampAvatarCropTransform(800, 800, zoom = 25f, offsetX = 0f, offsetY = 0f)
        assertEquals(AvatarCropMinZoom, tooSmall.zoom)
        assertEquals(AvatarCropMaxZoom, tooBig.zoom)
    }

    @Test
    fun everyTransformStaysInsideTheImage() {
        val sizes = listOf(1 to 1, 37 to 512, 1600 to 900, 900 to 1600, 512 to 512)
        val zooms = listOf(1f, 1.4f, 2f, 3.3f, 4f)
        val offsets = listOf(-5f, -0.5f, -0.13f, 0f, 0.27f, 0.5f, 5f)
        for ((width, height) in sizes) {
            for (zoom in zooms) {
                for (offsetX in offsets) {
                    for (offsetY in offsets) {
                        val rect = assertNotNull(
                            resolveAvatarCropRect(width, height, AvatarCropTransform(zoom, offsetX, offsetY)),
                            "Tidak ada hasil potong untuk $width x $height zoom $zoom",
                        )
                        assertTrue(rect.size >= 1)
                        assertTrue(rect.size <= minOf(width, height))
                        assertTrue(rect.left >= 0 && rect.left + rect.size <= width)
                        assertTrue(rect.top >= 0 && rect.top + rect.size <= height)
                    }
                }
            }
        }
    }

    @Test
    fun croppedCentreTracksTheOffset() {
        val shiftedRight = assertNotNull(
            resolveAvatarCropRect(1000, 500, AvatarCropTransform(1f, 0.25f, 0f)),
        )
        val shiftedLeft = assertNotNull(
            resolveAvatarCropRect(1000, 500, AvatarCropTransform(1f, -0.25f, 0f)),
        )
        assertTrue(
            abs(shiftedLeft.left - shiftedRight.left) == 250,
            "Offset 0.25 pada foto 1000x500 harus menggeser 250 px, dapat ${shiftedLeft.left} vs ${shiftedRight.left}",
        )
    }

    @Test
    fun emptyImageHasNoCrop() {
        assertNull(resolveAvatarCropRect(0, 500, AvatarCropTransform.Default))
        assertNull(resolveAvatarCropRect(500, 0, AvatarCropTransform.Default))
    }
}
