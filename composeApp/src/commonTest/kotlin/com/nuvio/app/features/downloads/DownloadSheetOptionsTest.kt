package com.nuvio.app.features.downloads

import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamDownloadVariant
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadSheetOptionsTest {
    private fun stream(
        url: String = "https://bridge.example/dash/x/1/1/manifest.mpd",
        height: Int? = null,
        audioId: String? = null,
        audioLabel: String? = null,
        original: Boolean = false,
        variants: List<StreamDownloadVariant> = emptyList(),
        template: String? = null,
    ) = StreamItem(
        name = "HOFLIX",
        title = "stream",
        url = url,
        addonName = "HOFLIX",
        addonId = "org.hoflix.moviebox",
        behaviorHints = StreamBehaviorHints(
            qualityHeight = height,
            audioId = audioId,
            audioLabel = audioLabel,
            audioOriginal = original,
            downloadVariants = variants,
            downloadTemplate = template,
        ),
    )

    @Test
    fun advertisedDashHeightsBecomeDownloadChoices() {
        val template = "https://bridge.example/dl/orig/{season}/{episode}/{height}.mp4"
        val choices = buildDownloadChoices(
            listOf(
                stream(
                    variants = listOf(
                        StreamDownloadVariant(480, 118_000_000),
                        StreamDownloadVariant(1080, 377_000_000),
                        StreamDownloadVariant(720, 213_000_000),
                    ),
                    template = template,
                ),
            ),
            audioId = "orig",
        )
        assertEquals(listOf(1080, 720, 480), choices.map { it.height })
        assertEquals(377_000_000L, choices.first().sizeBytes)
        assertEquals(template, choices.first().template)
    }

    @Test
    fun fallsBackToProgressiveMp4WhenNothingIsAdvertised() {
        val choices = buildDownloadChoices(
            listOf(
                stream(url = "https://cdn.example/manifest.mpd", height = 1080),
                stream(url = "https://cdn.example/480.mp4", height = 480),
            ),
            audioId = null,
        )
        assertEquals(listOf(480), choices.map { it.height })
        assertEquals("https://cdn.example/480.mp4", choices.single().url)
        assertNull(choices.single().template)
    }

    @Test
    fun sameHeightUsesTheBiggerReportedSize() {
        val choices = buildDownloadChoices(
            listOf(
                stream(variants = listOf(StreamDownloadVariant(1080, 300_000_000))),
                stream(variants = listOf(StreamDownloadVariant(1080, 377_000_000))),
            ),
            audioId = null,
        )
        assertEquals(377_000_000L, choices.single().sizeBytes)
    }

    @Test
    fun templateResolvesOtherEpisodes() {
        val url = resolveDownloadUrl(
            template = "https://bridge.example/dl/orig/{season}/{episode}/{height}.mp4",
            fallbackUrl = "https://bridge.example/dl/orig/2/1/1080.mp4",
            season = 2,
            episode = 7,
            height = 1080,
            isCurrentEpisode = false,
        )
        assertEquals("https://bridge.example/dl/orig/2/7/1080.mp4", url)
    }

    @Test
    fun withoutTemplateOnlyTheResolvedEpisodeIsDownloadable() {
        assertNull(
            resolveDownloadUrl(
                template = null,
                fallbackUrl = "https://cdn.example/ep1.mp4",
                season = 1,
                episode = 4,
                height = 480,
                isCurrentEpisode = false,
            ),
        )
        assertEquals(
            "https://cdn.example/ep1.mp4",
            resolveDownloadUrl(
                template = null,
                fallbackUrl = "https://cdn.example/ep1.mp4",
                season = 1,
                episode = 1,
                height = 480,
                isCurrentEpisode = true,
            ),
        )
    }

    @Test
    fun audiosKeepOriginalFirstAndDropDuplicates() {
        val audios = buildDownloadAudios(
            listOf(
                stream(audioId = "dub-en", audioLabel = "English dub"),
                stream(audioId = "orig", audioLabel = "Original Audio", original = true),
                stream(audioId = "dub-en", audioLabel = "English dub"),
            ),
        )
        assertEquals(listOf("orig", "dub-en"), audios.map { it.id })
        assertEquals("Original Audio", audios.first().label)
    }

    @Test
    fun episodeDurationIsHumanReadable() {
        assertEquals("45m", formatDownloadDuration(45))
        assertEquals("1j 5m", formatDownloadDuration(65))
        assertEquals("2j", formatDownloadDuration(120))
        assertNull(formatDownloadDuration(null))
        assertNull(formatDownloadDuration(0))
    }

    private val seriesRequest = DownloadRequestInfo(
        contentType = "series",
        videoId = "v",
        parentMetaId = "p",
        parentMetaType = "series",
        title = "Dark",
    )

    @Test
    fun seasonTotalFollowsTheCheckedEpisodes() {
        val state = DownloadPickerUiState.Ready(
            request = seriesRequest,
            isSeries = true,
            selectedHeight = 1080,
            qualities = listOf(DownloadQualityChoice(1080, 426_700_000L)),
            selectedEpisodes = setOf(1, 2),
            sizesByEpisode = mapOf(
                1 to mapOf(1080 to 426_700_000L),
                2 to mapOf(1080 to 295_400_000L),
            ),
        )
        assertEquals(426_700_000L, state.episodeSizeBytes(1))
        assertEquals(295_400_000L, state.episodeSizeBytes(2))
        assertEquals(722_100_000L, state.totalSizeBytes())
    }

    @Test
    fun uncheckedEpisodesDoNotChangeTheTotal() {
        val state = DownloadPickerUiState.Ready(
            request = seriesRequest,
            isSeries = true,
            selectedHeight = 720,
            qualities = listOf(DownloadQualityChoice(720, 262_800_000L)),
            selectedEpisodes = setOf(3),
            sizesByEpisode = mapOf(
                1 to mapOf(720 to 262_800_000L),
                3 to mapOf(720 to 178_800_000L),
            ),
        )
        assertEquals(178_800_000L, state.totalSizeBytes())
    }

    @Test
    fun missingSizeTableFallsBackToTheQualitySize() {
        val state = DownloadPickerUiState.Ready(
            request = seriesRequest,
            isSeries = true,
            selectedHeight = 480,
            qualities = listOf(DownloadQualityChoice(480, 167_500_000L)),
            selectedEpisodes = setOf(1, 2),
        )
        assertEquals(167_500_000L, state.episodeSizeBytes(1))
        assertEquals(335_000_000L, state.totalSizeBytes())
    }
}
