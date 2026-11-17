package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.search.SearchHistoryRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches_description
import nuvio.composeapp.generated.resources.settings_content_discovery_section_search
import org.jetbrains.compose.resources.stringResource

/**
 * Konten & Penemuan.
 *
 * Seksi SUMBER (Addon, Media Server, Plugin) sengaja tidak dirender: katalog
 * dan pemutaran sepenuhnya dilayani bridge, addon HOFLIX dipasang otomatis oleh
 * AddonRepository. Menampilkan baris itu hanya membuka jalan buntu.
 */
internal fun LazyListScope.contentDiscoveryContent(
    isTablet: Boolean,
) {
    item {
        val recentSearchesEnabled by remember {
            SearchHistoryRepository.ensureLoaded()
            SearchHistoryRepository.enabled
        }.collectAsStateWithLifecycle()

        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_search),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_content_discovery_recent_searches),
                    description = stringResource(Res.string.settings_content_discovery_recent_searches_description),
                    checked = recentSearchesEnabled,
                    isTablet = isTablet,
                    onCheckedChange = SearchHistoryRepository::setEnabled,
                )
            }
        }
    }
}
