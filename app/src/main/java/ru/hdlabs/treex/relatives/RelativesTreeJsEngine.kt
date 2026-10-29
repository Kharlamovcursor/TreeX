package ru.hdlabs.treex.relatives

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Runs relatives-tree [calcTree] in a hidden WebView (bundled [calc-tree.js], MIT).
 * Layout only — rendering is Compose ([RelativesTreeView]).
 */
@SuppressLint("SetJavaScriptEnabled")
class RelativesTreeJsEngine(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val ready = CompletableDeferred<Unit>()
    private val webView = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!ready.isCompleted) ready.complete(Unit)
            }
        }
        loadUrl("file:///android_asset/genealogy-tree/engine.html")
    }

    suspend fun calcTree(nodes: List<RelativesGraphNode>, rootId: String): RelativesTreeData =
        withContext(Dispatchers.Main) {
            ready.await()
            val payload = Base64.encodeToString(
                json.encodeToString(nodes).toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
            val safeRoot = rootId.replace("\\", "\\\\").replace("\"", "\\\"")
            val raw = CompletableDeferred<String>()
            webView.post {
                webView.evaluateJavascript("runTree(\"$payload\", \"$safeRoot\")") { value ->
                    raw.complete(value ?: "null")
                }
            }
            json.decodeFromString(unescapeJsString(raw.await()))
        }

    private fun unescapeJsString(value: String): String {
        if (value == "null" || value.isBlank()) return "{}"
        return if (value.startsWith("\"")) {
            Json.parseToJsonElement(value).jsonPrimitive.content
        } else {
            value
        }
    }
}
