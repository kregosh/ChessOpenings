package com.kregosh.chessopenings.core.training

import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.core.book.BookMove
import com.kregosh.chessopenings.core.book.OpeningBook
import kotlin.random.Random

/** How the bot picks its moves while the book lasts. */
class BotPolicy(private val book: OpeningBook, private val random: Random) {
    /**
     * A bot candidate for [position], drawn with probability proportional to how often
     * club players chose it; null when the book has none (the engine takes over).
     */
    fun bookMove(position: Position): BookMove? = weighted(book.moves(position).filter { it.botCandidate && it.toMove(position) != null })

    /** White's first move for a Black session: one after which [blackReply] is book. */
    fun whiteFirstMove(blackReply: String): BookMove? = weighted(SetupCatalog(book).whiteFirstMoveCandidates(blackReply))

    private fun weighted(moves: List<BookMove>): BookMove? {
        if (moves.isEmpty()) return null
        val total = moves.sumOf { it.clubGames.toLong().coerceAtLeast(1) }
        var r = random.nextLong(total)
        for (m in moves) {
            r -= m.clubGames.toLong().coerceAtLeast(1)
            if (r < 0) return m
        }
        return moves.last()
    }
}
