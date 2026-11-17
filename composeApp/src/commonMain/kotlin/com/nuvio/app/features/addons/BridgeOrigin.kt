package com.nuvio.app.features.addons

/**
 * Asal-usul bridge HOFLIX.
 *
 * Token identitas dan id perangkat hanya boleh dikirim ke bridge. Sebelumnya
 * header itu ditempel ke *setiap* permintaan addon, jadi addon pihak ketiga
 * (sisa migrasi, profil dengan addon sendiri, impor)` menerima token penuh —
 * dan token itu bisa ditukar jadi cookie IDLIX lewat `/hf/auth/idlix-session`.
 */
internal object BridgeOrigin {
    const val HOST = "hoflix-api.siho.my.id"

    /** Host tambahan yang diperlakukan sebagai bridge (dev/uji lokal). */
    private val extraHosts = setOf("10.0.2.2", "127.0.0.1", "localhost")

    private fun hostOf(url: String): String {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return ""
        val authority = afterScheme.substringBefore('/')
        val host = if (authority.startsWith("[")) {
            authority.substringBefore(']').removePrefix("[")
        } else {
            authority.substringBefore(':')
        }
        return host.lowercase()
    }

    fun matches(url: String): Boolean {
        val host = hostOf(url.trim())
        if (host.isEmpty()) return false
        return host == HOST || host.endsWith(".$HOST") || host in extraHosts
    }
}
