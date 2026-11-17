package com.nuvio.app.features.streams

/**
 * Spesifikasi resolve yang dikirim bridge saat IDLIX menolak `/api/watch/...`
 * dari IP server.
 *
 * Yang dibawa hanya alamat dan target — cookie sesi **tidak** ikut di sini.
 * Perangkat mengambil sesinya sendiri lewat `/hf/auth/idlix-session`, jadi
 * sesi user tidak menumpang di payload katalog yang bisa tersimpan di cache.
 */
data class HfxResolveSpec(
    val provider: String = "",
    val kind: String = "movie",
    val targetId: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val playInfoUrl: String = "",
    val apiBase: String = "",
    val webBase: String = "",
    val userAgent: String = "",
    val playlistBase: String = "",
    /**
     * Token play dari bridge. Playlist yang di-resolve di perangkat tidak bisa
     * ditandatangani (kunci HMAC hanya di server), jadi token ini yang membuat
     * `/idlix_pl` mau meneruskannya selama sesi putar masih sah.
     */
    val playToken: String = "",
) {
    val isSupported: Boolean
        get() = provider == "idlix" &&
            playInfoUrl.isNotBlank() &&
            apiBase.isNotBlank() &&
            playlistBase.isNotBlank()
}
