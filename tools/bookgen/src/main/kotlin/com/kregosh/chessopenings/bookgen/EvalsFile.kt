package com.kregosh.chessopenings.bookgen

import java.io.BufferedReader
import java.io.Writer

/**
 * Engine evaluations per position, as written by `bookgen evaluate` (one shard each) and
 * read by `bookgen build --evals`:
 * ```
 * E <key hex> <best score> <uci>:<score> <uci>:<score> …
 * ```
 * Scores are centipawns from the side to move's point of view (mates as ±(30000 − plies)).
 */
object EvalsFile {
    fun write(evaluations: List<Pair<Long, Evaluation>>, out: Writer) {
        for ((key, e) in evaluations) {
            out.write("E\t${key.toULong().toString(16)}\t${e.best}")
            for ((uci, score) in e.moves) out.write("\t$uci:$score")
            out.write("\n")
        }
    }

    /** Reads an evals file, adding to [into] (so the shards' files merge). */
    fun read(input: BufferedReader, into: MutableMap<Long, Evaluation> = HashMap()): MutableMap<Long, Evaluation> {
        input.lineSequence().forEachIndexed { i, line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val f = line.split('\t')
            require(f.size >= 3 && f[0] == "E") { "Bad line ${i + 1} in evals file: $line" }
            val moves = f.drop(3).associate { it.substringBefore(':') to it.substringAfter(':').toInt() }
            into[f[1].toULong(16).toLong()] = Evaluation(f[2].toInt(), moves)
        }
        return into
    }
}
