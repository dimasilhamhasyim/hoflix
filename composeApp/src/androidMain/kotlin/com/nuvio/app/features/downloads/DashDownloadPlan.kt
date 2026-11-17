package com.nuvio.app.features.downloads

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The bridge's DASH download plan: the exact segment names for one episode at
 * one resolution, plus the CDN base URL and the signed cookie.
 *
 * MovieBox downloads by pulling the DASH segments onto the device and joining
 * them there. This plan makes that possible without the bridge relaying a
 * single byte of video.
 */
@Serializable
internal data class DashPlanTrack(
    val id: String = "",
    val init: String = "",
    val segments: List<String> = emptyList(),
    val segmentSeconds: Double = 0.0,
    val count: Int = 0,
    val height: Int = 0,
    val bandwidth: Long = 0L,
    val codecs: String = "",
    val mimeType: String = "",
)

@Serializable
internal data class DashDownloadPlan(
    val target: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val baseUrl: String = "",
    val cookie: String = "",
    val mpdUrl: String = "",
    val height: Int = 0,
    val durationSeconds: Double = 0.0,
    val totalBytes: Long = 0L,
    val video: DashPlanTrack = DashPlanTrack(),
    val audio: DashPlanTrack? = null,
    /**
     * `dash` joins two fragmented-MP4 tracks. `hls` is one transport stream:
     * the segments are concatenated as-is and then remuxed to MP4.
     */
    val kind: String = "dash",
    /** Header the CDN requires for its segments; blank keeps the app default. */
    val userAgent: String = "",
) {
    /** Every file the download has to fetch, init segments included. */
    val fileCount: Int
        get() = (1 + video.segments.size) +
            (audio?.let { 1 + it.segments.size } ?: 0)

    fun urlOf(name: String): String = baseUrl + name

    fun fingerprint(): String = listOf(target, season, episode, height).joinToString("_")
}

internal class DashHttpException(
    val statusCode: Int,
    detail: String? = null,
) : java.io.IOException(if (detail.isNullOrBlank()) "HTTP $statusCode" else "HTTP $statusCode ($detail)")

/** `refresh` asks the bridge for a new cookie, for an expired CDN signature. */
internal fun fetchDashPlan(
    client: OkHttpClient,
    planUrl: String,
    refresh: Boolean = false,
): DashDownloadPlan {
    val url = if (!refresh) planUrl else {
        planUrl + if (planUrl.contains('?')) "&refresh=1" else "?refresh=1"
    }
    val request = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            // 409 dari bridge = MovieBox hanya punya manifest `/preview/`
            // (cuplikan ±15 menit). Mengunduhnya menghasilkan berkas pendek yang
            // terlihat seperti film utuh, jadi ditolak dengan alasan jelas.
            val detail = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
            if (response.code == 409 && detail.contains("preview_only")) {
                throw java.io.IOException("Sumber ini hanya menyediakan cuplikan, unduhan penuh tidak tersedia")
            }
            throw DashHttpException(response.code, "rencana")
        }
        val body = response.body?.string() ?: throw java.io.IOException("rencana kosong")
        return dashPlanJson.decodeFromString(DashDownloadPlan.serializer(), body)
    }
}

/**
 * A plan is reused for as long as its URL matches, so a restarted transfer does
 * not hit the bridge again before it has fetched a single segment. The cookie
 * inside it still expires; a 401/403 from the CDN replaces the cache.
 */
@Serializable
internal data class CachedDashPlan(
    val url: String = "",
    val plan: DashDownloadPlan = DashDownloadPlan(),
)

internal fun loadCachedDashPlan(workDir: File, planUrl: String): DashDownloadPlan? = runCatching {
    val cached = dashPlanJson.decodeFromString(
        CachedDashPlan.serializer(),
        File(workDir, "plan.json").readText(),
    )
    cached.plan.takeIf { cached.url == planUrl }
}.getOrNull()

internal fun saveCachedDashPlan(workDir: File, planUrl: String, plan: DashDownloadPlan) {
    runCatching {
        File(workDir, "plan.json")
            .writeText(dashPlanJson.encodeToString(CachedDashPlan.serializer(), CachedDashPlan(planUrl, plan)))
    }
}

internal val dashPlanJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
}
