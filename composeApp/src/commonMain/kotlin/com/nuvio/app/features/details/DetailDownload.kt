package com.nuvio.app.features.details

import com.nuvio.app.features.downloads.DownloadEpisodeOption
import com.nuvio.app.features.downloads.DownloadQualityPicker
import com.nuvio.app.features.downloads.DownloadRequestInfo

data class DetailDownloadRequest(
    val videoId: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
)

/**
 * "Unduh" on the detail page and on every episode row.
 *
 * Opens the quality sheet instead of silently taking the largest file: MovieBox
 * lets the user pick a resolution, and the sheet also shows the real size the
 * bridge reports for that stream.
 */
object DetailDownload {
    fun request(
        meta: MetaDetails,
        type: String,
        videoId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        episodeTitle: String? = null,
        episodeThumbnail: String? = null,
    ) {
        DownloadQualityPicker.request(
            DownloadRequestInfo(
                contentType = type,
                videoId = videoId,
                parentMetaId = meta.id,
                parentMetaType = meta.type,
                title = meta.name,
                logo = meta.logo,
                poster = meta.poster,
                background = meta.background,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                episodeThumbnail = episodeThumbnail,
                // The sheet offers the whole season, so it needs every episode
                // the detail page already knows about.
                episodes = meta.videos.mapNotNull { video ->
                    val number = video.episode ?: return@mapNotNull null
                    if (number <= 0) return@mapNotNull null
                    DownloadEpisodeOption(
                        videoId = video.id,
                        season = video.season,
                        episode = number,
                        title = video.title,
                        runtimeMinutes = video.runtime,
                        thumbnail = video.thumbnail,
                    )
                },
            ),
        )
    }
}
