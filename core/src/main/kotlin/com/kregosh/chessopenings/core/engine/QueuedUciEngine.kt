package com.kregosh.chessopenings.core.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Talks UCI to an engine that exchanges text lines: Stockfish.js in a Web Worker, or the
 * same in an Android WebView. The platform passes in [send] and feeds the engine's output
 * to [lineReceived]. One search runs at a time; requests queue up. A request whose caller
 * was cancelled is skipped, or stopped if it is already running.
 */
class QueuedUciEngine(scope: CoroutineScope, private val send: (String) -> Unit) : Engine {
    private class Request(
        val fen: String,
        val depth: Int,
        val searchMoves: List<String>,
        val multiPv: Int,
    ) {
        val result = CompletableDeferred<List<EngineLine>>()
    }

    private val lines = Channel<String>(Channel.UNLIMITED)
    private val requests = Channel<Request>(Channel.UNLIMITED)
    // Set from the platform's thread (e.g. the Android WebView bridge), read by the search loop.
    @Volatile
    private var failure: String? = null

    init {
        scope.launch { run() }
    }

    /** A line of engine output. Safe to call from any thread. */
    fun lineReceived(line: String) {
        lines.trySend(line)
    }

    /** The engine failed (e.g. it couldn't load); pending and later requests fail with [message]. */
    fun failed(message: String) {
        failure = message
        lines.trySend("bestmove (none)")
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
            if (line.startsWith(prefix) || failure != null) return line
        }
    }

    private suspend fun run() {
        send("uci")
        waitFor("uciok")
        send("isready")
        waitFor("readyok")
        for (r in requests) {
            if (r.result.isCompleted) continue // abandoned before it started
            failure?.let {
                r.result.completeExceptionally(IllegalStateException("Stockfish failed: $it"))
                continue
            }
            val scores = LinkedHashMap<String, Score>()
            send("setoption name MultiPV value ${r.multiPv}")
            send("position fen ${r.fen}")
            send("go depth ${r.depth}" + if (r.searchMoves.isEmpty()) "" else " searchmoves " + r.searchMoves.joinToString(" "))
            var stopped = false
            waitFor("bestmove") { line ->
                if (!stopped && r.result.isCompleted) {
                    send("stop")
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
        fun parseInfo(line: String): Pair<String, Score>? {
            if (!line.startsWith("info") || " pv " !in line || "bound" in line) return null
            val m = SCORE.find(line) ?: return null
            val v = m.groupValues[2].toInt()
            val score = if (m.groupValues[1] == "cp") Score(v) else Score.mate(v)
            return line.substringAfter(" pv ").substringBefore(' ') to score
        }
    }
}
