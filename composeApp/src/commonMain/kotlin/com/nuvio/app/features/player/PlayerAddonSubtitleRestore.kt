package com.nuvio.app.features.player

/**
 * What the player should do with a subtitle the user picked earlier for this content.
 *
 * The saved track is per content id, so it survives leaving and re-entering the player. Restoring it
 * must not depend on a provider request: the player already receives captions on the stream object,
 * and a provider round-trip can be slow, fail, or never start at all. Only a list that has truly
 * settled without the saved track may count as "this title has no such subtitle".
 */
internal enum class SavedAddonSubtitleRestore {
    /** The saved track is in the current list and can be attached now. */
    APPLY,

    /** The list can still change (provider fetch pending or in flight); keep waiting. */
    WAIT,

    /** The list settled without the saved track; fall back to the automatic pick. */
    NONE,
}

internal fun resolveSavedAddonSubtitleRestore(
    savedSubtitle: AddonSubtitle?,
    savedUrl: String?,
    savedAddonName: String?,
    isLoadingProviderSubtitles: Boolean,
    providerFetchKey: String?,
    providerFetchStartedKey: String?,
): SavedAddonSubtitleRestore {
    if (savedSubtitle != null) {
        val sameTrack = savedUrl != null && savedSubtitle.url == savedUrl
        val sameProvider = savedAddonName.isNullOrBlank() ||
            savedSubtitle.addonName.equals(savedAddonName, ignoreCase = true)
        if (isLoadingProviderSubtitles && !sameTrack && !sameProvider) return SavedAddonSubtitleRestore.WAIT
        return SavedAddonSubtitleRestore.APPLY
    }

    val fetchSettled = providerFetchKey == null ||
        (providerFetchStartedKey == providerFetchKey && !isLoadingProviderSubtitles)
    return if (fetchSettled) SavedAddonSubtitleRestore.NONE else SavedAddonSubtitleRestore.WAIT
}
