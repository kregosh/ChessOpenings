package com.kregosh.chessopenings.core.engine

/**
 * A UCI chess engine (Stockfish). Each platform provides one: a native process on
 * Android, a Web Worker in the browser.
 */
interface Engine {
    /**
     * Searches [fen] to [depth] and returns the best [multiPv] lines, best first. With
     * [searchMoves] (UCI), only those first moves are considered. Scores are from the side
     * to move's point of view.
     */
    suspend fun analyze(fen: String, depth: Int, searchMoves: List<String> = emptyList(), multiPv: Int = 1): List<EngineLine>
}

/** One principal variation: its first move (UCI) and score. */
data class EngineLine(val move: String, val score: Score)

/**
 * An evaluation in centipawns, or mate in [mate] moves (negative: getting mated). The
 * point of view is given by whoever produced it; [negate] switches sides.
 */
data class Score(val cp: Int, val mate: Int? = null) : Comparable<Score> {
    /** A single number for comparisons and differences; mates are ±(30000 − moves). */
    val value: Int get() = mate?.let { if (it > 0) MATE - it else -MATE - it } ?: cp

    fun negate(): Score = Score(-cp, mate?.let { -it })

    override fun compareTo(other: Score): Int = value.compareTo(other.value)

    companion object {
        const val MATE = 30_000

        fun mate(moves: Int): Score = Score(if (moves > 0) MATE - moves else -MATE - moves, moves)
    }
}

/** Search depths used for each purpose; lower on slow devices. */
data class EngineDepths(
    /** Grading the user's moves. */
    val review: Int = 12,
    /** The bot's moves once the book runs out. */
    val bot: Int = 12,
    /** The eval bar and the hint. */
    val eval: Int = 14,
)
