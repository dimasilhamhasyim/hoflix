package com.nuvio.app.features.search

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.addonTransportBaseUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.home.HomeCatalogParser
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

private const val HOFLIX_ADDON_ID = "org.hoflix.moviebox"

/** Sort keys the bridge understands, in the order it lists them. */
internal val DEFAULT_SORT_VALUES = listOf("relevance", "newest", "oldest", "title")

data class BrowseTab(
    val id: String,
    val label: String,
)

/** A filter choice: `value` goes to the API, `label` is what the chip prints. */
data class FilterOption(
    val value: String,
    val label: String,
)

data class BrowseUiState(
    val isAvailable: Boolean = false,
    val tabs: List<BrowseTab> = emptyList(),
    val selectedTab: String? = null,
    val categories: List<FilterOption> = emptyList(),
    val genres: List<FilterOption> = emptyList(),
    val countries: List<FilterOption> = emptyList(),
    val years: List<FilterOption> = emptyList(),
    val sorts: List<String> = emptyList(),
    val sortValues: List<String> = emptyList(),
    val category: String? = null,
    val genre: String? = null,
    val country: String? = null,
    val year: String? = null,
    val sort: String? = null,
    val items: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = false,
    val nextSkip: Int? = null,
    val errorMessage: String? = null,
) {
    val isBrowsing: Boolean
        get() = selectedTab != null

    val canLoadMore: Boolean
        get() = nextSkip != null

    /** The Anime tab keeps an implicit genre, so the genre chip is hidden there. */
    val showGenreFilter: Boolean
        get() = selectedTab != "anime"

    val categoryLabel: String
        get() = categories.firstOrNull { it.value == category }?.label ?: "Kategori"

    val genreLabel: String
        get() = genres.firstOrNull { it.value == genre }?.label ?: "Genre"

    val countryLabel: String
        get() = countries.firstOrNull { it.value == country }?.label ?: "Negara"

    val yearLabel: String
        get() = years.firstOrNull { it.value == year }?.label ?: "Tahun"

    /** Sort chip options: `value` goes to the API, `label` is what the chip prints. */
    val sortOptions: List<FilterOption>
        get() = sorts.mapIndexedNotNull { index, label ->
            val value = sortValues.getOrNull(index) ?: return@mapIndexedNotNull null
            FilterOption(value = value, label = label)
        }

    val sortLabel: String
        get() = sortOptions.firstOrNull { it.value == sort }?.label ?: "Urutan"

    /** Jumlah filter aktif (genre, negara, tahun) untuk badge tombol Filter. */
    val activeFilterCount: Int
        get() = listOfNotNull(genre, country, year).count { it.isNotBlank() }

    val hasActiveFilters: Boolean
        get() = activeFilterCount > 0
}

/**
 * MovieBox-style browse: Film / Series / Anime tabs, each with genre, country,
 * year and sort filters, backed by the HOFLIX bridge's own filter endpoint.
 */
object BrowseRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    /** Bumped whenever a rail preset opens the grid, so the screen can drop a stale query. */
    private val _presetRequests = MutableStateFlow(0)
    val presetRequests: StateFlow<Int> = _presetRequests.asStateFlow()

    private var baseUrl: String? = null
    private var filtersLoaded = false
    private var loadJob: Job? = null

    fun ensureLoaded(addons: List<ManagedAddon>) {
        val bridge = addons.enabledAddons().firstOrNull { addon ->
            addon.manifest?.id == HOFLIX_ADDON_ID
        } ?: return
        val nextBaseUrl = addonTransportBaseUrl(bridge.manifestUrl)
        if (_uiState.value.isAvailable && baseUrl == nextBaseUrl && filtersLoaded) return
        baseUrl = nextBaseUrl
        scope.launch {
            if (!filtersLoaded) {
                val filters = runCatching { fetchFilters(nextBaseUrl) }.getOrNull()
                filtersLoaded = filters != null
                if (filters != null) {
                    _uiState.value = _uiState.value.copy(
                        isAvailable = true,
                        tabs = filters.tabs,
                        categories = filters.categories,
                        genres = filters.genres,
                        countries = filters.countries,
                        years = filters.years,
                        sorts = filters.sorts,
                        sortValues = filters.sortValues,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isAvailable = true,
                        tabs = listOf(
                            BrowseTab("all", "Semua"),
                            BrowseTab("movie", "Film"),
                            BrowseTab("series", "Series"),
                        ),
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(isAvailable = true)
            }
            // A rail's "see all" can open the grid before this screen ever ran,
            // so the tab it selected is only now backed by a base URL.
            if (_uiState.value.isBrowsing) load(reset = true)
        }
    }

    fun selectTab(tabId: String) {
        val current = _uiState.value
        if (current.selectedTab == tabId) return
        _uiState.value = current.copy(
            selectedTab = tabId,
            category = null,
            genre = null,
            country = null,
            year = null,
            sort = null,
            items = emptyList(),
            nextSkip = null,
            errorMessage = null,
        )
        load(reset = true)
    }

    /** Re-reads the current tab; used when the 18+ switch changes the feed. */
    fun refresh() {
        if (_uiState.value.isBrowsing) load(reset = true)
    }

    /**
     * Opens the grid on a rail-preset filter. The rail itself stays finite; this
     * is the browse feed of the same type with the row's dominant genre, which
     * the user can then change like any other filter.
     */
    fun openPreset(tab: String, genre: String?) {
        if (tab.isBlank()) return
        val current = _uiState.value
        _uiState.value = current.copy(
            selectedTab = tab,
            category = null,
            genre = genre?.takeIf { tab != "anime" && it.isNotBlank() },
            country = null,
            year = null,
            sort = null,
            items = emptyList(),
            nextSkip = null,
            errorMessage = null,
        )
        _presetRequests.value += 1
        load(reset = true)
    }

    fun clearTab() {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedTab = null,
            category = null,
            genre = null,
            country = null,
            year = null,
            sort = null,
            items = emptyList(),
            nextSkip = null,
            isLoading = false,
            errorMessage = null,
        )
    }

    /** Filters and rows carry the catalog language, so a language switch drops them. */
    fun clearLanguageState() {
        filtersLoaded = false
        clearTab()
        _uiState.value = _uiState.value.copy(
            isAvailable = false,
            tabs = emptyList(),
            categories = emptyList(),
            genres = emptyList(),
            countries = emptyList(),
            years = emptyList(),
            sorts = emptyList(),
            sortValues = emptyList(),
        )
    }

    fun selectCategory(category: String?) = updateFilter { it.copy(category = category) }

    fun selectGenre(genre: String?) = updateFilter { it.copy(genre = genre) }

    fun selectCountry(country: String?) = updateFilter { it.copy(country = country) }

    fun selectYear(year: String?) = updateFilter { it.copy(year = year) }

    fun selectSort(sort: String?) = updateFilter { it.copy(sort = sort) }

    /** Terapkan genre + negara + tahun sekaligus dari satu lembar filter. */
    fun applyFilters(genre: String?, country: String?, year: String?) {
        val current = _uiState.value
        if (!current.isBrowsing) return
        _uiState.value = current.copy(
            genre = genre?.takeIf { it.isNotBlank() },
            country = country?.takeIf { it.isNotBlank() },
            year = year?.takeIf { it.isNotBlank() },
            items = emptyList(),
            nextSkip = null,
            errorMessage = null,
        )
        load(reset = true)
    }

    fun clearFilters() = applyFilters(genre = null, country = null, year = null)

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.nextSkip == null) return
        load(reset = false)
    }

    private fun updateFilter(transform: (BrowseUiState) -> BrowseUiState) {
        val current = _uiState.value
        if (!current.isBrowsing) return
        _uiState.value = transform(current).copy(
            items = emptyList(),
            nextSkip = null,
            errorMessage = null,
        )
        load(reset = true)
    }

    private fun load(reset: Boolean) {
        val base = baseUrl ?: return
        val current = _uiState.value
        val tab = current.selectedTab ?: return
        val skip = if (reset) 0 else current.nextSkip ?: return
        loadJob?.cancel()
        _uiState.value = current.copy(
            isLoading = true,
            items = if (reset) emptyList() else current.items,
            nextSkip = if (reset) null else current.nextSkip,
            errorMessage = null,
        )
        loadJob = scope.launch {
            try {
                val url = buildString {
                    append(base)
                    append("/browse/")
                    append(tab)
                    append(".json?skip=")
                    append(skip)
                    current.category?.takeIf { it.isNotBlank() }?.let { append("&category=").append(it.encode()) }
                    current.genre?.takeIf { it.isNotBlank() }?.let { append("&genre=").append(it.encode()) }
                    current.country?.takeIf { it.isNotBlank() }?.let { append("&country=").append(it.encode()) }
                    current.year?.takeIf { it.isNotBlank() }?.let { append("&year=").append(it.encode()) }
                    current.sort?.takeIf { it.isNotBlank() }?.let { append("&sort=").append(it.encode()) }
                }
                val payload = fetchAddonResponseText(url)
                val parsed = HomeCatalogParser.parseCatalogResponse(payload)
                val latest = _uiState.value
                if (latest.selectedTab != tab || latest.category != current.category ||
                    latest.genre != current.genre ||
                    latest.country != current.country || latest.year != current.year ||
                    latest.sort != current.sort
                ) {
                    return@launch
                }
                val merged = if (reset) {
                    parsed.items
                } else {
                    (latest.items + parsed.items).distinctBy { it.stableKey() }
                }
                val loadedNewItems = merged.size > latest.items.size
                _uiState.value = latest.copy(
                    items = merged,
                    isLoading = false,
                    nextSkip = if (parsed.rawItemCount > 0 && loadedNewItems) skip + parsed.rawItemCount else null,
                    errorMessage = if (merged.isEmpty()) "Tidak ada hasil untuk filter ini." else null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Gagal memuat katalog.",
                )
            }
        }
    }

    private fun String.encode(): String =
        buildString {
            encodeToByteArray().forEach { byte ->
                val value = byte.toInt() and 0xFF
                val char = value.toChar()
                if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' ||
                    char == '-' || char == '_' || char == '.' || char == '~'
                ) {
                    append(char)
                } else {
                    append('%')
                    append(HEX[value shr 4])
                    append(HEX[value and 0x0F])
                }
            }
        }

    private data class Filters(
        val tabs: List<BrowseTab>,
        val categories: List<FilterOption>,
        val genres: List<FilterOption>,
        val countries: List<FilterOption>,
        val years: List<FilterOption>,
        val sorts: List<String>,
        val sortValues: List<String>,
    )

    private suspend fun fetchFilters(base: String): Filters {
        val root = json.parseToJsonElement(fetchAddonResponseText("$base/filters.json")).jsonObject
        val tabs = root["tabs"]?.jsonArray?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id") ?: return@mapNotNull null
            BrowseTab(id = id, label = item.string("label") ?: id)
        }.orEmpty()
        return Filters(
            tabs = tabs,
            categories = root.optionList("categories"),
            genres = root.optionList("genres"),
            countries = root.optionList("countries"),
            years = root.optionList("years"),
            sorts = root.stringList("sorts"),
            // Older bridge builds sent labels only; the four values are stable,
            // so a missing list still gives working chips instead of none.
            sortValues = root.stringList("sortValues").ifEmpty {
                val labels = root.stringList("sorts")
                if (labels.isEmpty()) emptyList() else DEFAULT_SORT_VALUES.take(labels.size)
            },
        )
    }

    /**
     * Accepts both shapes the bridge can send: a bare string (value == label)
     * or `{value,label}`, so a stale manifest cannot blank the filter chips.
     */
    private fun JsonObject.optionList(key: String): List<FilterOption> =
        (this[key] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { element ->
                when (element) {
                    is JsonObject -> {
                        val value = element.string("value") ?: element.string("id")
                            ?: return@mapNotNull null
                        FilterOption(value = value, label = element.string("label") ?: value)
                    }
                    is JsonPrimitive -> element.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { FilterOption(value = it, label = it) }
                    else -> null
                }
            }
            .orEmpty()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()

    private val HEX = "0123456789ABCDEF".toCharArray()
}
