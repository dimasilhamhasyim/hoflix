package com.nuvio.app.features.home

import com.nuvio.app.features.addons.CatalogBrowsePreset
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.details.MetaExternalRating

data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val logo: String? = null,
    val posterShape: PosterShape = PosterShape.Poster,
    val description: String? = null,
    val releaseInfo: String? = null,
    val rawReleaseDate: String? = null,
    val popularity: Double? = null,
    val voteCount: Int? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val rawPosterUrl: String? = null,
    val landscapePoster: String? = null,
    val rawLandscapePosterUrl: String? = null,
    val runtime: String? = null,
    val status: String? = null,
    val ageRating: String? = null,
    val language: String? = null,
    val commentCount: Int? = null,
    val externalRatings: List<MetaExternalRating> = emptyList(),
)

fun MetaPreview.stableKey(): String = "$type:$id"

enum class PosterShape {
    Poster,
    Square,
    Landscape,
}

data class HomeCatalogSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val addonName: String,
    val target: CatalogTarget,
    val items: List<MetaPreview>,
    val availableItemCount: Int = items.size,
    val hasMore: Boolean = false,
    /** The browse feed this rail opens in "see all", when the addon declares one. */
    val railBrowse: CatalogBrowsePreset? = null,
    /** Pilihan di header rail (All/Movies/TV Series, network, chip TMDB). */
    val chips: List<HomeRailChip> = emptyList(),
    /** `catalogId` chip yang sedang dipakai; null = katalog dasar rail. */
    val selectedChipId: String? = null,
)

/** Satu chip di header rail; `catalogId` adalah katalog yang dimuat saat dipilih. */
data class HomeRailChip(
    val label: String,
    val catalogId: String,
    val type: String,
)

fun HomeCatalogSection.canOpenCatalog(previewLimit: Int): Boolean =
    availableItemCount > previewLimit || hasMore

/**
 * A curated rail is finite by design, so the old "more items than the preview"
 * test hid its "see all" button. A rail that declares a browse view always
 * offers it: the grid is a filtered view of the catalog, titled after the rail.
 */
fun HomeCatalogSection.canOpenRailBrowse(): Boolean = railBrowse != null

data class HomeUiState(
    val isLoading: Boolean = false,
    val heroItems: List<MetaPreview> = emptyList(),
    val sections: List<HomeCatalogSection> = emptyList(),
    val errorMessage: String? = null,
)

internal fun shouldShowInitialHomeLoading(
    hasRenderableHomeRows: Boolean,
    addonManifestsLoading: Boolean,
    homeCatalogLoading: Boolean,
): Boolean = !hasRenderableHomeRows && (addonManifestsLoading || homeCatalogLoading)

internal fun shouldShowHomeHeroSlot(
    heroEnabled: Boolean,
    hasHeroItems: Boolean,
    isResolvingHeroSources: Boolean,
    hasRenderableHomeRows: Boolean,
): Boolean = heroEnabled && (hasHeroItems || isResolvingHeroSources || hasRenderableHomeRows)

internal data class CatalogRequest(
    val addon: ManagedAddon,
    val catalogId: String,
    val catalogName: String,
    val type: String,
    val supportsPagination: Boolean,
)
