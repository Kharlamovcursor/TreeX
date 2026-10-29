package ru.hdlabs.treex.ui

import android.annotation.SuppressLint
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Embedded [Topola Genealogy Viewer](https://github.com/PeWu/topola-viewer) (Apache-2.0).
 * Bundled under `assets/topola-viewer/`; data via embedded GEDCOM postMessage protocol.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TopolaTreeView(
    gedcom: String,
    reloadToken: Int,
    modifier: Modifier = Modifier,
) {
    key(reloadToken, gedcom.hashCode()) {
        val gedcomState = rememberUpdatedState(gedcom)
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    setBackgroundColor(0xFF0B1220.toInt())
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(
                        TopolaReadyBridge {
                            TopolaEmbeddedBridge.pushGedcom(this, gedcomState.value)
                        },
                        "TreexTopola",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (view != null) {
                                TopolaEmbeddedBridge.installReadyListener(view)
                                TopolaEmbeddedBridge.schedulePush(view) { gedcomState.value }
                            }
                        }
                    }
                    loadUrl(
                        "file:///android_asset/topola-viewer/index.html?embedded=true&standalone=false#/view",
                    )
                }
            },
        )
    }
}

private class TopolaReadyBridge(private val onReady: () -> Unit) {
    @JavascriptInterface
    fun topolaReady() {
        onReady()
    }
}

private object TopolaEmbeddedBridge {
    private const val INSTALL_LISTENER_JS = """
        (function(){
          if (window.__treexTopolaListener) return;
          window.__treexTopolaListener = true;
          window.addEventListener('message', function(ev) {
            if (ev.data && ev.data.message === 'ready' && window.TreexTopola) {
              TreexTopola.topolaReady();
            }
          });
        })();
    """

    fun installReadyListener(webView: WebView) {
        webView.evaluateJavascript(INSTALL_LISTENER_JS, null)
    }

    fun schedulePush(webView: WebView, gedcomProvider: () -> String) {
        val delaysMs = longArrayOf(80, 250, 600, 1200, 2400)
        delaysMs.forEach { delay ->
            webView.postDelayed({
                pushGedcom(webView, gedcomProvider())
            }, delay)
        }
    }

    fun pushGedcom(webView: WebView, gedcom: String) {
        if (gedcom.isBlank()) return
        val payload = Base64.encodeToString(gedcom.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val js = """
            (function(){
              try {
                var gedcom = atob("$payload");
                window.postMessage({message:"parent_ready"}, "*");
                window.postMessage({message:"gedcom", gedcom: gedcom}, "*");
              } catch (e) { console.error(e); }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
