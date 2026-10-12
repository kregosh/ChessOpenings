package com.kregosh.chessopenings.bookgen

import java.io.BufferedReader
import java.io.Writer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Scores from the side to move's point of view, in centipawns (mates as ±(30000 − plies)). */
fun interface Evaluator {
    /** The best score in the position, and the score of each of [moves] (UCI). */
    fun evaluate(fen: String, moves: List<String>): Evaluation
}

data class Evaluation(val best: Int, val moves: Map<String, Int>) {
    /** How much [uci] loses against the best move, never negative. */
    fun loss(uci: String): Int? = moves[uci]?.let { maxOf(0, best - it) }
}

/** A Stockfish (or any UCI engine) process. Not thread-safe: one per thread. */
class UciEngine(command: List<String>, private val depth: Int, hashMb: Int = 64) : Evaluator, AutoCloseable {
    private val process = ProcessBuilder(command).redirectErrorStream(true).start()
    private val input: BufferedReader = process.inputStream.bufferedReader()
    private val output: Writer = process.outputStream.bufferedWriter()

    init {
        send("uci")
        waitFor("uciok")
        send("setoption name Threads value 1")
        send("setoption name Hash value $hashMb")
        send("isready")
        waitFor("readyok")
    }

    private fun send(command: String) {
        output.write(command + "\n")
        output.flush()
    }

    private fun waitFor(prefix: String, onLine: (String) -> Unit = {}): String {
        while (true) {
            val line = input.readLine() ?: error("Engine exited while waiting for '$prefix'")
            onLine(line)
            if (line.startsWith(prefix)) return line
        }
    }

    /** Searches [fen] (restricted to [moves] if given) and returns the score per first move. */
    private fun search(fen: String, moves: List<String>?, multiPv: Int): Map<String, Int> {
        send("setoption name MultiPV value $multiPv")
        send("position fen $fen")
        send("go depth $depth" + (moves?.let { " searchmoves " + it.joinToString(" ") } ?: ""))
        val scores = HashMap<String, Int>()
        waitFor("bestmove") { line ->
            if (!line.startsWith("info") || " pv " !in line || "bound" in line) return@waitFor
            val score = SCORE.find(line) ?: return@waitFor
            val firstMove = line.substringAfter(" pv ").substringBefore(' ')
            val v = score.groupValues[2].toInt()
            scores[firstMove] = if (score.groupValues[1] == "cp") v else (if (v > 0) MATE - v else -MATE - v)
        }
        return scores
    }

    override fun evaluate(fen: String, moves: List<String>): Evaluation {
        // Fresh hash per position, so results don't depend on what was searched before.
        send("ucinewgame")
        // First find the engine's best move, then score it together with [moves] in one
        // search: scores from separate searches differ by search noise, which would show
        // up as loss.
        val bestMove = search(fen, null, 1).maxByOrNull { it.value }?.key
        val searched = (moves + listOfNotNull(bestMove)).distinct()
        if (searched.isEmpty()) return Evaluation(0, emptyMap())
        val scores = search(fen, searched, searched.size)
        return Evaluation(scores.values.maxOrNull() ?: 0, scores.filterKeys { it in moves })
    }

    override fun close() {
        runCatching { send("quit") }
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    companion object {
        private val SCORE = Regex(""" score (cp|mate) (-?\d+)""")
        const val MATE = 30_000
    }
}

/** Evaluates many positions with [threads] engine processes. */
fun evaluateAll(
    jobs: List<Pair<String, List<String>>>,
    threads: Int,
    newEngine: () -> UciEngine,
    log: (String) -> Unit = {},
): List<Evaluation> {
    val results = arrayOfNulls<Evaluation>(jobs.size)
    val next = AtomicInteger()
    val done = AtomicInteger()
    val pool = Executors.newFixedThreadPool(threads)
    val workers = List(threads) {
        pool.submit {
            newEngine().use { engine ->
                while (true) {
                    val i = next.getAndIncrement()
                    if (i >= jobs.size) break
                    results[i] = engine.evaluate(jobs[i].first, jobs[i].second)
                    val n = done.incrementAndGet()
                    if (n % 500 == 0) log("evaluated $n / ${jobs.size}")
                }
            }
        }
    }
    pool.shutdown()
    workers.forEach { it.get() } // rethrows a worker's failure
    return results.map { it ?: error("Evaluation failed") }
}
