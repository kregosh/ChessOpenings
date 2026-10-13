package com.kregosh.chessopenings.web

import com.kregosh.chessopenings.core.engine.Engine
import com.kregosh.chessopenings.core.engine.EngineLine
import com.kregosh.chessopenings.core.engine.Score
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun newWorker(url: String): JsAny = js("new Worker(url)")
private fun postMessage(worker: JsAny, message: String): Unit = js("worker.postMessage(message)")
private fun onMessage(worker: JsAny, handler: (String) -> Unit): Unit = js("worker.onmessage = function (e) { handler(String(e.data)); }")
private fun onError(worker: JsAny, handler: (String) -> Unit): Unit = js("worker.onerror = function (e) { handler(String(e.message || e)); }")

/**
 * Stockfish (the WASM build of Stockfish.js) in a Web Worker, spoken to over UCI. One
 * search runs at a time; requests queue up. A request whose caller was cancelled is
 * skipped, or stopped if it is already running.
 */
class WebEngine(scriptUrl: String, scope: CoroutineScope) : Engine {
    private class Request(
        val fen: String,
        val depth: Int,
        val searchMoves: List<String>,
        val multiPv: Int,
    ) {
        val result = CompletableDeferred<List<EngineLine>>()
    }

    private val worker = newWorker(scriptUrl)
    private val lines = Channel<String>(Channel.UNLIMITED)
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private var failure: String? = null

    init {
        onMessage(worker) { lines.trySend(it) }
        onError(worker) { message ->
            failure = message
            lines.trySend("bestmove (none)")
        }
        scope.launch { run() }
    }

    override suspend fun analyze(fen: String, depth: Int, searchMoves: List<String>, multiPv: Int): List<EngineLine> {
        failure?.let { error("Stockfish failed: $it") }
        val request = Request(fen, depth, searchMoves, multiPv)
        requests.send(request)
        try {
            return request.result.await()
        } finally {
            request.result.cancel() // marks it abandoned if we were cancelled
        }
    }

    private suspend fun waitFor(prefix: String, onLine: (String) -> Unit = {}): String {
        while (true) {
            val line = lines.receive()
            onLine(line)
            if (line.startsWith(prefix)) return line
        }
    }

    private suspend fun run() {
        postMessage(worker, "uci")
        waitFor("uciok")
        postMessage(worker, "isready")
        waitFor("readyok")
        for (r in requests) {
            if (r.result.isCompleted) continue // abandoned before it started
            val scores = LinkedHashMap<String, Score>()
            postMessage(worker, "setoption name MultiPV value ${r.multiPv}")
            postMessage(worker, "position fen ${r.fen}")
            postMessage(worker, "go depth ${r.depth}" + if (r.searchMoves.isEmpty()) "" else " searchmoves " + r.searchMoves.joinToString(" "))
            var stopped = false
            waitFor("bestmove") { line ->
                if (!stopped && r.result.isCompleted) {
                    postMessage(worker, "stop")
                    stopped = true
                }
                parseInfo(line)?.let { (move, score) -> scores[move] = score }
            }
            failure?.let { r.result.completeExceptionally(IllegalStateException("Stockfish failed: $it")) }
            r.result.complete(scores.entries.map { EngineLine(it.key, it.value) }.sortedByDescending { it.score.value }.take(r.multiPv))
        }
    }

    companion object {
        private val SCORE = Regex(""" score (cp|mate) (-?\d+)""")

        /** (first move, score) of a final "info … pv" line; bounds and other lines give null. */
        internal fun parseInfo(line: String): Pair<String, Score>? {
            if (!line.startsWith("info") || " pv " !in line || "bound" in line) return null
            val m = SCORE.find(line) ?: return null
            val v = m.groupValues[2].toInt()
            val score = if (m.groupValues[1] == "cp") Score(v) else Score.mate(v)
            return line.substringAfter(" pv ").substringBefore(' ') to score
        }
    }
}
