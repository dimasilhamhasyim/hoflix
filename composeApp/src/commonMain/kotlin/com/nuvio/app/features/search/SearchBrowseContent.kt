package com.nuvio.app.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.PosterGridRow
import com.nuvio.app.features.home.components.PosterGridSkeletonRow

/**
 * Tab Film / Series sebagai segmented control: tanpa chip "Semua", karena
 * katalog HOFLIX memang selalu salah satu dari dua medium itu.
 */
internal fun LazyListScope.browseTabSegments(
    state: BrowseUiState,
    onTabSelected: (String) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    if (!state.isAvailable || state.tabs.isEmpty()) return
    item(key = "browse_tabs") {
        BrowseTabSegments(
            state = state,
            onTabSelected = onTabSelected,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
        )
    }
}

@Composable
private fun BrowseTabSegments(
    state: BrowseUiState,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        state.tabs.forEach { tab ->
            val selected = state.selectedTab == tab.id
            Text(
                text = tab.label,
                modifier = Modifier
                    .weight(1f)
                    .clip(tokens.shapes.compactCard)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onTabSelected(tab.id) }
                    .padding(vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Satu baris kendali pencarian: Urutan, Genre, Negara, Tahun.
 *
 * Tiap kategori punya chip sendiri, jadi pilihan genre/negara/tahun terlihat
 * tanpa harus membuka satu lembar filter gabungan. Opsi "Semua …" di dalam
 * tiap chip mengosongkan filter kategori itu.
 */
internal fun LazyListScope.browseControlsRow(
    state: BrowseUiState,
    onSortSelected: (String?) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onCountrySelected: (String?) -> Unit,
    onYearSelected: (String?) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    if (!state.isAvailable) return
    item(key = "browse_controls") {
        Row(
            modifier = Modifier
                .padding(horizontal = horizontalPadding, vertical = 4.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.sortOptions.isNotEmpty()) {
                NuvioDropdownChip(
                    title = "Urutan",
                    label = state.sortLabel,
                    selectedKey = state.sort ?: "",
                    options = state.sortOptions.map { NuvioDropdownOption(key = it.value, label = it.label) },
                    onSelected = { onSortSelected(it.key.ifBlank { null }) },
                )
            }
            if (state.showGenreFilter && state.genres.isNotEmpty()) {
                NuvioDropdownChip(
                    title = "Genre",
                    label = state.genreLabel,
                    selectedKey = state.genre ?: "",
                    options = listOf(NuvioDropdownOption(key = "", label = "Semua genre")) +
                        state.genres.map { NuvioDropdownOption(key = it.value, label = it.label) },
                    onSelected = { onGenreSelected(it.key.ifBlank { null }) },
                )
            }
            if (state.countries.isNotEmpty()) {
                NuvioDropdownChip(
                    title = "Negara",
                    label = state.countryLabel,
                    selectedKey = state.country ?: "",
                    options = listOf(NuvioDropdownOption(key = "", label = "Semua negara")) +
                        state.countries.map { NuvioDropdownOption(key = it.value, label = it.label) },
                    onSelected = { onCountrySelected(it.key.ifBlank { null }) },
                )
            }
            if (state.years.isNotEmpty()) {
                NuvioDropdownChip(
                    title = "Tahun",
                    label = state.yearLabel,
                    selectedKey = state.year ?: "",
                    options = listOf(NuvioDropdownOption(key = "", label = "Semua tahun")) +
                        state.years.map { NuvioDropdownOption(key = it.value, label = it.label) },
                    onSelected = { onYearSelected(it.key.ifBlank { null }) },
                )
            }
        }
    }
}

internal fun LazyListScope.browseGrid(
    state: BrowseUiState,
    columns: Int,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    if (!state.isBrowsing) return
    when {
        state.isLoading && state.items.isEmpty() -> {
            items(2, key = { "browse_skeleton_$it" }) {
                PosterGridSkeletonRow(
                    columns = columns,
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }
        }

        state.items.isEmpty() -> {
            item(key = "browse_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.errorMessage ?: "Tidak ada hasil.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        else -> {
            items(count = (state.items.size + columns - 1) / columns, key = { "browse_row_$it" }) { rowIndex ->
                val firstIndex = rowIndex * columns
                PosterGridRow(
                    items = state.items.subList(
                        firstIndex,
                        minOf(firstIndex + columns, state.items.size),
                    ),
                    columns = columns,
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            }
            if (state.isLoading) {
                item(key = "browse_loading_more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        NuvioLoadingIndicator(modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
