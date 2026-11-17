package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioPosterBadges
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.hoflix_coming_soon
import nuvio.composeapp.generated.resources.hoflix_coming_soon_date
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomePosterCard(
    item: MetaPreview,
    modifier: Modifier = Modifier,
    useLandscapeBackdropMode: Boolean = false,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showLandscapeOverlay: Boolean = true,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val isLandscapeMode = useLandscapeBackdropMode || posterCardStyle.catalogLandscapeModeEnabled
    val isComingSoon = item.status?.equals("Coming Soon", ignoreCase = true) == true
    val comingSoonLine = if (isComingSoon) {
        val date = item.rawReleaseDate
            ?.takeIf { it.isNotBlank() }
            ?.let { formatReleaseDateForDisplay(it) }
        if (date != null) {
            stringResource(Res.string.hoflix_coming_soon_date, date)
        } else {
            stringResource(Res.string.hoflix_coming_soon)
        }
    } else {
        null
    }
    val effectiveLandscapePoster = if (posterCardStyle.alwaysShowLandscapeClearlogo) null else item.landscapePoster
    val imageUrl = if (isLandscapeMode) {
        effectiveLandscapePoster ?: item.banner ?: item.poster
    } else item.poster
    val fallbackImageUrl = if (isLandscapeMode && !effectiveLandscapePoster.isNullOrBlank()) {
        // Landscape custom poster -> fall back to original backdrop, then portrait
        item.banner ?: item.rawPosterUrl
    } else {
        item.rawPosterUrl
    }

    NuvioPosterCard(
        title = item.cardTitle(),
        imageUrl = imageUrl,
        modifier = modifier,
        fallbackImageUrl = fallbackImageUrl,
        shape = if (isLandscapeMode) NuvioPosterShape.Landscape else item.posterShape.toNuvioPosterShape(),
        detailLine = if (isLandscapeMode || posterCardStyle.hideLabelsEnabled || item.cardEpisodeBadge() != null) {
            null
        } else {
            // Tahun sudah pindah ke judul ("Heartland (2026)"), jadi baris
            // kedua hanya diisi kalau isinya bukan tahun saja.
            comingSoonLine ?: item.releaseInfo
                ?.takeIf { it.trim().length != 4 || !it.trim().all { ch -> ch.isDigit() } }
                ?.let { formatReleaseDateForDisplay(it) }
        },
        // Kartu brand jaringan: hanya logo di atas warna brand, tanpa label teks.
        showTitleBelow = !posterCardStyle.hideLabelsEnabled && !item.isNetworkBrandCard(),
        bottomLeftLogoUrl = if (isLandscapeMode && showLandscapeOverlay && effectiveLandscapePoster.isNullOrBlank()) item.logo else null,
        bottomLeftText = if (isLandscapeMode && showLandscapeOverlay && effectiveLandscapePoster.isNullOrBlank() && item.logo.isNullOrBlank() && !posterCardStyle.hideLabelsEnabled) item.name else null,
        isWatched = isWatched,
        badges = when {
            posterCardStyle.hideLabelsEnabled -> null
            // Kartu episode (rail "Recently Added Episodes"): badge musim/episode
            // menggantikan badge tipe, dan tetap tampil walau kartunya lanskap.
            item.cardEpisodeBadge() != null -> NuvioPosterBadges(
                topLeft = item.cardEpisodeBadge(),
                topRight = item.cardRating(),
                bottomRight = null,
            )
            isLandscapeMode -> null
            else -> item.cardBadges()
        },
        emphasizeTitle = true,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/** Kartu brand jaringan (`network:<slug>`): gambarnya sudah logo resmi. */
internal fun MetaPreview.isNetworkBrandCard(): Boolean = id.startsWith("network:")

/** Judul kartu: `Heartland (2026)`, sama seperti kartu web IDLIX. */
internal fun MetaPreview.cardTitle(): String {
    // Kartu koleksi & kartu jaringan memakai namanya apa adanya (tanpa tahun).
    if (id.startsWith("collection:") || id.startsWith("network:")) return name
    val year = releaseInfo?.trim().orEmpty()
    return if (year.length == 4 && year.all { it.isDigit() }) "$name ($year)" else name
}

/** `S01 E26` untuk kartu episode, null untuk kartu judul biasa. */
internal fun MetaPreview.cardEpisodeBadge(): String? {
    val text = releaseInfo?.trim().orEmpty()
    val match = Regex("^S(\\d{1,2})\\s*E(\\d{1,4})$").find(text) ?: return null
    val season = match.groupValues[1].toIntOrNull() ?: return null
    val episode = match.groupValues[2].toIntOrNull() ?: return null
    // `String.format` hanya ada di JVM; padStart aman untuk semua target.
    return "S${season.toString().padStart(2, '0')} E${episode.toString().padStart(2, '0')}"
}

/** Badge rating bintang, dipakai kartu episode yang tidak memakai badge tipe. */
internal fun MetaPreview.cardRating(): String? =
    imdbRating?.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { ((it * 10).toInt() / 10.0).toString() }

/** Badge tipe dan rating ala kartu IDLIX. */
internal fun MetaPreview.cardBadges(): NuvioPosterBadges? {
    if (id.startsWith("collection:") || id.startsWith("network:")) return null
    // Badge FILM tidak dipakai lagi; SERIAL TV tetap supaya mediumnya jelas.
    val typeLabel = if (type == "series") "SERIAL TV" else null
    val rating = cardRating()
    if (typeLabel == null && rating == null) return null
    return NuvioPosterBadges(
        topLeft = typeLabel,
        topRight = rating,
        bottomRight = null,
    )
}

private fun PosterShape.toNuvioPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }
