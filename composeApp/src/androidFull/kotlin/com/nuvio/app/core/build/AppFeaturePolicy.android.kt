package com.nuvio.app.core.build

actual object AppFeaturePolicy {
    actual val pluginsEnabled: Boolean = true
    actual val supportersContributorsPageEnabled: Boolean = false
    actual val donationActionsEnabled: Boolean = false
    actual val donationProgressEnabled: Boolean = false
    actual val accountDeletionEnabled: Boolean = false
    actual val personalMediaAddonCopyEnabled: Boolean = false
    // P2P/torrent dimatikan: pada produk berlangganan, swarm mengekspos IP
    // pengguna dan tracker cleartext ikut terbawa di manifest.
    actual val p2pEnabled: Boolean = false
    actual val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
    actual val heroTrailerPlaybackSupported: Boolean = true
    // Updater dimatikan: sumber rilisnya repo pihak lain (NuvioMedia) dan
    // pemasangannya tidak memeriksa tanda tangan. Satu tag palsu di sana =
    // aplikasi menawarkan memasang APK asing ke perangkat pengguna.
    actual val inAppUpdaterEnabled: Boolean = false
    actual val imdbRatingLogoEnabled: Boolean = true
    actual val mediaPlaybackForegroundServiceEnabled: Boolean = true
    actual val downloadForegroundServiceEnabled: Boolean = true
    actual val customServerConnectionsEnabled: Boolean = false
}
