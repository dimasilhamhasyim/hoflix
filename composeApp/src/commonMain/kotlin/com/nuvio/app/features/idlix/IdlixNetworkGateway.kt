package com.nuvio.app.features.idlix

import com.nuvio.app.core.network.BridgeIdentity
import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.features.auth.AccountGateway
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Grid satu jaringan IDLIX (Netflix, HBO, tvN, …).
 *
 * Rail Network Originals di Beranda berisi kartu `network:<slug>`; kartu itu
 * membuka layar ini. Isinya berasal dari indeks network di bridge — API daftar
 * IDLIX sendiri tidak punya filter network dan halaman webnya mentok 15 judul,
 * jadi paging nyata hanya bisa dari indeks itu.
 */
internal object IdlixNetworkGateway {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy { createApiHttpClient() }

    @Serializable
    data class NetworkPage(
        val slug: String = "",
        val title: String = "",
        val total: Int = 0,
        val logo: String = "",
        val hasMore: Boolean = false,
        val metas: List<IdlixCollectionsGateway.IdlixMeta> = emptyList(),
    )

    suspend fun page(slug: String, skip: Int): Result<NetworkPage> = runCatching {
        val response = http.get("${AccountGateway.BASE_URL}/networks/$slug.json?skip=$skip") {
            header(HttpHeaders.UserAgent, "HOFLIX")
            // Gate keanggotaan menolak permintaan tanpa sesi; header ini yang
            // membuat bridge tahu siapa yang meminta.
            BridgeIdentity.headers().forEach { (name, value) -> header(name, value) }
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            error("Sesi berakhir. Masuk ulang untuk melanjutkan.")
        }
        if (response.status == HttpStatusCode.PaymentRequired) {
            error("Butuh keanggotaan aktif.")
        }
        if (!response.status.isSuccess()) error("network ${response.status.value}")
        json.decodeFromString<NetworkPage>(response.bodyAsText())
    }
}
