package com.kregosh.chessopenings.bookgen

import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San

enum class Band { STRONG, CLUB }

/** Which games count, and which rating band they belong to. */
data class GameFilter(
    val strongMin: Int = 2000,
    val clubMinAverage: Int = 1200,
    val clubMaxAverage: Int = 1800,
    /** Games with a shorter base time (seconds) are skipped: bullet openings are noisy. */
    val minBaseSeconds: Int = 180,
) {
    /** The band [game] belongs to, or null if it should be skipped. */
    fun band(game: PgnGame): Band? {
        val tags = game.tags
        if (tags["Variant"]?.let { it != "Standard" } == true) return null
        if (tags["SetUp"] == "1" || tags.containsKey("FEN")) return null
        if (tags["Event"]?.contains("Rated", ignoreCase = true) == false) return null
        val base = tags["TimeControl"]?.substringBefore('+')?.toIntOrNull()
        if (base != null && base < minBaseSeconds) return null
        val white = tags["WhiteElo"]?.toIntOrNull() ?: return null
        val black = tags["BlackElo"]?.toIntOrNull() ?: return null
        if (white >= strongMin && black >= strongMin) return Band.STRONG
        val average = (white + black) / 2
        if (average in clubMinAverage..clubMaxAverage) return Band.CLUB
        return null
    }
}

/** Game counts for one position, per move (UCI) and band. */
class PositionCounts {
    /** Filled in once the position is seen often enough to be worth evaluating later. */
    var fen: String? = null
    val moves = HashMap<String, IntArray>(4)
    var total = 0
        private set

    fun add(uci: String, band: Band, n: Int = 1) {
        moves.getOrPut(uci) { IntArray(2) }[band.ordinal] += n
        total += n
    }

    fun games(band: Band): Int = moves.values.sumOf { it[band.ordinal] }
}

/**
 * Counts moves per position over a stream of games. Memory is bounded by lossy counting:
 * whenever more than [maxPositions] positions are tracked, positions seen at most
 * [floor] times are dropped and the floor rises. Popular positions, the only ones that
 * end up in the book, are unaffected; a dropped position undercounts by at most the
 * final floor, which [stats] reports.
 */
class MoveCounter(
    private val filter: GameFilter = GameFilter(),
    private val maxPlies: Int = 24,
    private val maxPositions: Int = 2_000_000,
    /** Store a position's FEN once it has been seen this often. */
    private val fenAfter: Int = 5,
) {
    val positions = HashMap<Long, PositionCounts>()
    private var floor = 0
    var gamesRead = 0L
        private set
    val gamesPerBand = LongArray(Band.entries.size)
    var illegalGames = 0L
        private set

    fun add(game: PgnGame) {
        gamesRead++
        val band = filter.band(game) ?: return
        gamesPerBand[band.ordinal]++
        var position = Position.start
        for (san in game.sans.take(maxPlies)) {
            val move = San.parse(position, san)
            if (move == null) {
                illegalGames++
                return
            }
            val counts = positions.getOrPut(position.key) { PositionCounts() }
            counts.add(move.uci, band)
            if (counts.fen == null && counts.total >= fenAfter) counts.fen = position.toFen()
            position = position.play(move)
        }
        if (positions.size > maxPositions) prune()
    }

    private fun prune() {
        while (positions.size > maxPositions * 3 / 4) {
            floor++
            positions.values.removeAll { it.total <= floor }
        }
    }

    fun stats(): String = buildString {
        append("games read: $gamesRead, strong: ${gamesPerBand[Band.STRONG.ordinal]}, ")
        append("club: ${gamesPerBand[Band.CLUB.ordinal]}, illegal: $illegalGames, ")
        append("positions: ${positions.size}, pruning floor: $floor")
    }
}
