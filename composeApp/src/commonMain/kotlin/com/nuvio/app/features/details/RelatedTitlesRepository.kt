package com.nuvio.app.features.details

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.addonTransportBaseUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.home.HomeCatalogParser
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val HOFLIX_ADDON_ID = "org.hoflix.moviebox"

/**
 * "Yang serupa" rail on the detail page. MovieBox has no related endpoint, so
 * the HOFLIX bridge answers genre-matched titles for the same subject id.
 */
object RelatedTitlesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _items = MutableStateFlow<List<MetaPreview>>(emptyList())
    val items: StateFlow<List<MetaPreview>> = _items.asStateFlow()

    private var job: Job? = null
    private var requestedKey: String? = null

    fun load(type: String, id: String) {
        val key = "$type:$id"
        if (requestedKey == key && (job?.isActive == true || _items.value.isNotEmpty())) return
        val addon: ManagedAddon = AddonRepository.uiState.value.addons.enabledAddons()
            .firstOrNull { it.manifest?.id == HOFLIX_ADDON_ID } ?: run {
            _items.value = emptyList()
            return
        }
        requestedKey = key
        _items.value = emptyList()
        job?.cancel()
        job = scope.launch {
            val url = "${addonTransportBaseUrl(addon.manifestUrl)}/related/$type/$id.json"
            runCatching { fetchAddonResponseText(url) }
                .mapCatching { payload -> HomeCatalogParser.parseCatalogResponse(payload).items }
                .onSuccess { items -> _items.value = items.filterNot { it.id == id } }
                .onFailure { _items.value = emptyList() }
        }
    }

    fun clear() {
        job?.cancel()
        requestedKey = null
        _items.value = emptyList()
    }
}
