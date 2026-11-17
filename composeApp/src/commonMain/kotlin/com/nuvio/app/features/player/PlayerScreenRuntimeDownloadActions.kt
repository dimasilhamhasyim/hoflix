package com.nuvio.app.features.player

import com.nuvio.app.features.downloads.DownloadEpisodeOption
import com.nuvio.app.features.downloads.DownloadQualityPicker
import com.nuvio.app.features.downloads.DownloadRequestInfo

/**
 * "Unduh" in the player. The quality sheet is offered for the audio track that
 * is currently playing, so downloading a dub keeps the dub the user hears.
 */
internal fun PlayerScreenRuntime.downloadActiveStream() {
    val videoId = activeVideoId?.takeIf { it.isNotBlank() } ?: return
    DownloadQualityPicker.request(
        request = DownloadRequestInfo(
            contentType = contentType ?: parentMetaType,
            videoId = videoId,
            parentMetaId = parentMetaId,
            parentMetaType = parentMetaType,
            title = title,
            logo = metaUiState.meta?.logo,
            poster = metaUiState.meta?.poster,
            background = metaUiState.meta?.background,
            seasonNumber = activeSeasonNumber,
            episodeNumber = activeEpisodeNumber,
            episodeTitle = activeEpisodeTitle,
            episodeThumbnail = activeEpisodeThumbnail,
            episodes = metaUiState.meta?.videos.orEmpty().mapNotNull { video ->
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
        streams = PlayerStreamsRepository.sourceState.value.groups
            .flatMap { group -> group.streams }
            .ifEmpty { listOfNotNull(activeStream) },
        audioId = activeStream?.behaviorHints?.audioId,
    )
}
