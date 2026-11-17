package com.nuvio.app.features.appupdate

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Aturan yang menentukan apakah aplikasi berhenti di layar perbarui.
 *
 * Ini satu-satunya keputusan di sisi klien yang bisa mengunci pengguna keluar,
 * jadi ambangnya dipaku di sini: satu angka salah berarti semua orang di versi
 * yang sebenarnya masih sah ikut terblokir.
 */
class AppUpdateConfigTest {
    private val config = AppUpdateConfig(
        latest = "4.7.0",
        latestCode = 4700000,
        minCode = 4600003,
    )

    @Test
    fun blocksVersionsOlderThanMinimum() {
        assertTrue(config.blocks(4600002))
        assertTrue(config.blocks(0))
    }

    @Test
    fun doesNotBlockVersionAtOrAboveMinimum() {
        assertFalse(config.blocks(4600003))
        assertFalse(config.blocks(4700000))
    }

    @Test
    fun zeroMinCodeNeverBlocks() {
        assertFalse(config.copy(minCode = 0).blocks(1))
    }

    @Test
    fun downloadRequiresHttpsAndCode() {
        assertFalse(config.downloadable)
        assertTrue(config.copy(apkUrl = "https://example.test/app.apk").downloadable)
        assertFalse(config.copy(apkUrl = "http://example.test/app.apk").downloadable)
        assertFalse(config.copy(apkUrl = "https://example.test/app.apk", latestCode = 0).downloadable)
    }
}
