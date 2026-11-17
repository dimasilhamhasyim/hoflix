package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeRailChip
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState

@Composable
fun HomeCatalogRowSection(
    section: HomeCatalogSection,
    modifier: Modifier = Modifier,
    entries: List<MetaPreview> = section.items,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    sectionPadding: Dp? = null,
    onViewAllClick: (() -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    onChipSelected: ((HomeRailChip) -> Unit)? = null,
) {
    if (sectionPadding != null) {
        HomeCatalogRowSectionContent(
            section = section,
            entries = entries,
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            modifier = modifier.fillMaxWidth(),
            sectionPadding = sectionPadding,
            onViewAllClick = onViewAllClick,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
            onLoadMore = onLoadMore,
            onChipSelected = onChipSelected,
        )
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            HomeCatalogRowSectionContent(
                section = section,
                entries = entries,
                watchedKeys = watchedKeys,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                modifier = Modifier.fillMaxWidth(),
                sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value),
                onViewAllClick = onViewAllClick,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
                onLoadMore = onLoadMore,
                onChipSelected = onChipSelected,
            )
        }
    }
}

@Composable
private fun HomeCatalogRowSectionContent(
    section: HomeCatalogSection,
    entries: List<MetaPreview>,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    modifier: Modifier,
    sectionPadding: Dp,
    onViewAllClick: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onLoadMore: (() -> Unit)?,
    onChipSelected: ((HomeRailChip) -> Unit)?,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val activeChipId = section.selectedChipId ?: (section.target as? CatalogTarget.Addon)?.catalogId
    // Posisi geser diikat ke chip yang sedang aktif: ganti chip = mulai dari
    // kartu pertama. Sebelum ini, offset dari chip sebelumnya terbawa dan rail
    // terlihat "tidak reset" (kartu pertama masih milik chip lama).
    val shelfState = key(activeChipId) { rememberLazyListState() }

    NuvioShelfSection(
        title = section.title,
        entries = entries,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        onViewAllClick = onViewAllClick,
        viewAllPillSize = NuvioViewAllPillSize.Compact,
        headerContent = if (section.chips.isNotEmpty() && onChipSelected != null) {
            {
                RailChipRow(
                    chips = section.chips,
                    activeChipId = activeChipId,
                    horizontalPadding = sectionPadding,
                    onChipClick = onChipSelected,
                )
            }
        } else {
            null
        },
        itemSpacing = 8.dp,
        key = { item -> item.stableKey() },
        state = shelfState,
        onLoadMore = onLoadMore,
    ) { item ->
        HomePosterCard(
            item = item,
            useLandscapeBackdropMode = posterCardStyle.catalogLandscapeModeEnabled,
            isWatched = WatchingState.isPosterWatched(
                watchedKeys = watchedKeys,
                item = item,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            ),
            onClick = onPosterClick?.let { { it(item) } },
            onLongClick = onPosterLongClick?.let { { it(item) } },
        )
    }
}

/**
 * Baris chip pemilih di bawah judul rail, mengikuti web IDLIX: Trending Now
 * punya All / Movies / TV Series, Network Originals punya daftar jaringan,
 * TMDB Trending punya chip drama per negara.
 *
 * Chip **tidak** menyaring di klien: tiap chip menunjuk katalognya sendiri di
 * bridge, jadi memilih chip memuat halaman pertama katalog itu.
 */
@Composable
private fun RailChipRow(
    chips: List<HomeRailChip>,
    activeChipId: String?,
    horizontalPadding: Dp,
    onChipClick: (HomeRailChip) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    LazyRow(
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { chip -> chip.catalogId }) { chip ->
            val selected = chip.catalogId == activeChipId
            Text(
                text = chip.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) tokens.colors.accent else tokens.colors.surface)
                    .clickable { onChipClick(chip) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) tokens.colors.onAccent else tokens.colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}
