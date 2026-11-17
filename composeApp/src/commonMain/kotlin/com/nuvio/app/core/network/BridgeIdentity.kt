package com.nuvio.app.core.network

import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.sync.SyncClientIdentity

/**
 * Header identitas untuk permintaan ke bridge HOFLIX.
 *
 * Sejak gate keanggotaan dipasang, endpoint isi katalog (`/catalog/`, `/meta/`,
 * `/stream/`, `/collections/`, `/networks/`, `/new.json`, …) menolak permintaan
 * tanpa sesi — `401 identity_required` atau `402 membership_required`. Klien
 * yang lupa menempelkan token tidak mendapat apa pun, dan di layar terlihat
 * sebagai "401" mentah.
 *
 * Hanya untuk host bridge. Token ini bisa ditukar jadi cookie IDLIX di bridge,
 * jadi addon pihak ketiga tidak boleh menerimanya — lihat [BridgeOrigin].
 */
internal object BridgeIdentity {
    const val DeviceHeader: String = "X-HF-Device"

    /** Kode versi aplikasi; dipakai bridge untuk menolak versi kedaluwarsa. */
    const val VersionHeader: String = "X-HF-App-Version"

    /** `Authorization` + [DeviceHeader] + [VersionHeader]; kosong kalau tanpa sesi. */
    fun headers(): Map<String, String> = buildMap {
        AuthStorage.loadIdentityToken()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { put("Authorization", "Bearer $it") }
        runCatching { SyncClientIdentity.currentClientId() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { put(DeviceHeader, it) }
        AppVersionConfig.VERSION_CODE.takeIf { it > 0 }?.let { code ->
            put(VersionHeader, code.toString())
        }
    }
}
