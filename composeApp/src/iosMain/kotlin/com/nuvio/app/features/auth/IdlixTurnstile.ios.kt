package com.nuvio.app.features.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * Build iOS tidak memuat WebView untuk widget ini; statusnya dilaporkan
 * `unavailable` supaya form menampilkan jalur pendaftaran lewat browser.
 */
@Composable
internal actual fun IdlixTurnstileBox(
    url: String,
    modifier: Modifier,
    onMessage: (TurnstileMessage) -> Unit,
) {
    val latest = rememberUpdatedState(onMessage)
    LaunchedEffect(url) {
        latest.value(TurnstileMessage(type = "unavailable"))
    }
}
