package com.nuvio.app.features.downloads

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plain JVM tests (no Robolectric): the resume decision and the plan cache are
 * pure functions over files.
 */
class DashTrackResumeTest {
    private val track = DashPlanTrack(
        id = "video",
        init = "init-stream0.m4s",
        segments = listOf("chunk-stream0-00001.m4s", "chunk-stream0-00002.m4s"),
        count = 2,
    )
    private val fingerprint = dashTrackFingerprint(track)

    @Test
    fun firstAttemptStartsAtTheBeginning() {
        val resume = resolveDashTrackResume(null, fingerprint, 3, 0L)

        assertEquals(0, resume.nextIndex)
        assertEquals(0L, resume.bytes)
        assertEquals(fingerprint, resume.fingerprint)
    }

    @Test
    fun savedCheckpointContinuesWhereItStopped() {
        val saved = DashTrackProgress(fingerprint, nextIndex = 2, bytes = 4_096L)

        val resume = resolveDashTrackResume(saved, fingerprint, 3, fileLength = 4_096L)

        assertEquals(2, resume.nextIndex)
        assertEquals(4_096L, resume.bytes)
    }

    @Test
    fun aDifferentSegmentListRestartsTheTrack() {
        val saved = DashTrackProgress("other|plan|3|a|b", nextIndex = 2, bytes = 4_096L)

        val resume = resolveDashTrackResume(saved, fingerprint, 3, fileLength = 4_096L)

        assertEquals(0, resume.nextIndex)
        assertEquals(0L, resume.bytes)
    }

    @Test
    fun aFileShorterThanTheCheckpointRestartsInsteadOfJoiningGarbage() {
        val saved = DashTrackProgress(fingerprint, nextIndex = 2, bytes = 4_096L)

        val resume = resolveDashTrackResume(saved, fingerprint, 3, fileLength = 1_024L)

        assertEquals(0, resume.nextIndex)
        assertEquals(0L, resume.bytes)
    }

    @Test
    fun aCheckpointBeyondThePlanRestartsTheTrack() {
        val saved = DashTrackProgress(fingerprint, nextIndex = 9, bytes = 4_096L)

        val resume = resolveDashTrackResume(saved, fingerprint, 3, fileLength = 4_096L)

        assertEquals(0, resume.nextIndex)
    }

    @Test
    fun namesPutTheInitSegmentFirst() {
        assertEquals(
            listOf("init-stream0.m4s", "chunk-stream0-00001.m4s", "chunk-stream0-00002.m4s"),
            dashTrackNames(track),
        )
    }

    @Test
    fun checkpointSurvivesAProcessRestart() {
        val directory = Files.createTempDirectory("dash-progress").toFile()
        val file = File(directory, "track_video.progress.json")
        writeDashTrackProgress(file, DashTrackProgress(fingerprint, nextIndex = 7, bytes = 123_456L))

        val restored = readDashTrackProgress(file)

        assertEquals(DashTrackProgress(fingerprint, 7, 123_456L), restored)
        assertTrue(file.isFile)
    }

    @Test
    fun planCacheIsReusedOnlyForTheSameUrl() {
        val directory = Files.createTempDirectory("dash-plan").toFile()
        val plan = DashDownloadPlan(
            target = "5409343034641236440/1/1/480",
            baseUrl = "https://cdn.example/dash/",
            cookie = "Policy=abc",
            totalBytes = 426_670_164L,
            video = track,
        )

        saveCachedDashPlan(directory, "https://bridge/dl/plan/a.json", plan)

        assertEquals(plan, loadCachedDashPlan(directory, "https://bridge/dl/plan/a.json"))
        assertNull(loadCachedDashPlan(directory, "https://bridge/dl/plan/b.json"))
    }

    @Test
    fun aMissingPlanCacheIsNotAnError() {
        val directory = Files.createTempDirectory("dash-plan-empty").toFile()

        assertNull(loadCachedDashPlan(directory, "https://bridge/dl/plan/a.json"))
    }
}
