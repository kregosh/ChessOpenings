package com.kregosh.chessopenings.core.training

import com.kregosh.chessopenings.chess.Move
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import com.kregosh.chessopenings.chess.Uci
import com.kregosh.chessopenings.core.book.OpeningBook
import com.kregosh.chessopenings.core.engine.Engine

enum class Quality {
    BOOK,
    /** Not book, but the engine's top choice (or scores just as well). */
    BEST,
    PLAYABLE,
    INACCURACY,
    MISTAKE,
    BLUNDER;

    companion object {
        /** Grade of a non-book move that loses [lossCp] centipawns against the best move. */
        fun fromLoss(lossCp: Int): Quality = when {
            lossCp <= 0 -> BEST
            lossCp <= 30 -> PLAYABLE
            lossCp <= 90 -> INACCURACY
            lossCp <= 200 -> MISTAKE
            else -> BLUNDER
        }
    }
}

/** The verdict on one of the user's moves. */
data class Verdict(
    val quality: Quality,
    /** Book moves in the position, most popular among strong players first (SAN). */
    val bookMoves: List<String>,
    /** The engine's best move (SAN), or the main book move for book moves. */
    val bestMove: String?,
    /** Centipawns lost against the best move; null if unknown. */
    val lossCp: Int?,
    val reasons: List<Reason>,
) {
    val inBook: Boolean get() = quality == Quality.BOOK

    /** The book had no moves here: the user is past the end of theory. */
    val pastBook: Boolean get() = bookMoves.isEmpty()
}

class Grader(private val book: OpeningBook, private val engine: Engine, private val depth: Int) {

    /** The book moves for the user in [position], most popular first. */
    fun bookMoves(position: Position) = book.moves(position).filter { it.userBook }.sortedByDescending { it.strongGames }

    /** Whether [move] is book: answered from the book alone, no engine needed. */
    fun isBook(position: Position, move: Move): Boolean = bookMoves(position).any { it.uci == move.uci }

    /** Grades [move] in [position]; the engine is only asked about non-book moves. */
    suspend fun grade(position: Position, move: Move, history: List<Pair<Position, Move>>): Verdict {
        val book = bookMoves(position)
        val bookSans = book.mapNotNull { m -> m.toMove(position)?.let { San.write(position, it) } }
        val played = book.firstOrNull { it.uci == move.uci }
        val reasons = Explainer.explain(position, move, history)
        if (played != null) {
            return Verdict(Quality.BOOK, bookSans, bookSans.firstOrNull(), played.lossCp, reasons.filter { it.kind.positive })
        }
        val loss = engineLoss(position, move)
        val quality = Quality.fromLoss(loss.first)
        // The engine's own choice gets no warnings: whatever principle it bends, it's right here.
        return Verdict(quality, bookSans, loss.second, loss.first, if (quality == Quality.BEST) reasons.filter { it.kind.positive } else reasons)
    }

    /** Loss of [move] against the engine's best, from one search scoring both. */
    private suspend fun engineLoss(position: Position, move: Move): Pair<Int, String?> {
        val fen = position.toFen()
        val best = engine.analyze(fen, depth).firstOrNull()?.move ?: return 0 to null
        val bestSan = Uci.parse(position, best)?.let { San.write(position, it) }
        if (best == move.uci) return 0 to bestSan
        val lines = engine.analyze(fen, depth, searchMoves = listOf(best, move.uci), multiPv = 2)
        val bestScore = lines.maxOfOrNull { it.score.value } ?: return 0 to bestSan
        val moveScore = lines.firstOrNull { it.move == move.uci }?.score?.value ?: return 0 to bestSan
        return maxOf(0, bestScore - moveScore) to bestSan
    }
}
