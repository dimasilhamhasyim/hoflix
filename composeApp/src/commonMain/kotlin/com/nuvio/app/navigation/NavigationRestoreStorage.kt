package com.nuvio.app.navigation

/** Durable storage for [NavigationRestoreStore]; scoped per profile. */
internal expect object NavigationRestoreStorage {
    fun load(profileId: Int): String?
    fun save(profileId: Int, payload: String)
    fun clear(profileId: Int)
}
