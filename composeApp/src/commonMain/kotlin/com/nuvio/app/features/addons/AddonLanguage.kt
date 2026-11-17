package com.nuvio.app.features.addons

import com.nuvio.app.features.catalog.CatalogRepository
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.HomeHeroMetaRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.player.DeviceLanguagePreferences
import com.nuvio.app.features.search.BrowseRepository
import com.nuvio.app.features.search.SearchRepository
import com.nuvio.app.features.settings.AppLanguage
import com.nuvio.app.features.settings.ThemeSettingsRepository

/**
 * Catalog language asked of the HOFLIX addon.
 *
 * The bridge localises titles, genres, country names and rail labels from this
 * header, exactly like MovieBox follows its own language setting. Only
 * Indonesian and English are wired; every other app language reads the English
 * catalog while the interface keeps its own language.
 */
internal fun currentAddonLanguage(): String {
    val selected = ThemeSettingsRepository.selectedAppLanguage.value
    val code = if (selected == AppLanguage.DEVICE) {
        DeviceLanguagePreferences.preferredLanguageCodes().firstOrNull() ?: "en"
    } else {
        selected.code
    }
    val primary = code.substringBefore('-').lowercase()
    // "in" is the legacy ISO code Android still reports for Indonesian.
    return if (primary == "id" || primary == "in") "id" else "en"
}

/**
 * Drops every catalog fetched in the previous language and reloads the home
 * rails, so a language switch takes effect without restarting the app.
 */
internal fun reloadCatalogForLanguageChange() {
    clearAddonHttpCache()
    MetaDetailsRepository.clear()
    CatalogRepository.clear()
    HomeRepository.clear()
    SearchRepository.clear()
    BrowseRepository.clearLanguageState()
    HomeHeroMetaRepository.clear()
    AddonRepository.refreshAll()
    HomeRepository.refresh(AddonRepository.uiState.value.addons.enabledAddons())
}
