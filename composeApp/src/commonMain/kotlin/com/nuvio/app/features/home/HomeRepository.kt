package com.nuvio.app.features.home

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.core.poster.reapplyCustomPosterUrls
import com.nuvio.app.core.poster.withCustomPosterUrls
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.collection.catalogRouteKey
import com.nuvio.app.features.collection.findCollectionCatalog
import com.nuvio.app.features.servers.ServerCatalog
import com.nuvio.app.features.trakt.TraktPublicListSourceResolver
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Katalog yang isinya bukan judul (koleksi), jadi tidak layak jadi hero.
 *
 * Urutan Beranda sendiri mengikuti urutan katalog di manifest addon: bridge
 * HOFLIX membangun manifest itu dari Beranda IDLIX, jadi rail ikut berubah saat
 * IDLIX mengubah susunannya — tidak perlu kode baru di aplikasi. Katalog yang
 * ditandai `featured` hanya jadi sumber hero, bukan rail.
 */
private fun isNonTitleCatalog(catalogId: String): Boolean = catalogId == "hoflix-collections"

object HomeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeRequestKey: String? = null
    private var currentRequestKey: String? = null
    private var currentDefinitions: List<HomeCatalogDefinition> = emptyList()
    private var cachedSections: Map<String, HomeCatalogSection> = emptyMap()
    private var cachedCollectionHeroItems: List<MetaPreview> = emptyList()
    private var collectionHeroJob: Job? = null
    private var collectionHeroRequestKey: String? = null
    private var lastPublishedCatalogHeroEmpty: Boolean = true
    private var lastErrorMessage: String? = null
    private val loadingMoreSectionKeys = mutableSetOf<String>()
    /** section key → catalogId chip yang dipilih. Hanya di memori. */
    private val chipSelections = mutableMapOf<String, String>()
    private val chipJobs = mutableMapOf<String, Job>()

    fun refresh(addons: List<ManagedAddon>, force: Boolean = false) {
        val activeAddons = addons.enabledAddons()
        val requests = buildHomeCatalogDefinitions(activeAddons)
        currentDefinitions = requests
        val requestKeys = requests.mapTo(mutableSetOf(), HomeCatalogDefinition::key)
        chipSelections.keys.retainAll(requestKeys)
        val requestCacheKeys = requests.mapTo(mutableSetOf(), HomeCatalogDefinition::cacheKey)
        cachedSections = cachedSections.filterKeys(requestCacheKeys::contains)
        val requestKey = requests.joinToString(separator = "|", transform = HomeCatalogDefinition::cacheKey)
        currentRequestKey = requestKey

        if (!force && activeRequestKey == requestKey && _uiState.value.isLoading) return
        activeRequestKey = requestKey

        if (requests.isEmpty()) {
            activeJob?.cancel()
            activeJob = null
            activeRequestKey = null
            cachedSections = emptyMap()
            lastErrorMessage = null
            publishCurrentState(
                isLoading = false,
                requestKey = requestKey,
            )
            ensureCollectionHeroFallback(
                addons = activeAddons,
                forceRefresh = force,
                refreshSources = true,
                requestKey = requestKey,
            )
            return
        }

        activeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        activeJob = scope.launch {
            val prioritizedRequests = prioritizeDefinitions(
                definitions = requests,
                snapshot = HomeCatalogSettingsRepository.snapshot(),
            )
            val loadedSections = linkedMapOf<String, HomeCatalogSection>().apply {
                putAll(cachedSections)
            }
            var firstErrorMessage: String? = null
            var batchIndex = 0

            prioritizedRequests.chunked(HOME_CATALOG_FETCH_BATCH_SIZE).forEach { batch ->
                if (activeRequestKey != requestKey) return@launch
                val results = batch.map { request ->
                    async {
                        request to runCatching {
                            request.toSection(forceRefresh = force)
                        }
                    }
                }.awaitAll()

                if (activeRequestKey != requestKey) return@launch

                results.mapNotNull { (request, result) ->
                    result.getOrNull()?.let { section -> request.cacheKey to section }
                }.forEach { (cacheKey, section) ->
                    loadedSections[cacheKey] = section
                }
                if (firstErrorMessage == null) {
                    firstErrorMessage = results.firstNotNullOfOrNull { (_, result) ->
                        result.exceptionOrNull()?.message
                    }
                }
                cachedSections = loadedSections.toMap()
                lastErrorMessage = firstErrorMessage
                if (batchIndex == 0 || (batchIndex + 1) % HOME_CATALOG_PUBLISH_INTERVAL == 0) {
                    publishCurrentState(
                        isLoading = true,
                        requestKey = requestKey,
                    )
                }
                batchIndex++
            }

            if (activeRequestKey != requestKey) return@launch

            cachedSections = loadedSections.toMap()
            lastErrorMessage = firstErrorMessage
            activeRequestKey = null
            publishCurrentState(
                isLoading = false,
                requestKey = requestKey,
            )
            ensureCollectionHeroFallback(
                addons = activeAddons,
                forceRefresh = force,
                refreshSources = true,
                requestKey = requestKey,
            )
        }
    }

    /**
     * Append the next catalog page to one home rail. MovieBox rows keep loading
     * as the viewer swipes, so the rail asks for the next `skip` instead of
     * stopping at the first page.
     */
    fun loadMoreSection(sectionKey: String) {
        val section = _uiState.value.sections.firstOrNull { it.key == sectionKey } ?: return
        if (!section.hasMore) return
        val target = section.target as? CatalogTarget.Addon ?: return
        val definition = currentDefinitions.firstOrNull { it.key == sectionKey } ?: return
        if (!loadingMoreSectionKeys.add(sectionKey)) return
        scope.launch {
            try {
                val page = fetchCatalogPage(
                    manifestUrl = target.manifestUrl,
                    type = target.contentType,
                    catalogId = target.catalogId,
                    genre = target.genre,
                    skip = section.availableItemCount,
                    maxItems = HOME_CATALOG_PAGE_SIZE,
                )
                if (page.items.isEmpty()) {
                    cachedSections = cachedSections.mapValues { (_, cached) ->
                        if (cached.key == sectionKey) cached.copy(hasMore = false) else cached
                    }
                    publishCurrentState(
                        isLoading = _uiState.value.isLoading,
                        requestKey = currentRequestKey,
                    )
                    return@launch
                }
                val existing = cachedSections[definition.cacheKey]
                val mergedItems = ((existing?.items ?: section.items) + page.items)
                    .distinctBy { item -> item.stableKey() }
                val loadedNewItems = mergedItems.size > (existing?.items?.size ?: section.items.size)
                val merged = (existing ?: section).copy(
                    items = mergedItems,
                    availableItemCount = section.availableItemCount + page.rawItemCount,
                    hasMore = page.nextSkip != null && loadedNewItems,
                )
                cachedSections = cachedSections + (definition.cacheKey to merged)
                publishCurrentState(
                    isLoading = _uiState.value.isLoading,
                    requestKey = currentRequestKey,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // A failed tail page just leaves the rail where it was.
            } finally {
                loadingMoreSectionKeys.remove(sectionKey)
            }
        }
    }

    /**
     * Ganti katalog yang dimuat satu rail lewat chip di header (All / Movies /
     * TV Series, network, chip TMDB). Tiap chip adalah katalog sendiri di
     * bridge, jadi ini memuat ulang rail itu saja — rail lain tidak tersentuh.
     */
    fun selectRailChip(sectionKey: String, catalogId: String) {
        val definition = currentDefinitions.firstOrNull { it.key == sectionKey } ?: return
        if (definition.chips.none { it.catalogId == catalogId }) return
        val current = _uiState.value.sections.firstOrNull { it.key == sectionKey }
        if (current?.selectedChipId == catalogId) return
        chipSelections[sectionKey] = catalogId
        chipJobs.remove(sectionKey)?.cancel()
        chipJobs[sectionKey] = scope.launch {
            val section = runCatching { definition.toSection(forceRefresh = false) }.getOrNull()
            if (section != null) {
                cachedSections = cachedSections + (definition.cacheKey to section)
            }
            publishCurrentState(
                isLoading = _uiState.value.isLoading,
                requestKey = currentRequestKey,
            )
            chipJobs.remove(sectionKey)
        }
    }

    fun applyCurrentSettings() {
        publishCurrentState(
            isLoading = _uiState.value.isLoading,
            requestKey = currentRequestKey,
        )
        ensureCollectionHeroFallback(
            addons = AddonRepository.uiState.value.addons.enabledAddons(),
            forceRefresh = false,
            refreshSources = false,
            requestKey = currentRequestKey,
        )
    }

    fun clear() {
        activeJob?.cancel()
        activeJob = null
        activeRequestKey = null
        currentRequestKey = null
        currentDefinitions = emptyList()
        cachedSections = emptyMap()
        cachedCollectionHeroItems = emptyList()
        collectionHeroJob?.cancel()
        collectionHeroJob = null
        collectionHeroRequestKey = null
        lastPublishedCatalogHeroEmpty = true
        lastErrorMessage = null
        _uiState.value = HomeUiState()
    }

    private fun publishCurrentState(
        isLoading: Boolean,
        requestKey: String?,
    ) {
        val snapshot = HomeCatalogSettingsRepository.snapshot()
        val preferences = snapshot.preferences
        val todayIsoDate = if (snapshot.hideUnreleasedContent) CurrentDateProvider.todayIsoDate() else null
        CustomPosterUrlRepository.ensureLoaded()
        val posterPattern = CustomPosterUrlRepository.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.HOME)
        fun HomeCatalogSection.withReleaseFilter(): HomeCatalogSection =
            if (todayIsoDate == null) this else filterReleasedItems(todayIsoDate)
        fun HomeCatalogSection.withPosterOverlay(): HomeCatalogSection =
            copy(items = items.reapplyCustomPosterUrls(posterPattern))

        val sections = currentDefinitions
            .sortedWith(
                compareBy(
                    // Urutan rail = urutan katalog di manifest. Manifest bridge
                    // dibangun dari Beranda IDLIX, jadi susunannya mengikuti
                    // sumber itu, bukan urutan lama yang tersimpan di HP.
                    { definition -> definition.manifestOrder },
                    { definition -> definition.key },
                ),
            )
            .mapNotNull { definition ->
                // Katalog `featured` hanya sumber hero; tidak dirender sebagai rail.
                if (definition.featured) return@mapNotNull null
                val preference = preferences[definition.key]
                if (preference?.enabled == false) return@mapNotNull null

                val section = cachedSections[definition.cacheKey]
                    ?.withPosterOverlay()
                    ?.withReleaseFilter()
                    ?: return@mapNotNull null
                if (section.items.isEmpty()) return@mapNotNull null
                val customTitle = preference?.customTitle.orEmpty()
                section.copy(
                    title = customTitle.ifBlank { definition.titleFor(snapshot.showCatalogType) },
                )
            }

        val catalogHeroItems = if (snapshot.heroEnabled) {
            // Seed acak per proses: urutan hero berubah tiap aplikasi dibuka,
            // tapi tetap sama sepanjang satu sesi supaya slide tidak melompat
            // saat batch rail berikutnya selesai dimuat.
            val heroRandom = Random(HERO_PROCESS_SEED)
            fun sectionItems(definition: HomeCatalogDefinition) =
                cachedSections[definition.cacheKey]
                    ?.withPosterOverlay()
                    ?.withReleaseFilter()
            // Default Nuvio: satu kolam berisi SEMUA katalog yang ditandai
            // sebagai sumber hero, digabung lalu diacak — bukan "featured
            // dulu, fallback kalau kosong". Dengan begitu pilihan dua sumber
            // (mis. Film Indonesia + Featured) benar-benar terselang-seling,
            // persis seperti aplikasi aslinya.
            currentDefinitions
                .filter { definition -> preferences[definition.key]?.heroSourceEnabled != false }
                .filterNot { definition -> isNonTitleCatalog(definition.catalogId) }
                .mapNotNull(::sectionItems)
                .flatMap { section -> section.items }
                // Hanya gambar lanskap HD yang boleh jadi slide: poster w342
                // dan backdrop w780 selalu terlihat burem di layar 1080.
                .filter { item -> item.hasHdHeroImage() }
                .distinctBy { item -> "${item.type}:${item.id}" }
                .shuffled(heroRandom)
                .take(HOME_HERO_ITEM_LIMIT)
        } else {
            emptyList()
        }
        lastPublishedCatalogHeroEmpty = snapshot.heroEnabled && catalogHeroItems.isEmpty()
        val heroItems = if (snapshot.heroEnabled) {
            catalogHeroItems.ifEmpty { cachedCollectionHeroItems }
        } else {
            emptyList()
        }

        _uiState.value = HomeUiState(
            isLoading = isLoading,
            heroItems = heroItems,
            sections = sections,
            errorMessage = if (sections.isEmpty()) lastErrorMessage else null,
        )
    }

    private suspend fun HomeCatalogDefinition.toSection(forceRefresh: Boolean): HomeCatalogSection {
        serverConnectionId?.let { connectionId -> return toServerSection(connectionId) }
        CustomPosterUrlRepository.ensureLoaded()
        val pattern = CustomPosterUrlRepository.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.HOME)
        // Chip aktif mengganti katalog yang dimuat, bukan menyaring di klien:
        // tiap chip di manifest menunjuk katalognya sendiri di bridge.
        val activeChip = chipSelections[key]?.let { id -> chips.firstOrNull { it.catalogId == id } }
        val activeType = activeChip?.type ?: type
        val activeCatalogId = activeChip?.catalogId ?: catalogId
        val railChips = chips.map { chip -> HomeRailChip(chip.label, chip.catalogId, chip.type ?: type) }
        val page = fetchCatalogPage(
            manifestUrl = manifestUrl,
            type = activeType,
            catalogId = activeCatalogId,
            maxItems = HOME_CATALOG_PREVIEW_FETCH_LIMIT,
            forceRefresh = forceRefresh,
        )
        val items = if (pattern.isNotBlank()) {
            page.items.withCustomPosterUrls(pattern)
        } else {
            page.items
        }
        if (items.isEmpty()) {
            return HomeCatalogSection(
                key = key,
                title = defaultTitle,
                subtitle = addonName,
                addonName = addonName,
                target = CatalogTarget.Addon(
                    manifestUrl = manifestUrl,
                    contentType = activeType,
                    catalogId = activeCatalogId,
                    supportsPagination = supportsPagination,
                ),
                items = emptyList(),
                availableItemCount = 0,
                hasMore = false,
                railBrowse = railBrowse,
                chips = railChips,
                selectedChipId = activeChip?.catalogId,
            )
        }

        return HomeCatalogSection(
            key = key,
            title = defaultTitle,
            subtitle = addonName,
            addonName = addonName,
            target = CatalogTarget.Addon(
                manifestUrl = manifestUrl,
                contentType = activeType,
                catalogId = activeCatalogId,
                supportsPagination = supportsPagination,
            ),
            items = items,
            availableItemCount = page.rawItemCount,
            hasMore = supportsPagination && page.nextSkip != null,
            railBrowse = railBrowse,
            chips = railChips,
            selectedChipId = activeChip?.catalogId,
        )
    }

    private suspend fun HomeCatalogDefinition.toServerSection(connectionId: String): HomeCatalogSection {
        val target = CatalogTarget.Server(connectionId, catalogId, type)
        val page = ServerCatalog.page(target, skip = 0, limit = HOME_CATALOG_PREVIEW_FETCH_LIMIT)
        return HomeCatalogSection(
            key = key,
            title = defaultTitle,
            subtitle = addonName,
            addonName = addonName,
            target = target,
            items = page.items,
            availableItemCount = page.rawItemCount,
            hasMore = page.nextSkip != null,
        )
    }

    private fun ensureCollectionHeroFallback(
        addons: List<ManagedAddon>,
        forceRefresh: Boolean,
        refreshSources: Boolean,
        requestKey: String?,
    ) {
        if (!lastPublishedCatalogHeroEmpty) return
        val snapshot = HomeCatalogSettingsRepository.snapshot()
        if (!snapshot.heroEnabled) return
        val collections = enabledCollectionsForHero(snapshot)
        if (collections.isEmpty()) {
            cachedCollectionHeroItems = emptyList()
            collectionHeroRequestKey = null
            return
        }

        val nextRequestKey = collectionHeroRequestKey(
            collections = collections,
            addons = addons,
            snapshot = snapshot,
            requestKey = requestKey,
        )
        if (!refreshSources && collectionHeroRequestKey == nextRequestKey) return

        collectionHeroJob?.cancel()
        collectionHeroRequestKey = nextRequestKey
        cachedCollectionHeroItems = emptyList()
        publishCurrentState(
            isLoading = _uiState.value.isLoading,
            requestKey = requestKey,
        )

        collectionHeroJob = scope.launch {
            val sources = collectionHeroSources(collections)
            val sourceResults = sources.map { source ->
                async {
                    runCatching {
                        source.resolveCollectionHeroItems(
                            addons = addons,
                            forceRefresh = forceRefresh,
                        )
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
            val random = Random(HERO_PROCESS_SEED xor COLLECTION_HERO_SEED_OFFSET)
            cachedCollectionHeroItems = roundRobinCollectionHeroItems(sourceResults)
                .filter { item -> item.hasHdHeroImage() }
                .distinctBy { item -> item.stableKey() }
                .shuffled(random)
                .take(HOME_HERO_ITEM_LIMIT)
            publishCurrentState(
                isLoading = _uiState.value.isLoading,
                requestKey = requestKey,
            )
        }
    }

    private fun enabledCollectionsForHero(snapshot: HomeCatalogSettingsSnapshot): List<Collection> {
        val preferences = snapshot.preferences
        return CollectionRepository.collections.value
            .filter { collection ->
                collection.folders.isNotEmpty() &&
                    preferences["collection_${collection.id}"]?.enabled != false
            }
            .sortedBy { collection ->
                preferences["collection_${collection.id}"]?.order ?: Int.MAX_VALUE
            }
    }

    private fun collectionHeroSources(collections: List<Collection>): List<CollectionSource> =
        collections
            .flatMap { collection -> collection.folders }
            .flatMap { folder -> folder.resolvedSources }
            .take(HOME_COLLECTION_HERO_SOURCE_LIMIT)

    private suspend fun CollectionSource.resolveCollectionHeroItems(
        addons: List<ManagedAddon>,
        forceRefresh: Boolean,
    ): List<MetaPreview> {
        val page = when {
            isTmdb -> TmdbCollectionSourceResolver.resolve(source = this, page = 1)
            isTrakt -> TraktPublicListSourceResolver.resolve(source = this, page = 1)
            else -> {
                val catalogSource = addonCatalogSource() ?: return emptyList()
                val resolvedCatalog = addons.findCollectionCatalog(catalogSource) ?: return emptyList()
                fetchCatalogPage(
                    manifestUrl = resolvedCatalog.addon.manifestUrl,
                    type = catalogSource.type,
                    catalogId = catalogSource.catalogId,
                    genre = catalogSource.genre,
                    maxItems = HOME_COLLECTION_HERO_SOURCE_ITEM_LIMIT,
                    forceRefresh = forceRefresh,
                )
            }
        }
        val items = page.items
        return if (HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) {
            items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
        } else {
            items
        }
    }

    private fun roundRobinCollectionHeroItems(sourceResults: List<List<MetaPreview>>): List<MetaPreview> {
        val iterators = sourceResults.filter { it.isNotEmpty() }.map { it.iterator() }
        if (iterators.isEmpty()) return emptyList()
        val merged = mutableListOf<MetaPreview>()
        var hasMore = true
        while (hasMore && merged.size < HOME_COLLECTION_HERO_SOURCE_LIMIT * HOME_COLLECTION_HERO_SOURCE_ITEM_LIMIT) {
            hasMore = false
            iterators.forEach { iterator ->
                if (iterator.hasNext()) {
                    merged.add(iterator.next())
                    hasMore = true
                }
            }
        }
        return merged
    }

    private fun collectionHeroRequestKey(
        collections: List<Collection>,
        addons: List<ManagedAddon>,
        snapshot: HomeCatalogSettingsSnapshot,
        requestKey: String?,
    ): String = buildString {
        append(requestKey.orEmpty())
        append("|hideUnreleased=")
        append(snapshot.hideUnreleasedContent)
        append("|collections=")
        collections.forEach { collection ->
            val preference = snapshot.preferences["collection_${collection.id}"]
            append(collection.id)
            append(":")
            append(preference?.order ?: Int.MAX_VALUE)
            append(":")
            collection.folders.forEach { folder ->
                append(folder.id)
                append("[")
                folder.resolvedSources.forEach { source ->
                    append(collectionSourceKey(source))
                    append(",")
                }
                append("]")
            }
            append(";")
        }
        append("|addons=")
        addons.forEach { addon ->
            append(addon.manifest?.id.orEmpty())
            append(":")
            append(addon.manifestUrl)
            append(":")
            append(addon.manifest?.catalogs?.size ?: 0)
            append(";")
        }
    }

    private fun collectionSourceKey(source: CollectionSource): String =
        source.catalogRouteKey()
}

private const val HOME_HERO_ITEM_LIMIT = 8
/**
 * Seed acak hero untuk satu proses. Dibuat sekali saat kelas dimuat, jadi
 * urutan hero berbeda tiap aplikasi dibuka tetapi stabil selama sesi itu.
 */
private val HERO_PROCESS_SEED: Int = Random.Default.nextInt()
private const val COLLECTION_HERO_SEED_OFFSET = 0x5EED
private const val HOME_COLLECTION_HERO_SOURCE_LIMIT = 6
private const val HOME_COLLECTION_HERO_SOURCE_ITEM_LIMIT = 8
private const val HOME_CATALOG_FETCH_BATCH_SIZE = 4
private const val HOME_CATALOG_PREVIEW_FETCH_LIMIT = 18
private const val HOME_CATALOG_PAGE_SIZE = 20
private const val HOME_CATALOG_PUBLISH_INTERVAL = 2

private fun prioritizeDefinitions(
    definitions: List<HomeCatalogDefinition>,
    snapshot: HomeCatalogSettingsSnapshot,
): List<HomeCatalogDefinition> {
    val orderedDefinitions = definitions.sortedBy { definition ->
        snapshot.preferences[definition.key]?.order ?: Int.MAX_VALUE
    }
    val (priority, remainder) = orderedDefinitions.partition { definition ->
        val preference = snapshot.preferences[definition.key]
        if (preference == null) {
            true
        } else {
            preference.enabled || (snapshot.heroEnabled && preference.heroSourceEnabled)
        }
    }
    return priority + remainder
}
