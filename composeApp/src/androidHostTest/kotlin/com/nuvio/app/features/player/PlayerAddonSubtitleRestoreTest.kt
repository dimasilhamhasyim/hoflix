package com.nuvio.app.features.player

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Plain JVM test (no Robolectric): the restore decision is pure, so it must hold on every host.
 */
class PlayerAddonSubtitleRestoreTest {
    private val indonesian = AddonSubtitle(
        id = "HOFLIX Indonesian",
        url = "https://hoflix.example/sub/123/1/1/id.vtt",
        language = "id",
        display = "Indonesian",
        addonName = "Indonesian",
    )
    private val preference = PersistedPlayerTrackPreference(
        subtitleType = PersistedSubtitleSelectionType.ADDON,
        subtitleLanguage = "id",
        subtitleName = "Indonesian",
        addonSubtitleUrl = indonesian.url,
        addonSubtitleAddonName = "Indonesian",
    )

    @Test
    fun savedTrackAppliesEvenWhileTheProviderRequestIsStillPending() {
        // Stream-level captions already carry the saved track, but the provider request has not even
        // started (providerFetchStartedKey == null). Waiting here is what lost the user's choice.
        val saved = findPersistedAddonSubtitle(listOf(indonesian), preference)

        assertEquals(
            SavedAddonSubtitleRestore.APPLY,
            resolveSavedAddonSubtitleRestore(
                savedSubtitle = saved,
                savedUrl = preference.addonSubtitleUrl,
                savedAddonName = preference.addonSubtitleAddonName,
                isLoadingProviderSubtitles = true,
                providerFetchKey = "series|123|hoflix:https://hoflix.example",
                providerFetchStartedKey = null,
            ),
        )
    }

    @Test
    fun missingTrackWaitsForTheProviderRequestToStart() {
        assertEquals(
            SavedAddonSubtitleRestore.WAIT,
            resolveSavedAddonSubtitleRestore(
                savedSubtitle = null,
                savedUrl = preference.addonSubtitleUrl,
                savedAddonName = preference.addonSubtitleAddonName,
                isLoadingProviderSubtitles = false,
                providerFetchKey = "series|123|hoflix:https://hoflix.example",
                providerFetchStartedKey = null,
            ),
        )
    }

    @Test
    fun missingTrackWaitsWhileTheRequestIsInFlight() {
        val fetchKey = "series|123|hoflix:https://hoflix.example"

        assertEquals(
            SavedAddonSubtitleRestore.WAIT,
            resolveSavedAddonSubtitleRestore(
                savedSubtitle = null,
                savedUrl = preference.addonSubtitleUrl,
                savedAddonName = preference.addonSubtitleAddonName,
                isLoadingProviderSubtitles = true,
                providerFetchKey = fetchKey,
                providerFetchStartedKey = fetchKey,
            ),
        )
    }

    @Test
    fun missingTrackSettlesOnlyAfterTheRequestFinished() {
        val fetchKey = "series|123|hoflix:https://hoflix.example"

        assertEquals(
            SavedAddonSubtitleRestore.NONE,
            resolveSavedAddonSubtitleRestore(
                savedSubtitle = null,
                savedUrl = preference.addonSubtitleUrl,
                savedAddonName = preference.addonSubtitleAddonName,
                isLoadingProviderSubtitles = false,
                providerFetchKey = fetchKey,
                providerFetchStartedKey = fetchKey,
            ),
        )
    }

    @Test
    fun anotherProviderLoadingDoesNotStealTheSavedChoice() {
        val otherProvider = indonesian.copy(
            id = "Other Indonesian",
            url = "https://other.example/id.vtt",
            addonName = "Other",
        )

        assertEquals(
            SavedAddonSubtitleRestore.WAIT,
            resolveSavedAddonSubtitleRestore(
                savedSubtitle = otherProvider,
                savedUrl = preference.addonSubtitleUrl,
                savedAddonName = preference.addonSubtitleAddonName,
                isLoadingProviderSubtitles = true,
                providerFetchKey = "series|123|hoflix:https://hoflix.example",
                providerFetchStartedKey = null,
            ),
        )
    }

    @Test
    fun savedTrackAppliesWithoutAnyProviderRequest() {
        assertEquals(
            SavedAddonSubtitleRestore.APPLY,
            resolveSavedAddonSubtitleRestore(
                savedSubtitle = indonesian,
                savedUrl = preference.addonSubtitleUrl,
                savedAddonName = preference.addonSubtitleAddonName,
                isLoadingProviderSubtitles = false,
                providerFetchKey = null,
                providerFetchStartedKey = null,
            ),
        )
    }

    @Test
    fun savedTrackIsFoundByLanguageWhenTheEpisodeUrlChanges() {
        val nextEpisode = AddonSubtitle(
            id = "HOFLIX Indonesian",
            url = "https://hoflix.example/sub/123/1/2/id.vtt",
            language = "id",
            display = "Indonesian",
            addonName = "Indonesian",
        )

        assertEquals(nextEpisode.url, findPersistedAddonSubtitle(listOf(nextEpisode), preference)?.url)
    }
}
