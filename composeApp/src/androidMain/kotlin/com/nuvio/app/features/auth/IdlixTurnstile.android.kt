package com.nuvio.app.features.auth

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Halaman verifikasi memanggil jembatan ini lewat nama tetap `IdlixTurnstile`. */
private const val TurnstileBridgeName = "IdlixTurnstile"

/**
 * UA Chrome Android. Turnstile menolak WebView default (`; wv`), sementara
 * halaman IDLIX memang dirancang untuk aplikasi mereka.
 */
private const val TurnstileUserAgent =
    "Mozilla/5.0 (Linux; Android 14; V2309A) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Mobile Safari/537.36"

private val turnstileJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Hanya host ini yang boleh dimuat di WebView Turnstile.
 *
 * Halaman tantangan diambil dari API IDLIX (dan kadang lewat bridge kalau
 * pemuatan langsung gagal). Tanpa daftar ini, halaman mana pun yang berhasil
 * membujuk user mengetuk tautan akan berjalan di WebView yang punya jembatan
 * JS kita.
 */
private val TurnstileHosts = setOf(
    "api.idlixku.com",
    "z2.idlixku.com",
    "hoflix-api.siho.my.id",
    "challenges.cloudflare.com",
)

private fun turnstileHostOf(url: String): String {
    val afterScheme = url.substringAfter("://", "")
    if (afterScheme.isEmpty()) return ""
    return afterScheme.substringBefore('/').substringBefore(':').lowercase()
}

private fun isAllowedTurnstileUrl(url: String): Boolean {
    val scheme = url.substringBefore("://", "").lowercase()
    if (scheme != "https") return false
    val host = turnstileHostOf(url)
    return TurnstileHosts.any { host == it || host.endsWith(".$it") }
}

/** Token Turnstile: base64url panjang, tidak pernah berisi HTML. */
private fun plausibleTurnstileToken(token: String): Boolean {
    if (token.isEmpty() || token.length > 4096) return false
    return token.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == ':' || it == '~' }
}

private const val MaxBridgePayload = 8 * 1024

private fun parseTurnstileMessage(payload: String): TurnstileMessage? = runCatching {
    if (payload.length > MaxBridgePayload) return null
    val obj = turnstileJson.parseToJsonElement(payload) as? JsonObject ?: return null
    val type = obj["type"]?.jsonPrimitive?.content ?: return null
    if (type !in setOf("ready", "token", "expired", "error", "unavailable", "disabled")) return null
    val token = obj["token"]?.jsonPrimitive?.content.orEmpty()
    if (type == "token" && !plausibleTurnstileToken(token)) return null
    TurnstileMessage(
        type = type,
        token = token,
        height = obj["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        code = obj["code"]?.jsonPrimitive?.content.orEmpty().take(64),
    )
}.getOrNull()

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
internal actual fun IdlixTurnstileBox(
    url: String,
    modifier: Modifier,
    onMessage: (TurnstileMessage) -> Unit,
) {
    val latest = rememberUpdatedState(onMessage)
    var boxHeight by remember { mutableStateOf(78.dp) }
    // Holder biasa, bukan state: menulis state di dalam `factory` terjadi saat
    // komposisi dan bisa memicu apply ganda.
    val holder = remember { arrayOfNulls<WebView>(1) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(boxHeight),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                // Tanpa software layer, kotak WebView menimpa field di atasnya
                // saat form digulir.
                setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mediaPlaybackRequiresUserGesture = false
                    // Isi halaman campuran (http di dalam https) tidak boleh
                    // dimuat: satu skrip http bisa membajak jembatan JS.
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    allowFileAccess = false
                    allowContentAccess = false
                    userAgentString = TurnstileUserAgent
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = !isAllowedTurnstileUrl(request.url.toString())

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                        !isAllowedTurnstileUrl(url)

                    /**
                     * Halaman tantangan bisa gagal dimuat sama sekali (DNS router
                     * memblokir api.idlixku.com, jaringan mati, Cloudflare menolak).
                     * Dulu tidak ada penanganan: WebView berhenti di halaman error,
                     * JS tidak pernah jalan, jadi `unavailable` tidak pernah dikirim
                     * dan tombol Daftar mati tanpa penjelasan.
                     */
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (!request.isForMainFrame) return
                        latest.value(
                            TurnstileMessage(
                                type = "unavailable",
                                code = "load_failed_${error.errorCode}",
                            ),
                        )
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (!request.isForMainFrame) return
                        latest.value(
                            TurnstileMessage(
                                type = "error",
                                code = "http_${errorResponse.statusCode}",
                            ),
                        )
                    }
                }
                addJavascriptInterface(
                    TurnstileJsBridge { payload ->
                        val message = parseTurnstileMessage(payload) ?: return@TurnstileJsBridge
                        if (message.type == "ready" && message.height in 60..320) {
                            boxHeight = message.height.dp
                        }
                        latest.value(message)
                    },
                    TurnstileBridgeName,
                )
                loadUrl(url)
                holder[0] = this
            }
        },
        update = { view ->
            if (view.url != url) view.loadUrl(url)
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.apply {
                removeJavascriptInterface(TurnstileBridgeName)
                stopLoading()
                destroy()
            }
            holder[0] = null
        }
    }
}

/** Jembatan JS → Compose; wajib di thread UI karena WebView memanggilnya di sana. */
private class TurnstileJsBridge(
    private val onPayload: (String) -> Unit,
) {
    @JavascriptInterface
    fun postMessage(payload: String) {
        onPayload(payload)
    }
}
