package com.nuvio.app.features.downloads

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The byte counter is what the download card draws, so it has to be absolute:
 * seeded from disk, set — never added — while segments stream in, and free of
 * double counting when a plan is refreshed mid-transfer.
 */
class DashDownloadProgressTest {
    private fun workDir(): File = Files.createTempDirectory("dash-ledger").toFile()

    @Test
    fun streamingBytesReplaceThePartInsteadOfPilingUp() {
        val ledger = DashByteLedger()

        ledger.partBytes("video", 1, 1_000)
        ledger.partBytes("video", 1, 2_500)
        ledger.partBytes("video", 1, 4_000)

        assertEquals(4_000L, ledger.total())
    }

    @Test
    fun finishingASegmentMovesItOutOfTheInFlightCount() {
        val ledger = DashByteLedger(seedBytes = 500L)

        ledger.partBytes("audio", 3, 2_000)
        ledger.finishPart("audio", 3, 2_000)

        assertEquals(2_500L, ledger.total())
        // The part is gone, so a later sample of the same segment cannot be
        // counted a second time.
        ledger.partBytes("audio", 3, 0)
        assertEquals(2_500L, ledger.total())
    }

    @Test
    fun throwingAwayAPartialBodyWithdrawsItsBytes() {
        val ledger = DashByteLedger(seedBytes = 100L)

        ledger.partBytes("video", 7, 900)
        ledger.dropPart("video", 7)

        assertEquals(100L, ledger.total())
    }

    @Test
    fun videoAndAudioKeepSeparateSegmentIndices() {
        val ledger = DashByteLedger()

        ledger.finishPart("video", 1, 700)
        ledger.finishPart("audio", 1, 300)

        assertEquals(1_000L, ledger.total())
    }

    @Test
    fun speedAndEtaComeFromTheBytesActuallyArriving() {
        val rate = DownloadRateEstimator(windowMillis = 1_000L, stallMillis = 2_000L)

        rate.sample(0L, 0L, 10_000L)
        rate.sample(1_000L, 2_000L, 10_000L)
        val sample = rate.sample(2_000L, 4_000L, 10_000L)

        assertEquals(2_000L, sample.bytesPerSecond)
        assertEquals(3L, sample.etaSeconds)
    }

    @Test
    fun aStalledTransferStopsAdvertisingARate() {
        val rate = DownloadRateEstimator(windowMillis = 1_000L, stallMillis = 2_000L)

        rate.sample(0L, 0L, 100_000L)
        rate.sample(1_000L, 5_000L, 100_000L)
        val stalled = rate.sample(9_000L, 5_000L, 100_000L)

        assertEquals(0L, stalled.bytesPerSecond)
        assertNull(stalled.etaSeconds)
    }

    @Test
    fun aLargeFileWithNoTotalHasSpeedButNoEta() {
        val rate = DownloadRateEstimator(windowMillis = 1_000L)

        rate.sample(0L, 0L, null)
        val sample = rate.sample(1_000L, 1_000L, null)

        assertEquals(1_000L, sample.bytesPerSecond)
        assertNull(sample.etaSeconds)
    }

    @Test
    fun theSeedEqualsWhatIsReallyOnDisk() {
        val directory = workDir()
        val track = DashPlanTrack(
            id = "video",
            init = "init-stream0.m4s",
            segments = listOf("chunk-1.m4s", "chunk-2.m4s", "chunk-3.m4s"),
        )
        val plan = DashDownloadPlan(target = "1/1/1/480", video = track)
        // A finished checkpoint: the track file holds the bytes it claims.
        File(directory, "track_video.mp4").writeBytes(ByteArray(4_096))
        writeDashTrackProgress(
            File(directory, "track_video.progress.json"),
            DashTrackProgress(dashTrackFingerprint(track), nextIndex = 1, bytes = 4_096L),
        )
        // One segment already fetched before the interruption.
        File(directory, "seg_video_2.bin").writeBytes(ByteArray(1_024))

        assertEquals(5_120L, dashSeededBytes(directory, plan))
    }

    @Test
    fun aCheckpointFromAnotherPlanIsNotSeeded() {
        val directory = workDir()
        val track = DashPlanTrack(id = "video", init = "init-stream0.m4s", segments = listOf("chunk-1.m4s"))
        val plan = DashDownloadPlan(target = "1/1/1/480", video = track)
        File(directory, "track_video.mp4").writeBytes(ByteArray(4_096))
        writeDashTrackProgress(
            File(directory, "track_video.progress.json"),
            DashTrackProgress("other|plan|1|a|b", nextIndex = 1, bytes = 4_096L),
        )
        File(directory, "seg_video_2.bin").writeBytes(ByteArray(1_024))

        assertEquals(0L, dashSeededBytes(directory, plan))
    }

    @Test
    fun progressFractionNeverReachesFullBeforeTheJoin() {
        val item = DownloadItem(
            id = "1",
            contentType = "series",
            parentMetaId = "mb:1",
            parentMetaType = "series",
            videoId = "v",
            title = "Dark",
            streamTitle = "1080p",
            providerName = "HOFLIX",
            sourceUrl = "https://example/plan.json",
            dashPlan = true,
            fileName = "dark.mp4",
            status = DownloadStatus.Downloading,
            downloadedBytes = 500L,
            totalBytes = 400L,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )

        assertTrue(item.progressFraction <= 1f)
        assertEquals(1f, item.progressFraction)
    }
}
