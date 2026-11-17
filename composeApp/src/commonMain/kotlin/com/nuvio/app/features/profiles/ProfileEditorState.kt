package com.nuvio.app.features.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-app profile editor route. The upstream app only reaches [ProfileEditScreen]
 * from the login gate, so the profile menu had no way to change an avatar from
 * inside the app.
 */
object ProfileEditorState {
    private val _request = MutableStateFlow<NuvioProfile?>(null)
    val request: StateFlow<NuvioProfile?> = _request.asStateFlow()

    fun open(profile: NuvioProfile) {
        _request.value = profile
    }

    fun close() {
        _request.value = null
    }
}
