package com.kregosh.chessopenings.core.training

import com.kregosh.chessopenings.chess.Color
import com.kregosh.chessopenings.chess.Move
import com.kregosh.chessopenings.chess.PieceType
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.Square

/** Why a move is good or bad, in beginner terms. The UI turns these into text. */
enum class ReasonKind(val positive: Boolean) {
    DEVELOPS_PIECE(true),
    CENTER_PAWN(true),
    CASTLES(true),
    CAPTURES(true),
    GIVES_CHECK(true),
    EARLY_QUEEN(false),
    SAME_PIECE_TWICE(false),
    BLOCKS_BISHOP(false),
    LOSES_CASTLING(false),
    HANGS_PIECE(false),
    FLANK_PAWN(false),
}

/** A reason, with the square it is about where that helps (the hanging piece, the blocked bishop). */
data class Reason(val kind: ReasonKind, val square: Square? = null)

/**
 * Rule-based explanations of opening moves. Simple on purpose: they name the principle a
 * move follows or breaks, while the engine decides how good it actually is.
 */
object Explainer {
    private val CENTER = setOf("c4", "d4", "e4", "c5", "d5", "e5").map(Square::parse).toSet()
    private val VALUE = mapOf(
        PieceType.PAWN to 1, PieceType.KNIGHT to 3, PieceType.BISHOP to 3,
        PieceType.ROOK to 5, PieceType.QUEEN to 9, PieceType.KING to 100,
    )

    /**
     * Reasons for [move] in [before]. [history] holds the earlier plies of the game as
     * (position before, move), oldest first. Negative reasons come first.
     */
    fun explain(before: Position, move: Move, history: List<Pair<Position, Move>> = emptyList()): List<Reason> {
        val us = before.sideToMove
        val piece = before.pieceAt(move.from) ?: return emptyList()
        val after = before.play(move)
        val capture = before.pieceAt(move.to) != null || move.isEnPassant
        val undeveloped = minorsAtHome(before, us)
        val positives = ArrayList<Reason>()
        val negatives = ArrayList<Reason>()

        if (move.isCastling) positives += Reason(ReasonKind.CASTLES)
        if (piece.type in MINORS && move.from in homeSquares(piece.type, us)) positives += Reason(ReasonKind.DEVELOPS_PIECE, move.to)
        if (piece.type == PieceType.PAWN && move.to in CENTER) positives += Reason(ReasonKind.CENTER_PAWN, move.to)
        if (capture) positives += Reason(ReasonKind.CAPTURES, move.to)
        if (after.isCheck) positives += Reason(ReasonKind.GIVES_CHECK)

        // Moving a piece out of an attack isn't wasted time, even if it moved before.
        val underAttack = before.attackers(move.from, us.opposite).isNotEmpty()
        if (!capture && !before.isCheck && !underAttack) {
            if (piece.type == PieceType.QUEEN && before.fullmoveNumber <= 8 && undeveloped >= 2) {
                negatives += Reason(ReasonKind.EARLY_QUEEN, move.to)
            }
            if (piece.type != PieceType.PAWN && piece.type != PieceType.KING && undeveloped >= 1 &&
                move.from in movedPieceSquares(history, us)
            ) {
                negatives += Reason(ReasonKind.SAME_PIECE_TWICE, move.to)
            }
            if (piece.type == PieceType.PAWN && move.from.file in listOf(0, 1, 6, 7) &&
                before.fullmoveNumber <= 6 && undeveloped >= 2
            ) {
                negatives += Reason(ReasonKind.FLANK_PAWN, move.to)
            }
        }
        if (!move.isCastling && castlingRights(before, us) != 0 && castlingRights(after, us) == 0) {
            negatives += Reason(ReasonKind.LOSES_CASTLING)
        }
        for (home in homeSquares(PieceType.BISHOP, us)) {
            if (move.from == home) continue
            if (before.pieceAt(home)?.let { it.type == PieceType.BISHOP && it.color == us } != true) continue
            if (bishopMobility(before, home) > 0 && bishopMobility(after, home) == 0) negatives += Reason(ReasonKind.BLOCKS_BISHOP, home)
        }
        hangingPiece(after, us)?.let { negatives += Reason(ReasonKind.HANGS_PIECE, it) }

        return negatives + positives
    }

    private val MINORS = setOf(PieceType.KNIGHT, PieceType.BISHOP)

    private fun homeSquares(type: PieceType, color: Color): List<Square> {
        val rank = if (color == Color.WHITE) 0 else 7
        return when (type) {
            PieceType.KNIGHT -> listOf(Square.of(1, rank), Square.of(6, rank))
            PieceType.BISHOP -> listOf(Square.of(2, rank), Square.of(5, rank))
            else -> emptyList()
        }
    }

    private fun minorsAtHome(p: Position, color: Color): Int = MINORS.sumOf { type ->
        homeSquares(type, color).count { sq -> p.pieceAt(sq)?.let { it.type == type && it.color == color } == true }
    }

    /** Squares where [color]'s pieces stand that got there by an earlier move of theirs. */
    private fun movedPieceSquares(history: List<Pair<Position, Move>>, color: Color): Set<Square> {
        val squares = HashSet<Square>()
        for ((position, move) in history) {
            squares -= move.to // whatever stood there was captured or replaced
            if (position.sideToMove == color) {
                squares -= move.from
                squares += move.to
            }
        }
        return squares
    }

    private fun castlingRights(p: Position, color: Color): Int =
        p.castlingRights and (if (color == Color.WHITE) Position.WHITE_KINGSIDE or Position.WHITE_QUEENSIDE else Position.BLACK_KINGSIDE or Position.BLACK_QUEENSIDE)

    /** Squares a bishop on its home square could move to along its two forward diagonals. */
    private fun bishopMobility(p: Position, home: Square): Int {
        val color = p.pieceAt(home)?.color ?: return 0
        val forward = if (color == Color.WHITE) 1 else -1
        var count = 0
        for (df in listOf(-1, 1)) {
            var f = home.file + df
            var r = home.rank + forward
            while (f in 0..7 && r in 0..7) {
                val occupant = p.pieceAt(Square.of(f, r))
                if (occupant == null) count++
                else {
                    if (occupant.color != color) count++
                    break
                }
                f += df
                r += forward
            }
        }
        return count
    }

    /**
     * A piece of [us] (not pawn or king) that the opponent, now to move, can win: attacked
     * and undefended, or attacked by something worth less. Returns its square.
     */
    private fun hangingPiece(after: Position, us: Color): Square? {
        val them = us.opposite
        return Square.all
            .filter { sq -> after.pieceAt(sq)?.let { it.color == us && it.type != PieceType.PAWN && it.type != PieceType.KING } == true }
            .sortedByDescending { VALUE.getValue(after.pieceAt(it)!!.type) }
            .firstOrNull { sq ->
                val value = VALUE.getValue(after.pieceAt(sq)!!.type)
                val attackers = after.attackers(sq, them)
                attackers.isNotEmpty() && (
                    after.attackers(sq, us).isEmpty() ||
                        attackers.minOf { VALUE.getValue(after.pieceAt(it)!!.type) } < value
                    )
            }
    }
}
