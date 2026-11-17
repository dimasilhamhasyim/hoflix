package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Entri resolve-di-perangkat tidak punya `url` sama sekali, jadi ia hanya
 * selamat sampai daftar stream kalau parser mengenali `hfxResolve` sebagai
 * sumber yang sah. Kunci `clientResolve` sengaja tidak dipakai — itu milik
 * model debrid Nuvio.
 */
class HfxResolveParserTest {

    @Test
    fun `parse keeps device resolve entries without url`() {
        val streams = StreamParser.parse(
            payload =
                """
                {
                  "streams": [
                    {
                      "name": "HOFLIX",
                      "title": "HOFLIX \u00b7 resolve di perangkat",
                      "hfxResolve": {
                        "provider": "idlix",
                        "kind": "episode",
                        "targetId": "uuid-ep-1",
                        "season": 2,
                        "episode": 7,
                        "playInfoUrl": "https://api.example/api/watch/play-info/episode/uuid-ep-1",
                        "apiBase": "https://api.example",
                        "webBase": "https://web.example",
                        "userAgent": "UA",
                        "playlistBase": "https://bridge.example"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            addonName = "HOFLIX",
            addonId = "addon:hoflix",
        )

        assertEquals(1, streams.size)
        val spec = assertNotNull(streams.first().hfxResolve)
        assertEquals("idlix", spec.provider)
        assertEquals("episode", spec.kind)
        assertEquals("uuid-ep-1", spec.targetId)
        assertEquals(2, spec.season)
        assertEquals(7, spec.episode)
        assertEquals("https://bridge.example", spec.playlistBase)
        assertTrue(spec.isSupported)
        assertNull(streams.first().url)
    }

    @Test
    fun `spec without playlist base is not supported`() {
        val streams = StreamParser.parse(
            payload =
                """
                {
                  "streams": [
                    {
                      "name": "HOFLIX",
                      "hfxResolve": {
                        "provider": "idlix",
                        "playInfoUrl": "https://api.example/play-info",
                        "apiBase": "https://api.example"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            addonName = "HOFLIX",
            addonId = "addon:hoflix",
        )

        assertEquals(1, streams.size)
        assertTrue(streams.first().hfxResolve?.isSupported == false)
    }

    @Test
    fun `ordinary streams keep no device resolve spec`() {
        val streams = StreamParser.parse(
            payload =
                """
                {
                  "streams": [
                    { "name": "HOFLIX", "url": "https://cdn.example/master.m3u8" }
                  ]
                }
                """.trimIndent(),
            addonName = "HOFLIX",
            addonId = "addon:hoflix",
        )

        assertEquals(1, streams.size)
        assertNull(streams.first().hfxResolve)
    }
}
