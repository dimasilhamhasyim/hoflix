package com.nuvio.app.features.addons

import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.auth.currentDeviceClientMetadata
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.sync.SyncClientIdentity
import com.nuvio.app.features.membership.DeviceLimitNotice
import com.nuvio.app.features.appupdate.AppUpdateController

internal suspend fun fetchAddonResponseText(
    url: String,
    forceRefresh: Boolean = false,
): String {
    // The bridge localises the catalog from this header: titles, genres,
    // country names and rail labels follow the app language, like MovieBox
    // follows its own language setting.
    // Header ini hanya untuk bridge. Addon pihak ketiga tidak boleh menerima
    // token identitas: token itu bisa ditukar jadi cookie IDLIX di bridge.
    val bridged = BridgeOrigin.matches(url)
    val headers = buildMap {
        put("Accept-Language", currentAddonLanguage())
        if (forceRefresh) put("Cache-Control", "no-cache")
        if (bridged) {
            // Token sesi IDLIX yang dipegang bridge. Tanpa ini bridge tidak tahu
            // siapa yang memutar dan setiap play jatuh ke tier tamu (720).
            AuthStorage.loadIdentityToken()?.takeIf { it.isNotBlank() }?.let { token ->
                put("Authorization", "Bearer $token")
            }
            // Id pemasangan ini yang dipakai bridge menghitung slot perangkat (maks 4).
            runCatching { SyncClientIdentity.currentClientId() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { installation -> put("X-HF-Device", installation) }
            // Nama perangkat dipakai daftar "Perangkat" di layar keanggotaan.
            runCatching { currentDeviceClientMetadata() }.getOrNull()?.let { metadata ->
                metadata.deviceName.takeIf { it.isNotBlank() }?.let { put("X-HF-Device-Name", it) }
                metadata.platform.takeIf { it.isNotBlank() }?.let { put("X-HF-Platform", it) }
            }
            // Bridge menolak versi di bawah `minCode` dengan 426. Tanpa header
            // ini, versi lama hanya menerima galat generik dan pengguna tidak
            // tahu bahwa yang dibutuhkan adalah pembaruan.
            AppVersionConfig.VERSION_CODE.takeIf { it > 0 }?.let { code ->
                put("X-HF-App-Version", code.toString())
            }
        }
    }
    return try {
        httpGetTextWithHeaders(
            url = url,
            headers = headers,
        )
    } catch (error: Throwable) {
        // 409 hanya datang dari bridge, dan artinya slot perangkat penuh.
        // Tanpa pemberitahuan, pemakai hanya melihat "sumber tidak ditemukan".
        if (bridged && error.message?.contains("409") == true) {
            runCatching { DeviceLimitNotice.probeAndShow() }
        }
        // 426 = bridge menolak versi ini. Pemeriksaan saat start bisa
        // ketinggalan (cache masih segar, atau jaringan tadi gagal), jadi
        // penolakan server sendiri yang menutup aplikasi.
        if (bridged && error.message?.contains("426") == true) {
            runCatching { AppUpdateController.reportServerRejection() }
        }
        throw error
    }
}
