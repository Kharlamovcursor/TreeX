package ru.hdlabs.treex.relatives

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

/**
 * Runs relatives-tree calcTree in a hidden WebView (must be [attach]ed to an Activity window).
 */
@SuppressLint("SetJavaScriptEnabled")
class RelativesTreeJsEngine {
    private val json = Json { ignoreUnknownKeys = true }
    private var appContext: Context? = null
    private var webView: WebView? = null
    private val pageReady = CompletableDeferred<Unit>()

    val isAttached: Boolean get() = webView?.parent != null

    fun attach(activity: ComponentActivity) {
        if (webView?.parent != null) return
        appContext = activity.applicationContext
        val view = WebView(activity).apply {
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(0, 0)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!pageReady.isCompleted) pageReady.complete(Unit)
                }
            }
            loadUrl("file:///android_asset/genealogy-tree/engine.html")
        }
        (activity.window.decorView as ViewGroup).addView(view)
        webView = view
    }

    suspend fun calcTree(nodes: List<RelativesGraphNode>, rootId: String): RelativesTreeData {
        val view = webView ?: throw IllegalStateException("RelativesTreeJsEngine not attached")
        if (!isAttached) throw IllegalStateException("RelativesTree WebView not in window")

        return withTimeout(CALC_TIMEOUT_MS) {
            if (!pageReady.isCompleted) {
                withContext(Dispatchers.Main) { pageReady.await() }
            }
            val payload = Base64.encodeToString(
                json.encodeToString(nodes).toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
            val safeRoot = rootId.replace("\\", "\\\\").replace("\"", "\\\"")
            val script = "runTree(\"$payload\", \"$safeRoot\")"
            val raw = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    view.evaluateJavascript(script) { value ->
                        if (cont.isActive) cont.resume(value)
                    }
                }
            }
            withContext(Dispatchers.Default) {
                json.decodeFromString<RelativesTreeData>(unescapeJsString(raw ?: "null"))
            }
        }
    }

    private fun unescapeJsString(value: String): String {
        if (value == "null" || value.isBlank()) return "{}"
        return if (value.startsWith("\"")) {
            Json.parseToJsonElement(value).jsonPrimitive.content
        } else {
            value
        }
    }

    companion object {
        private const val CALC_TIMEOUT_MS = 8_000L
    }
}
