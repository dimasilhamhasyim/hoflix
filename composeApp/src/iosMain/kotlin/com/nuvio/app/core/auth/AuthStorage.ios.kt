package com.nuvio.app.core.auth

import platform.Foundation.NSUserDefaults

actual object AuthStorage {
    private const val KEY_ANONYMOUS_USER_ID = "anonymous_user_id"
    private const val KEY_IDENTITY_TOKEN = "identity_token"

    actual fun loadAnonymousUserId(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(KEY_ANONYMOUS_USER_ID)

    actual fun saveAnonymousUserId(userId: String) {
        NSUserDefaults.standardUserDefaults.setObject(userId, forKey = KEY_ANONYMOUS_USER_ID)
    }

    actual fun clearAnonymousUserId() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(KEY_ANONYMOUS_USER_ID)
    }

    actual fun loadFlag(key: String): Boolean =
        NSUserDefaults.standardUserDefaults.boolForKey(key)

    actual fun saveFlag(key: String, value: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(value, forKey = key)
    }

    actual fun loadText(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(key)

    actual fun saveText(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }

    actual fun clearText(key: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
    }

    actual fun loadIdentityToken(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(KEY_IDENTITY_TOKEN)

    actual fun saveIdentityToken(token: String) {
        NSUserDefaults.standardUserDefaults.setObject(token, forKey = KEY_IDENTITY_TOKEN)
    }

    actual fun clearIdentityToken() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(KEY_IDENTITY_TOKEN)
    }
}
