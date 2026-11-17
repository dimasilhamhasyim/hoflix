package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The DASH download is only as reliable as the plan it starts from: every
 * segment name, the audio track and the byte estimate have to line up with what
 * the bridge advertised before a single byte is fetched.
 */
class DashDownloadPlanTest {
    private val payload = """
        {
          "target": "5409343034641236440",
          "season": 1,
          "episode": 1,
          "baseUrl": "https://cdn.example/dash/title/",
          "cookie": "CloudFront-Policy=abc",
          "mpdUrl": "https://cdn.example/dash/title/index_web.mpd",
          "height": 1080,
          "durationSeconds": 3090.3,
          "totalBytes": 426510458,
          "video": {
            "id": "0",
            "init": "init-stream0.m4s",
            "segments": ["chunk-stream0-00001.m4s", "chunk-stream0-00002.m4s"],
            "segmentSeconds": 5.0,
            "count": 2,
            "height": 1080,
            "bandwidth": 976127,
            "codecs": "hev1.1.6.L150.90",
            "mimeType": "video/mp4"
          },
          "audio": {
            "id": "3",
            "init": "init-stream3.m4s",
            "segments": ["chunk-stream3-00001.m4s"],
            "segmentSeconds": 5.0,
            "count": 1,
            "bandwidth": 128000,
            "codecs": "mp4a.40.2",
            "mimeType": "audio/mp4"
          }
        }
    """.trimIndent()

    @Test
    fun parsesPlanAndBuildsAbsoluteSegmentUrls() {
        val plan = dashPlanJson.decodeFromString(DashDownloadPlan.serializer(), payload)

        assertEquals("5409343034641236440", plan.target)
        assertEquals(1080, plan.height)
        assertEquals(426510458L, plan.totalBytes)
        assertEquals(
            "https://cdn.example/dash/title/chunk-stream0-00002.m4s",
            plan.urlOf(plan.video.segments[1]),
        )
        assertEquals("https://cdn.example/dash/title/init-stream3.m4s", plan.urlOf(plan.audio!!.init))
    }

    @Test
    fun countsEveryFileIncludingInitSegments() {
        val plan = dashPlanJson.decodeFromString(DashDownloadPlan.serializer(), payload)

        assertEquals(5, plan.fileCount)
        assertEquals("5409343034641236440_1_1_1080", plan.fingerprint())
    }

    @Test
    fun missingAudioTrackDoesNotBreakThePlan() {
        val plan = dashJsonWithoutAudio()

        assertEquals(null, plan.audio)
        assertEquals(3, plan.fileCount)
    }

    @Test
    fun sharedFileNameCarriesShowEpisodeAndQuality() {
        val item = DownloadItem(
            id = "1",
            contentType = "series",
            parentMetaId = "mb:1",
            parentMetaType = "series",
            videoId = "mb:1:1:1",
            title = "Dark",
            streamTitle = "1080p • HEVC • 759 MB • DASH",
            providerName = "HOFLIX",
            sourceUrl = "https://bridge/dl/plan/1/1/1/1080.json",
            dashPlan = true,
            fileName = "Dark_S01E01_1.mp4",
            status = DownloadStatus.Completed,
            seasonNumber = 1,
            episodeNumber = 1,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )

        val name = downloadDisplayName(item, item.fileName)

        assertEquals("HOFLIX - Dark - S01E01 - 1080p.mp4", name)
    }

    @Test
    fun planUrlIsDetectedSoThePhoneDownloadsTheSegments() {
        assertTrue("https://hoflix-api.siho.my.id/dl/plan/1/1/1/1080.json".isDashPlanUrl())
        assertTrue("https://hoflix-api.siho.my.id/dl/plan/1/1/1/1080.json?refresh=1".isDashPlanUrl())
        assertTrue(
            !"https://hoflix-api.siho.my.id/dl/1/1/1/1080.mp4".isDashPlanUrl(),
        )
    }

    private fun dashJsonWithoutAudio(): DashDownloadPlan =
        dashPlanJson.decodeFromString(
            DashDownloadPlan.serializer(),
            """
            {
              "baseUrl": "https://cdn.example/dash/title/",
              "video": {
                "id": "1",
                "init": "init-stream1.m4s",
                "segments": ["a.m4s", "b.m4s"],
                "count": 2
              }
            }
            """.trimIndent(),
        )
}
