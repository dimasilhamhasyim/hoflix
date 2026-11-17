package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Token identitas dan id perangkat hanya boleh dikirim ke bridge. Tes ini
 * mengunci batas itu supaya addon pihak ketiga tidak diam-diam menerima token.
 */
class BridgeOriginTest {
    @Test
    fun matchesBridgeHost() {
        assertTrue(BridgeOrigin.matches("https://hoflix-api.siho.my.id/manifest.json"))
        assertTrue(BridgeOrigin.matches("https://hoflix-api.siho.my.id:443/catalog/movie/x.json"))
        assertTrue(BridgeOrigin.matches("https://cdn.hoflix-api.siho.my.id/x"))
    }

    @Test
    fun rejectsForeignHosts() {
        assertFalse(BridgeOrigin.matches("https://catalog.nuvio.tv/manifest.json"))
        assertFalse(BridgeOrigin.matches("https://evil.example.com/hoflix-api.siho.my.id"))
        assertFalse(BridgeOrigin.matches("https://hoflix-api.siho.my.id.evil.com/x"))
        assertFalse(BridgeOrigin.matches(""))
    }

    @Test
    fun allowsLoopbackForDevelopment() {
        assertTrue(BridgeOrigin.matches("http://10.0.2.2:8799/stream/movie/x.json"))
        assertTrue(BridgeOrigin.matches("http://127.0.0.1:8799/health"))
    }
}
