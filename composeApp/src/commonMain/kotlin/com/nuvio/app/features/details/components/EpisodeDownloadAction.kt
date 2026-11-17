package com.nuvio.app.features.details.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.downloads.DownloadStatus
import com.nuvio.app.features.downloads.DownloadsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.downloads_download_action
import org.jetbrains.compose.resources.stringResource

/**
 * Per-episode download control, mirroring MovieBox's row action.
 *
 * The icon follows the download it belongs to: cloud → progress ring → check,
 * so a tap that started a transfer is visibly acknowledged even after the sheet
 * closed.
 */
@Composable
internal fun EpisodeDownloadAction(
    parentMetaId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 20.dp,
    showBackground: Boolean = true,
) {
    val downloadsState by DownloadsRepository.uiState.collectAsStateWithLifecycle()
    val logicalKey = if (seasonNumber != null && episodeNumber != null) {
        "${parentMetaId.trim()}|$seasonNumber|$episodeNumber"
    } else {
        "${parentMetaId.trim()}|movie"
    }
    val item = downloadsState.items.firstOrNull { it.logicalContentKey == logicalKey }

    Surface(
        shape = CircleShape,
        color = if (showBackground) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        modifier = modifier.size(buttonSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (item?.status) {
                DownloadStatus.Downloading, DownloadStatus.Paused -> {
                    CircularProgressIndicator(
                        progress = { item.progressFraction },
                        modifier = Modifier.size(iconSize),
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                }

                DownloadStatus.Completed -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(iconSize),
                    )
                }

                DownloadStatus.Failed -> {
                    IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = stringResource(Res.string.downloads_download_action),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }

                null -> {
                    IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = stringResource(Res.string.downloads_download_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }
        }
    }
}
