package com.nuvio.app.navigation

import android.content.Context
import android.content.SharedPreferences

actual object NavigationRestoreStorage {
    private const val preferencesName = "nuvio_navigation_restore"
    private const val keyPrefix = "navigation_restore_"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    private fun keyOf(profileId: Int): String = "$keyPrefix$profileId"

    actual fun load(profileId: Int): String? =
        preferences?.getString(keyOf(profileId), null)

    actual fun save(profileId: Int, payload: String) {
        // apply() writes to memory immediately and flushes on the next
        // handleStopActivity(), which is exactly the window we care about:
        // the process is only killed after the app stopped.
        preferences?.edit()?.putString(keyOf(profileId), payload)?.apply()
    }

    actual fun clear(profileId: Int) {
        preferences?.edit()?.remove(keyOf(profileId))?.apply()
    }
}
