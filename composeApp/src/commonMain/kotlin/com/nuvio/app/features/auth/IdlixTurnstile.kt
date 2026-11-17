package com.nuvio.app.features.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pesan dari halaman verifikasi IDLIX (`window.IdlixTurnstile.postMessage`).
 *
 * [type] salah satu: `ready`, `token`, `expired`, `unavailable`, `disabled`,
 * `error`. [token] hanya terisi untuk `token`, [height] hanya untuk `ready`.
 */
internal data class TurnstileMessage(
    val type: String,
    val token: String = "",
    val height: Int = 0,
    val code: String = "",
)

/**
 * Widget verifikasi Cloudflare Turnstile milik IDLIX, dirender di dalam form
 * pendaftaran supaya user tidak perlu keluar aplikasi.
 */
@Composable
internal expect fun IdlixTurnstileBox(
    url: String,
    modifier: Modifier,
    onMessage: (TurnstileMessage) -> Unit,
)
