package com.kregosh.chessopenings.ui

import com.kregosh.chessopenings.core.engine.Score
import com.kregosh.chessopenings.core.training.Quality
import com.kregosh.chessopenings.core.training.Reason
import com.kregosh.chessopenings.core.training.ReasonKind

/** All user-facing text, in one place. */
object Strings {
    const val APP_NAME = "Opening Trainer"

    fun quality(q: Quality): String = when (q) {
        Quality.BOOK -> "Book move"
        Quality.BEST -> "Best move"
        Quality.PLAYABLE -> "Playable"
        Quality.INACCURACY -> "Inaccuracy"
        Quality.MISTAKE -> "Mistake"
        Quality.BLUNDER -> "Blunder"
    }

    fun reason(r: Reason): String = when (r.kind) {
        ReasonKind.DEVELOPS_PIECE -> "Brings a new piece into the game."
        ReasonKind.CENTER_PAWN -> "Fights for the center."
        ReasonKind.CASTLES -> "Tucks your king away safely."
        ReasonKind.CAPTURES -> "Wins back material."
        ReasonKind.GIVES_CHECK -> "Gives check."
        ReasonKind.EARLY_QUEEN -> "The queen comes out early and can get chased around."
        ReasonKind.SAME_PIECE_TWICE -> "Moves the same piece again while others are still at home."
        ReasonKind.BLOCKS_BISHOP -> "Blocks your bishop on ${r.square}."
        ReasonKind.LOSES_CASTLING -> "Gives up your right to castle."
        ReasonKind.HANGS_PIECE -> "Leaves the piece on ${r.square} hanging."
        ReasonKind.FLANK_PAWN -> "An edge pawn move doesn't help your development."
    }

    private val praise = listOf("Nice, that's book!", "Straight from the book!", "Theory approves.", "Well remembered!", "Exactly what the masters play.")

    /** A varying compliment for a book move. */
    fun praise(seed: Int): String = praise[((seed % praise.size) + praise.size) % praise.size]

    fun headline(book: Int, total: Int): String = when {
        total == 0 -> "Session over"
        book == total -> "Perfect opening!"
        book * 4 >= total * 3 -> "Great job!"
        book * 2 >= total -> "Good progress!"
        else -> "Keep practicing!"
    }

    /** "+0.8", "-1.3", "M3": from White's point of view. */
    fun eval(score: Score): String = score.mate?.let { "M${kotlin.math.abs(it)}" }
        ?: ((if (score.cp >= 0) "+" else "-") + (kotlin.math.abs(score.cp) / 100).toString() + "." + ((kotlin.math.abs(score.cp) % 100) / 10).toString())

    fun pawns(cp: Int): String = (cp / 100).toString() + "." + ((cp % 100) / 10).toString()
}
