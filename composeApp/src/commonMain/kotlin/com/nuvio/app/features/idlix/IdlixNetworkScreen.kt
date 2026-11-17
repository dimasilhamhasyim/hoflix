package com.nuvio.app.features.idlix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Grid satu jaringan (kartu Network Originals).
 *
 * Paging mengikuti kebiasaan rail: minta halaman berikutnya begitu ujung grid
 * terlihat, berhenti saat bridge bilang `hasMore = false`.
 */
@Composable
internal fun IdlixNetworkScreen(
    slug: String,
    title: String,
    onBack: () -> Unit,
    onPosterClick: (IdlixCollectionsGateway.IdlixMeta) -> Unit,
) {
    var items by remember(slug) { mutableStateOf<List<IdlixCollectionsGateway.IdlixMeta>>(emptyList()) }
    var pageTitle by remember(slug) { mutableStateOf(title) }
    var total by remember(slug) { mutableStateOf(0) }
    var hasMore by remember(slug) { mutableStateOf(false) }
    var loading by remember(slug) { mutableStateOf(false) }
    var error by remember(slug) { mutableStateOf<String?>(null) }
    var started by remember(slug) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    suspend fun load() {
        if (loading) return
        loading = true
        val skip = items.size
        IdlixNetworkGateway.page(slug, skip)
            .onSuccess { page ->
                if (page.title.isNotBlank()) pageTitle = page.title
                total = page.total
                hasMore = page.hasMore
                val known = items.mapTo(mutableSetOf()) { it.id }
                items = items + page.metas.filter { it.id.isNotBlank() && known.add(it.id) }
                error = null
            }
            .onFailure { error = it.message ?: "gagal memuat jaringan" }
        loading = false
        started = true
    }

    LaunchedEffect(slug) {
        items = emptyList()
        hasMore = false
        started = false
        load()
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { if (hasMore && !loading) load() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NetworkScreenTitle(
            title = pageTitle,
            subtitle = when {
                total > 0 -> "$total judul"
                loading -> "Memuat…"
                else -> ""
            },
            onBack = onBack,
        )
        when {
            items.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Memuat jaringan…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    error ?: "Belum ada judul untuk jaringan ini",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = nuvioSafeBottomPadding(20.dp),
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items, key = { it.id }) { meta ->
                    NetworkPoster(meta = meta, onClick = { onPosterClick(meta) })
                }
                if (loading) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Memuat…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (!hasMore && items.isNotEmpty() && started) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "end") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Akhir daftar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkPoster(
    meta: IdlixCollectionsGateway.IdlixMeta,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val art = meta.poster.ifBlank { meta.background }
            if (art.isNotBlank()) {
                AsyncImage(
                    model = art,
                    contentDescription = meta.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = meta.name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta.releaseInfo.isNotBlank()) {
            Text(
                text = meta.releaseInfo,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NetworkScreenTitle(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NuvioBackButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
