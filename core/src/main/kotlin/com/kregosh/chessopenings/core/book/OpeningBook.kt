package com.kregosh.chessopenings.core.book

import com.kregosh.chessopenings.chess.Move
import com.kregosh.chessopenings.chess.PieceType
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.Square

/** One move from a book position, with the game statistics and verdicts bookgen computed. */
data class BookMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    /** Games between strong players (both 2000+) that played this move here. */
    val strongGames: Int,
    /** Games between club players (average 1200–1800) that played this move here. */
    val clubGames: Int,
    /** Centipawns this move loses against Stockfish's best move; null if not evaluated. */
    val lossCp: Int?,
    /** Counts as book for the user: what strong players play, and sound. */
    val userBook: Boolean,
    /** The bot may play it: what club players play, and not bad. */
    val botCandidate: Boolean,
) {
    val uci: String get() = from.name + to.name + (promotion?.letter ?: "")

    /** The legal move this entry denotes in [position], or null if it doesn't fit. */
    fun toMove(position: Position): Move? = position.findMove(from, to, promotion)
}

/**
 * The opening book: moves per position (keyed by Polyglot key) and opening names.
 * Positions are kept sorted by key so lookups are a binary search.
 */
class OpeningBook(
    /** Free-form build information: thresholds, source data, date. */
    val metadata: Map<String, String>,
    positions: Map<Long, List<BookMove>>,
    val names: Map<Long, String>,
) {
    private val keys: LongArray = positions.keys.sorted().toLongArray()
    private val moves: Array<List<BookMove>> = Array(keys.size) { positions.getValue(keys[it]) }

    val positionCount: Int get() = keys.size

    fun moves(position: Position): List<BookMove> = moves(position.key)

    fun moves(key: Long): List<BookMove> {
        val i = keys.binarySearch(key)
        return if (i >= 0) moves[i] else emptyList()
    }

    fun name(position: Position): String? = names[position.key]

    /** All positions with their moves, in key order. */
    fun entries(): Sequence<Pair<Long, List<BookMove>>> = keys.indices.asSequence().map { keys[it] to moves[it] }

    companion object {
        fun decode(bytes: ByteArray): OpeningBook = BookCodec.decode(bytes)
    }
}
