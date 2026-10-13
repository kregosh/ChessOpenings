package com.kregosh.chessopenings.core.training

import com.kregosh.chessopenings.chess.Color
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import com.kregosh.chessopenings.chess.Uci
import com.kregosh.chessopenings.core.book.BookMove
import com.kregosh.chessopenings.core.book.OpeningBook

enum class FeedbackMode { INSTANT, END }

data class SessionOptions(
    /** How many of the user's own moves a session lasts, counted after the starting line. */
    val moves: Int = 8,
    val feedback: FeedbackMode = FeedbackMode.INSTANT,
    val hints: Boolean = true,
    val evalBar: Boolean = false,
) {
    init {
        require(moves in 1..30) { "Session length out of range: $moves" }
    }
}

/**
 * What to train. [line] is the fixed starting line in UCI from the start position: the
 * user's first move(s), and optionally the opponent's reply to train against. As Black
 * with [randomWhiteFirstMove], [line] starts with Black's reply and White's first move is
 * drawn when the session starts.
 */
data class TrainingSetup(
    val color: Color,
    val line: List<String>,
    val randomWhiteFirstMove: Boolean = false,
    val options: SessionOptions = SessionOptions(),
) {
    init {
        require(!randomWhiteFirstMove || color == Color.BLACK) { "Only Black trains against a random first move" }
        require(!randomWhiteFirstMove || line.isNotEmpty()) { "A random White first move needs Black's reply" }
    }
}

/** A move offered in the setup screen, with how often it is played. */
data class Choice(
    val uci: String,
    val san: String,
    /** Share of games in this position (0..1) in the band that matters for the choice. */
    val share: Double,
    /** The opening name of the position after the move, if it has one. */
    val name: String?,
)

/**
 * Answers the setup screen's questions from the book: which first moves there are, which
 * replies the opponent may play, and which White first moves fit a Black reply.
 */
class SetupCatalog(private val book: OpeningBook) {

    /** The user's possible moves after [line]: book moves, by strong players' popularity. */
    fun userMoves(line: List<String>): List<Choice> {
        val position = play(line) ?: return emptyList()
        return choices(position, book.moves(position).filter { it.userBook }) { it.strongGames }
    }

    /** The opponent's realistic replies after [line], by club players' popularity. */
    fun opponentReplies(line: List<String>): List<Choice> {
        val position = play(line) ?: return emptyList()
        return choices(position, book.moves(position).filter { it.botCandidate }) { it.clubGames }
    }

    /**
     * Black's possible first moves, over all White first moves: a move is offered if it is
     * book against at least one of them. Popularity sums its strong games across them.
     */
    fun blackFirstMoves(): List<Choice> {
        val totals = LinkedHashMap<String, Int>()
        val sans = HashMap<String, String>()
        for (white in book.moves(Position.start)) {
            val after = white.toMove(Position.start)?.let(Position.start::play) ?: continue
            for (reply in book.moves(after).filter { it.userBook }) {
                totals[reply.uci] = (totals[reply.uci] ?: 0) + reply.strongGames
                sans.getOrPut(reply.uci) { San.write(after, reply.toMove(after)!!) }
            }
        }
        val sum = totals.values.sum().coerceAtLeast(1)
        return totals.entries.sortedByDescending { it.value }.map { (uci, n) ->
            Choice(uci, sans.getValue(uci), n.toDouble() / sum, name = null)
        }
    }

    /** White first moves after which [blackReply] is book, by club popularity. */
    fun whiteFirstMovesFor(blackReply: String): List<Choice> =
        whiteFirstMoveCandidates(blackReply).let { moves ->
            choices(Position.start, moves) { it.clubGames }
        }

    internal fun whiteFirstMoveCandidates(blackReply: String): List<BookMove> =
        book.moves(Position.start).filter { white ->
            val after = white.toMove(Position.start)?.let(Position.start::play) ?: return@filter false
            book.moves(after).any { it.uci == blackReply && it.userBook }
        }

    private fun choices(position: Position, moves: List<BookMove>, weight: (BookMove) -> Int): List<Choice> {
        val total = moves.sumOf(weight).coerceAtLeast(1)
        return moves.sortedByDescending(weight).mapNotNull { m ->
            val move = m.toMove(position) ?: return@mapNotNull null
            Choice(m.uci, San.write(position, move), weight(m).toDouble() / total, book.name(position.play(move)))
        }
    }

    private fun play(line: List<String>): Position? =
        line.fold(Position.start as Position?) { p, uci -> p?.let { Uci.parse(it, uci)?.let(it::play) } }
}
