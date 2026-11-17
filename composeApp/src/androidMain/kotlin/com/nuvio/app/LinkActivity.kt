package com.nuvio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.nuvio.app.features.settings.AppIconPlatform

class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // exported=true membuat aktivitas ini bisa dipanggil aplikasi/situs mana
        // pun. Tanpa validasi, tautan sembarang diteruskan mentah ke aplikasi.
        // Yang diterima hanya skema milik HOFLIX, atau https ke host bridge.
        intent?.dataString?.takeIf(::isAllowedIncomingLink)?.let { link ->
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = AppIconPlatform.currentLauncherComponent(this@LinkActivity)
                    putExtra(EXTRA_LINK, link)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_LINK = "link"

        private val customSchemes = setOf("hoflix", "nuvio", "stremio")
        private val httpsHosts = setOf("hoflix-api.siho.my.id")

        internal fun isAllowedIncomingLink(link: String): Boolean {
            if (link.isEmpty() || link.length > 2048) return false
            val scheme = link.substringBefore("://", "").lowercase()
            if (scheme.isEmpty()) return false
            if (scheme in customSchemes) return link.length > scheme.length + 3
            if (scheme != "https") return false
            val authority = link.substringAfter("://").substringBefore('/')
            val host = authority.substringBefore(':').substringBefore('?').lowercase()
            return host in httpsHosts
        }
    }
}
