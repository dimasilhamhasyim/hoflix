package com.nuvio.app.navigation

import platform.Foundation.NSUserDefaults

actual object NavigationRestoreStorage {
    private const val keyPrefix = "nuvio_navigation_restore_"

    private fun keyOf(profileId: Int): String = "$keyPrefix$profileId"

    actual fun load(profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(keyOf(profileId))

    actual fun save(profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = keyOf(profileId))
    }

    actual fun clear(profileId: Int) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(keyOf(profileId))
    }
}
