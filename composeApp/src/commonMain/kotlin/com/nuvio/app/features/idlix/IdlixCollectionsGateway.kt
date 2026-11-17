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
 * Koleksi IDLIX (universe, waralaba, era kreator, studio).
 *
 * Koleksi sengaja tidak dipasang sebagai katalog manifest: 66 katalog akan
 * muncul sebagai 66 rail di Beranda. Layar ini yang membacanya, lalu membuka
 * detail judul lewat id `idlix:<slug>` yang sudah dikenal aplikasi.
 */
internal object IdlixCollectionsGateway {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy { createApiHttpClient() }

    @Serializable
    data class IdlixCollection(
        val slug: String = "",
        val title: String = "",
        val kind: String = "",
        val kindLabel: String = "",
        val description: String = "",
        val itemCount: Int = 0,
        val childCount: Int = 0,
        val poster: String = "",
        val backdrop: String = "",
    )

    @Serializable
    private data class CollectionsPayload(val collections: List<IdlixCollection> = emptyList())

    @Serializable
    data class IdlixMeta(
        val id: String = "",
        val type: String = "movie",
        val name: String = "",
        val poster: String = "",
        val background: String = "",
        val description: String = "",
        val releaseInfo: String = "",
        val imdbRating: String = "",
        val genres: List<String> = emptyList(),
    )

    @Serializable
    data class IdlixSection(
        val title: String = "",
        val description: String = "",
        val items: List<IdlixMeta> = emptyList(),
    )

    @Serializable
    data class IdlixCollectionDetail(
        val slug: String = "",
        val title: String = "",
        val description: String = "",
        val itemCount: Int = 0,
        val poster: String = "",
        val backdrop: String = "",
        val sections: List<IdlixSection> = emptyList(),
        val metas: List<IdlixMeta> = emptyList(),
    )

    suspend fun collections(): Result<List<IdlixCollection>> = runCatching {
        val response = http.get("${AccountGateway.BASE_URL}/collections.json") {
            header(HttpHeaders.UserAgent, "HOFLIX")
            BridgeIdentity.headers().forEach { (name, value) -> header(name, value) }
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            error("Sesi berakhir. Masuk ulang untuk melanjutkan.")
        }
        if (response.status == HttpStatusCode.PaymentRequired) {
            error("Butuh keanggotaan aktif.")
        }
        if (!response.status.isSuccess()) error("collections ${response.status.value}")
        json.decodeFromString<CollectionsPayload>(response.bodyAsText()).collections
            .filter { it.slug.isNotBlank() && it.title.isNotBlank() }
    }

    suspend fun collection(slug: String): Result<IdlixCollectionDetail> = runCatching {
        val response = http.get("${AccountGateway.BASE_URL}/collections/$slug.json") {
            header(HttpHeaders.UserAgent, "HOFLIX")
            BridgeIdentity.headers().forEach { (name, value) -> header(name, value) }
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            error("Sesi berakhir. Masuk ulang untuk melanjutkan.")
        }
        if (response.status == HttpStatusCode.PaymentRequired) {
            error("Butuh keanggotaan aktif.")
        }
        if (!response.status.isSuccess()) error("collection ${response.status.value}")
        json.decodeFromString<IdlixCollectionDetail>(response.bodyAsText())
    }
}
