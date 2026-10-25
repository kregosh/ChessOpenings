package com.kregosh.chessopenings.web

import com.kregosh.chessopenings.core.engine.Engine
import com.kregosh.chessopenings.core.engine.QueuedUciEngine
import kotlinx.coroutines.CoroutineScope

private fun newWorker(url: String): JsAny = js("new Worker(url)")
private fun postMessage(worker: JsAny, message: String): Unit = js("worker.postMessage(message)")
private fun onMessage(worker: JsAny, handler: (String) -> Unit): Unit = js("worker.onmessage = function (e) { handler(String(e.data)); }")
private fun onError(worker: JsAny, handler: (String) -> Unit): Unit = js("worker.onerror = function (e) { handler(String(e.message || e)); }")

/** Stockfish (the WASM build of Stockfish.js) in a Web Worker, spoken to over UCI. */
class WebEngine private constructor(private val uci: QueuedUciEngine) : Engine by uci {
    companion object {
        operator fun invoke(scriptUrl: String, scope: CoroutineScope): WebEngine {
            val worker = newWorker(scriptUrl)
            val uci = QueuedUciEngine(scope) { postMessage(worker, it) }
            onMessage(worker, uci::lineReceived)
            onError(worker, uci::failed)
            return WebEngine(uci)
        }
    }
}
