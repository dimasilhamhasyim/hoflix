package com.nuvio.app.features.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.hoflix_download_button
import nuvio.composeapp.generated.resources.hoflix_download_preparing
import nuvio.composeapp.generated.resources.hoflix_download_select_all
import nuvio.composeapp.generated.resources.hoflix_download_source
import nuvio.composeapp.generated.resources.hoflix_download_source_by
import nuvio.composeapp.generated.resources.hoflix_download_title
import org.jetbrains.compose.resources.stringResource

/**
 * MovieBox's download sheet, reproduced: a source block, the audio and season
 * dropdowns side by side, one row of quality segments, an episode list with its
 * own select-all control, and the download button in the bottom bar.
 *
 * Every resolution here comes from what the addon advertised, so a title that
 * MovieBox only ships as DASH can still be downloaded at 1080p — the bridge
 * remuxes that stream into one MP4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualitySheetHost() {
    val pickerState by DownloadQualityPicker.state.collectAsStateWithLifecycle()
    val current = pickerState ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    NuvioModalBottomSheet(
        onDismissRequest = { DownloadQualityPicker.dismiss() },
        sheetState = sheetState,
    ) {
        when (current) {
            is DownloadPickerUiState.Loading -> LoadingContent(current)
            is DownloadPickerUiState.Ready -> ReadyContent(current)
        }
    }
}

@Composable
private fun SheetHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .clickable { DownloadQualityPicker.dismiss() },
        )
    }
}

@Composable
private fun LoadingContent(state: DownloadPickerUiState.Loading) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(stringResource(Res.string.hoflix_download_title))
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = state.request.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(Res.string.hoflix_download_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReadyContent(state: DownloadPickerUiState.Ready) {
    val selectedCount = if (state.isSeries) state.selectedEpisodes.size else 1
    val canDownload = state.selectedHeight != null && selectedCount > 0
    val totalLabel = state.totalSizeBytes()?.let(::formatDownloadBytes)

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(stringResource(Res.string.hoflix_download_title))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(Res.string.hoflix_download_source),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(Res.string.hoflix_download_source_by),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(state.request.title)
                    val season = state.selectedSeason
                    val episode = state.request.episodeNumber
                    if (state.isSeries && season != null) append(" • Musim $season")
                    if (state.isSeries && episode != null && episode > 0) {
                        append(" • Episode $episode")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))

            val showAudio = state.audios.size > 1
            val showSeason = state.isSeries && state.seasons.size > 1
            if (showAudio || showSeason) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showAudio) {
                        DropdownBox(
                            modifier = Modifier.weight(1f),
                            value = state.audios
                                .firstOrNull { it.id == state.selectedAudioId }?.label
                                ?: state.audios.first().label,
                            options = state.audios.map {
                                DownloadSelectorOption(it.id ?: "", it.label)
                            },
                            onSelect = { id -> DownloadQualityPicker.selectAudio(id.ifBlank { null }) },
                        )
                    }
                    if (showSeason) {
                        DropdownBox(
                            modifier = Modifier.weight(1f),
                            value = "Musim ${state.selectedSeason ?: state.seasons.first()}",
                            options = state.seasons.map {
                                DownloadSelectorOption(it.toString(), "Musim $it")
                            },
                            onSelect = { value ->
                                DownloadQualityPicker.selectSeason(value.toIntOrNull())
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.qualities.forEach { quality ->
                    QualitySegment(
                        modifier = Modifier.weight(1f),
                        label = "${quality.height}P",
                        selected = quality.height == state.selectedHeight,
                        onClick = { DownloadQualityPicker.selectHeight(quality.height) },
                    )
                }
            }
            totalLabel?.let { size ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = size,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.isSeries && state.episodes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val allSelected = state.episodes.isNotEmpty() &&
                state.selectedEpisodes.containsAll(state.episodes.map { it.episode })
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(horizontal = 12.dp),
            ) {
                items(state.episodes, key = { it.episode }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        checked = episode.episode in state.selectedEpisodes,
                        sizeLabel = state.episodeSizeBytes(episode.episode)
                            ?.let(::formatDownloadBytes),
                        onToggle = { DownloadQualityPicker.toggleEpisode(episode.episode) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = allSelected,
                    onClick = { DownloadQualityPicker.toggleAllEpisodes() },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = stringResource(Res.string.hoflix_download_select_all),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        DownloadQualityPicker.toggleAllEpisodes()
                    },
                )
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { DownloadQualityPicker.download() },
                enabled = canDownload,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .widthIn(min = 140.dp)
                    .height(44.dp),
            ) {
                Text(
                    text = buildString {
                        append(stringResource(Res.string.hoflix_download_button))
                        if (state.isSeries) append(" ($selectedCount)")
                        totalLabel?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: DownloadEpisodeOption,
    checked: Boolean,
    sizeLabel: String? = null,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = checked,
            onClick = { onToggle() },
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            val prefix = buildString {
                if (episode.season != null && episode.season > 0) {
                    append("S${episode.season.toString().padStart(2, '0')} ")
                }
                append("E${episode.episode.toString().padStart(2, '0')}")
            }
            val title = episode.title.trim()
            Text(
                text = if (title.isBlank() || title.startsWith("Episode ", ignoreCase = true)) {
                    prefix
                } else {
                    "$prefix · $title"
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                formatDownloadDuration(episode.runtimeMinutes),
                sizeLabel,
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QualitySegment(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .background(container, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private data class DownloadSelectorOption(val id: String, val label: String)

@Composable
private fun DropdownBox(
    modifier: Modifier = Modifier,
    value: String,
    options: List<DownloadSelectorOption>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option.id)
                    },
                )
            }
        }
    }
}
