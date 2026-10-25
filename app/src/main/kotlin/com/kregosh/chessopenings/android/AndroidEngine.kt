package com.kregosh.chessopenings.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.kregosh.chessopenings.core.engine.Engine
import com.kregosh.chessopenings.core.engine.EngineLine
import com.kregosh.chessopenings.core.engine.QueuedUciEngine
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

/**
 * Stockfish on Android: the same WASM build as the web app (Stockfish.js lite, single
 * threaded), running in a Web Worker inside a hidden [WebView]. The engine files are
 * served from the app's assets under an https origin, so the worker and its .wasm load
 * like on the web. Create it on the main thread.
 */
@SuppressLint("SetJavaScriptEnabled")
class AndroidEngine(context: Context, scope: CoroutineScope) : Engine {
    private val main = Handler(Looper.getMainLooper())
    private val uci = QueuedUciEngine(scope, ::send)

    // Main thread only: commands sent before the page has loaded wait here.
    private var ready = false
    private val pending = ArrayList<String>()

    private val assets = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    /** The engine's WebView. It shows nothing; activities attach it so it keeps running. */
    val view: WebView = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        addJavascriptInterface(Bridge(), "Engine")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) uci.failed("Couldn't load the engine page: ${error.description}")
            }

            // Android may stop the WebView's renderer (e.g. low on memory). The engine is gone
            // then; the app reports it instead of crashing.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                uci.failed(if (detail.didCrash()) "The engine crashed" else "The engine was stopped to free memory")
                return true
            }
        }
        loadUrl(PAGE)
    }

    override suspend fun analyze(fen: String, depth: Int, searchMoves: List<String>, multiPv: Int): List<EngineLine> =
        uci.analyze(fen, depth, searchMoves, multiPv)

    private fun send(command: String) {
        main.post {
            if (ready) evaluate(command) else pending += command
        }
    }

    private fun evaluate(command: String) = view.evaluateJavascript("send(${JSONObject.quote(command)})", null)

    /** Called by the engine page, on a WebView background thread. */
    inner class Bridge {
        @JavascriptInterface
        fun onLine(line: String) = uci.lineReceived(line)

        @JavascriptInterface
        fun onError(message: String) = uci.failed(message)

        @JavascriptInterface
        fun onReady() {
            main.post {
                ready = true
                pending.forEach(::evaluate)
                pending.clear()
            }
        }
    }

    private companion object {
        const val PAGE = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/engine/engine.html"
    }
}
