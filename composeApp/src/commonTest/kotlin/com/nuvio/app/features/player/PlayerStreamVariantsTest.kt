package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerStreamVariantsTest {

    private fun stream(
        url: String,
        audioId: String,
        audioLabel: String,
        original: Boolean,
        height: Int,
        adaptive: Boolean,
    ) = StreamItem(
        url = url,
        addonName = "HOFLIX",
        addonId = "addon:hoflix",
        behaviorHints = StreamBehaviorHints(
            audioId = audioId,
            audioLabel = audioLabel,
            audioOriginal = original,
            qualityHeight = height,
            adaptive = adaptive,
        ),
    )

    private val streams = listOf(
        stream("https://x/dash-original.mpd", "500", "Original Audio", true, 1080, true),
        stream("https://x/original-480.mp4", "500", "Original Audio", true, 480, false),
        stream("https://x/dash-english.mpd", "900", "English dub", false, 1080, true),
        stream("https://x/english-360.mp4", "900", "English dub", false, 360, false),
    )

    @Test
    fun `audio variants keep the original first and drop duplicates`() {
        val variants = PlayerStreamVariants.audioVariants(streams)
        assertEquals(listOf("500", "900"), variants.map { it.id })
        assertEquals("Original Audio", variants.first().label)
        assertTrue(variants.first().original)
    }

    @Test
    fun `quality choices list real resolutions with no automatic entry`() {
        val choices = PlayerStreamVariants.qualityChoices(streams, "900")
        assertEquals(listOf("1080p", "360p"), choices.map { it.label })
        assertTrue(choices.none { it.automatic })
        assertEquals("https://x/dash-english.mpd", choices.first().stream.url)
        assertEquals(360, choices.last().height)
    }

    @Test
    fun `selecting a dub at a missing height falls back to the closest resource`() {
        val target = PlayerStreamVariants.streamFor(streams, "900", 720)
        assertEquals("https://x/dash-english.mpd", target?.url)
    }

    @Test
    fun `automatic keeps the adaptive manifest`() {
        val target = PlayerStreamVariants.streamFor(streams, "500", 0)
        assertEquals("https://x/dash-original.mpd", target?.url)
        assertTrue(PlayerStreamVariants.activeIsAutomatic(target))
    }

    @Test
    fun `streams without audio hints expose no variants`() {
        val plain = listOf(
            StreamItem(url = "https://x/a.mp4", addonName = "Other", addonId = "addon:other"),
        )
        assertTrue(PlayerStreamVariants.audioVariants(plain).isEmpty())
        assertNull(PlayerStreamVariants.activeAudioId(plain.first()))
    }
}
