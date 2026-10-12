package com.kregosh.chessopenings.bookgen

import java.io.BufferedReader
import java.io.Writer

/**
 * The text file `count` writes and `build` reads, so the slow counting pass over game dumps
 * and the slow evaluation pass can run separately. Only positions seen at least
 * `minGames` times are written:
 * ```
 * P <key hex> <fen>
 * M <uci> <strong games> <club games>
 * ```
 * Counts files from several runs can be merged by [read]ing them into one map.
 */
object CountsFile {
    fun write(counter: MoveCounter, out: Writer, minGames: Int, header: List<String> = emptyList()) {
        for (line in header) out.write("# $line\n")
        for ((key, counts) in counter.positions.entries.sortedBy { it.key }) {
            val fen = counts.fen ?: continue
            if (counts.total < minGames) continue
            out.write("P\t${key.toULong().toString(16)}\t$fen\n")
            for ((uci, n) in counts.moves.entries.sortedByDescending { it.value.sum() }) {
                out.write("M\t$uci\t${n[Band.STRONG.ordinal]}\t${n[Band.CLUB.ordinal]}\n")
            }
        }
    }

    /** Reads a counts file, adding to [into] (so several files merge). */
    fun read(input: BufferedReader, into: MutableMap<Long, PositionCounts> = HashMap()): MutableMap<Long, PositionCounts> {
        var current: PositionCounts? = null
        input.lineSequence().forEachIndexed { i, line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val f = line.split('\t')
            when (f[0]) {
                "P" -> {
                    val key = f[1].toULong(16).toLong()
                    current = into.getOrPut(key) { PositionCounts() }.also { it.fen = it.fen ?: f[2] }
                }
                "M" -> {
                    val c = current ?: error("Move before any position on line ${i + 1}")
                    c.add(f[1], Band.STRONG, f[2].toInt())
                    c.add(f[1], Band.CLUB, f[3].toInt())
                }
                else -> error("Bad line ${i + 1} in counts file: $line")
            }
        }
        return into
    }
}
