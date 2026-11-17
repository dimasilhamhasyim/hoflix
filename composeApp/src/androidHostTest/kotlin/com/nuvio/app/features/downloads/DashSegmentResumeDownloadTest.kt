package com.nuvio.app.features.downloads

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the loop fix end to end: a track whose connection dies halfway keeps
 * the segments it already joined and continues from there, instead of pulling
 * the whole episode from the start again. The ledger is checked too, so the
 * reported byte count matches the file and never counts a segment twice.
 */
class DashSegmentResumeDownloadTest {
    private val segments = (1..4).associateWith { "chunk-stream0-0000$it.m4s" }
    private val initName = "init-stream0.m4s"
    private val initBody = "init;".repeat(50)
    private fun body(name: String) = if (name == initName) initBody else "payload-$name;".repeat(200)

    @Test
    fun anInterruptedTrackContinuesFromTheLastJoinedSegment() = runBlocking {
        val directory = Files.createTempDirectory("dash-resume").toFile()
        val server = MockWebServer()
        val failOnce = AtomicBoolean(true)
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val names = listOf(initName) + segments.values
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val name = request.path.orEmpty().trimStart('/')
                requests.add(name)
                // The third media segment dies once, the way a dropped radio
                // connection does mid-episode.
                if (name == segments[3] && failOnce.compareAndSet(true, false)) {
                    return MockResponse().setResponseCode(400)
                }
                if (name !in names) return MockResponse().setResponseCode(404)
                return MockResponse().setBody(body(name))
            }
        }
        server.start()
        val client = OkHttpClient()
        val plan = DashDownloadPlan(
            target = "1/1/1/480",
            baseUrl = server.url("/").toString(),
            cookie = "",
            video = DashPlanTrack(id = "video", init = initName, segments = segments.values.toList()),
        )
        try {
            val firstLedger = DashByteLedger(dashSeededBytes(directory, plan))
            val firstAttempt = runCatching {
                downloadDashTrack(
                    client, plan, plan.video, directory,
                    concurrency = 1, ledger = firstLedger, onChanged = { },
                )
            }
            assertTrue(firstAttempt.isFailure, "the interrupted attempt has to report a failure")

            val checkpoint = assertNotNull(
                readDashTrackProgress(File(directory, "track_video.progress.json")),
                "progress has to survive the failure",
            )
            assertEquals(3, checkpoint.nextIndex, "init plus the two joined segments are kept")
            assertEquals(checkpoint.bytes, File(directory, "track_video.mp4").length())
            assertTrue(checkpoint.bytes > 0L)

            requests.clear()
            // A resumed transfer seeds from the checkpoint, so its counter opens
            // at the bytes already on disk instead of zero.
            val resumedLedger = DashByteLedger(dashSeededBytes(directory, plan))
            assertEquals(checkpoint.bytes, resumedLedger.total(), "the resume point is counted at once")

            val resumedFile = downloadDashTrack(
                client, plan, plan.video, directory,
                concurrency = 1, ledger = resumedLedger, onChanged = { },
            )

            assertEquals(
                listOf(segments[3], segments[4]),
                requests.toList(),
                "a resume must not re-fetch segments it already joined",
            )
            val expectedBytes = names.sumOf { body(it).toByteArray().size.toLong() }
            assertEquals(expectedBytes, resumedFile.length())
            assertEquals(
                expectedBytes,
                resumedLedger.total(),
                "the ledger has to end on the real file size, with nothing counted twice",
            )
        } finally {
            server.shutdown()
        }
    }
}
