package com.nuvio.app.features.downloads

import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadQualityOptionsTest {
    private fun stream(
        url: String,
        height: Int,
        bytes: Long? = null,
        audioId: String? = null,
        audioLabel: String? = null,
    ) = StreamItem(
        name = "HOFLIX",
        title = "${height}p",
        url = url,
        addonName = "HOFLIX",
        addonId = "org.hoflix.moviebox",
        behaviorHints = StreamBehaviorHints(
            qualityHeight = height,
            sizeBytes = bytes,
            audioId = audioId,
            audioLabel = audioLabel,
            audioOriginal = audioId == null,
        ),
    )

    @Test
    fun keepsOnlyProgressiveMp4() {
        val options = buildDownloadQualityOptions(
            listOf(
                stream("https://cdn.example/manifest.mpd", 1080),
                stream("https://cdn.example/master.m3u8", 1080),
                stream("https://cdn.example/480.mp4", 480, 320_000_000),
            ),
        )
        assertEquals(listOf(480), options.map { it.height })
    }

    @Test
    fun oneOptionPerResolutionHighestFirst() {
        val options = buildDownloadQualityOptions(
            listOf(
                stream("https://cdn.example/360.mp4", 360, 260_000_000),
                stream("https://cdn.example/1080.mp4", 1080, 3_530_000_000),
                stream("https://cdn.example/720.mp4", 720, 1_700_000_000),
                stream("https://cdn.example/1080-alt.mp4", 1080, 3_100_000_000),
            ),
        )
        assertEquals(listOf(1080, 720, 360), options.map { it.height })
        assertEquals(3_530_000_000L, options.first().sizeBytes)
    }

    @Test
    fun audioFilterKeepsThePlayingDub() {
        val streams = listOf(
            stream("https://cdn.example/orig-480.mp4", 480, audioId = null),
            stream("https://cdn.example/hindi-480.mp4", 480, audioId = "42", audioLabel = "Hindi dub"),
            stream("https://cdn.example/hindi-720.mp4", 720, audioId = "42", audioLabel = "Hindi dub"),
        )
        val options = buildDownloadQualityOptions(streams, audioId = "42")
        assertEquals(listOf(720, 480), options.map { it.height })
        assertEquals("Hindi dub", options.first().audioLabel)
    }

    @Test
    fun unknownAudioFallsBackToEveryVariant() {
        val options = buildDownloadQualityOptions(
            listOf(
                stream("https://cdn.example/orig-480.mp4", 480),
                stream("https://cdn.example/hindi-720.mp4", 720, audioId = "42"),
            ),
            audioId = "missing",
        )
        assertEquals(listOf(720, 480), options.map { it.height })
    }

    @Test
    fun sizeLabelUsesReadableUnits() {
        assertTrue(formatDownloadBytes(3_530_000_000).endsWith("GB"))
        assertEquals("320 MB", formatDownloadBytes(320_000_000))
        assertEquals("", formatDownloadBytes(0))
    }
}
