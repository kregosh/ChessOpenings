package com.kregosh.chessopenings.core.training

import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import com.kregosh.chessopenings.chess.Square
import com.kregosh.chessopenings.chess.Uci
import com.kregosh.chessopenings.core.book.BookMove
import com.kregosh.chessopenings.core.book.OpeningBook
import com.kregosh.chessopenings.core.engine.Engine
import com.kregosh.chessopenings.core.engine.EngineLine
import com.kregosh.chessopenings.core.engine.Score

/** The position after [sans] from the start. */
fun line(vararg sans: String): Position = sans.fold(Position.start) { p, s -> p.play(San.parse(p, s) ?: error("Can't play $s in $p")) }

/** UCI moves for [sans] from the start. */
fun uci(vararg sans: String): List<String> {
    var p = Position.start
    return sans.map { s -> San.parse(p, s)!!.also { p = p.play(it) }.uci }
}

private fun bookMove(position: Position, san: String, strong: Int, club: Int, user: Boolean, bot: Boolean, loss: Int = 0): BookMove {
    val m = San.parse(position, san) ?: error("Can't play $san in $position")
    return BookMove(m.from, m.to, m.promotion, strong, club, loss, user, bot)
}

/**
 * A small book:
 * ```
 * start      e4 (user+bot 600/700), d4 (user+bot 300/200), b3 (bot only, 0/100)
 * 1.e4       e5 (user+bot 300/600), c5 (user+bot 400/100)
 * 1.e4 e5    Nf3 (user+bot), Bc4 (user+bot), Qh5 (bot only)
 * 1.e4 c5    Nf3 (user+bot)
 * 1.e4 e5 Nf3   Nc6 (user+bot)
 * 1.d4       d5 (user+bot), Nf6 (user only)
 * 1.b3       e5 (user+bot)
 * ```
 */
fun testBook(): OpeningBook {
    val positions = HashMap<Long, List<BookMove>>()
    fun at(p: Position, vararg moves: BookMove) {
        positions[p.key] = moves.toList()
    }
    val s = Position.start
    at(s, bookMove(s, "e4", 600, 700, true, true), bookMove(s, "d4", 300, 200, true, true), bookMove(s, "b3", 0, 100, false, true))
    val e4 = line("e4")
    at(e4, bookMove(e4, "e5", 300, 600, true, true), bookMove(e4, "c5", 400, 100, true, true))
    val e4e5 = line("e4", "e5")
    at(
        e4e5,
        bookMove(e4e5, "Nf3", 500, 500, true, true),
        bookMove(e4e5, "Bc4", 100, 200, true, true, loss = 15),
        bookMove(e4e5, "Qh5", 0, 100, false, true, loss = 60),
    )
    val e4c5 = line("e4", "c5")
    at(e4c5, bookMove(e4c5, "Nf3", 400, 100, true, true))
    val e4e5Nf3 = line("e4", "e5", "Nf3")
    at(e4e5Nf3, bookMove(e4e5Nf3, "Nc6", 400, 500, true, true))
    val d4 = line("d4")
    at(d4, bookMove(d4, "d5", 200, 150, true, true), bookMove(d4, "Nf6", 250, 0, true, false))
    val b3 = line("b3")
    at(b3, bookMove(b3, "e5", 10, 100, true, true))
    val names = mapOf(
        line("e4").key to "King's Pawn Game",
        line("e4", "c5").key to "Sicilian Defense",
        line("e4", "e5", "Nf3").key to "King's Knight Opening",
    )
    return OpeningBook(emptyMap(), positions, names)
}

/**
 * A scriptable engine. Scores come from [score] (side to move's view, per FEN and move),
 * 0 by default; the best move is the highest scoring legal move, ties going to the first
 * legal move.
 */
class FakeEngine(var score: (fen: String, uci: String) -> Int = { _, _ -> 0 }) : Engine {
    val calls = ArrayList<String>()
    var fail = false

    override suspend fun analyze(fen: String, depth: Int, searchMoves: List<String>, multiPv: Int): List<EngineLine> {
        calls += fen
        if (fail) error("engine crashed")
        val p = Position.fromFen(fen)
        val moves = searchMoves.ifEmpty { p.legalMoves.map { it.uci } }
        return moves.map { EngineLine(it, Score(score(fen, it))) }.sortedByDescending { it.score.value }.take(multiPv)
    }

    /** Scores [san] in [position] at [cp] (side to move's view). */
    fun set(position: Position, san: String, cp: Int) {
        val fen = position.toFen()
        val uci = San.parse(position, san)!!.uci
        val previous = score
        score = { f, u -> if (f == fen && u == uci) cp else previous(f, u) }
    }
}

fun sq(name: String) = Square.parse(name)
