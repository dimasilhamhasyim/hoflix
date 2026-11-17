package com.nuvio.app.features.notifications

internal expect object PremiereReminderStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
