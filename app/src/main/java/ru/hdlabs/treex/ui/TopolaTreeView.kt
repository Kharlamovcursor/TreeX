package ru.hdlabs.treex.ui

import android.annotation.SuppressLint
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
    key(gedcom.hashCode(), reloadToken) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    setBackgroundColor(0xFF0B1220.toInt())
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (view != null) {
                                view.postDelayed({ TopolaEmbeddedBridge.pushGedcom(view, gedcom) }, 120)
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

private object TopolaEmbeddedBridge {
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
