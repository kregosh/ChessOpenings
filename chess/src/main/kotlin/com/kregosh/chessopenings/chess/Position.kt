package com.kregosh.chessopenings.chess

/**
 * An immutable chess position. [play] returns a new position; nothing is ever changed
 * in place, so positions can be shared and kept freely (move history, undo, caches).
 *
 * The board is a 64-entry mailbox of piece codes: 0 = empty, otherwise
 * `type.ordinal + 1`, plus [BLACK_BIT] for Black pieces.
 */
class Position internal constructor(
    private val board: IntArray,
    val sideToMove: Color,
    /** Bit set of [WHITE_KINGSIDE], [WHITE_QUEENSIDE], [BLACK_KINGSIDE], [BLACK_QUEENSIDE]. */
    val castlingRights: Int,
    /** The square a pawn skipped with a double push on the last move, as in FEN. */
    val enPassantSquare: Square?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    fun pieceAt(square: Square): Piece? = pieceOf(board[square.index])

    /** All legal moves for [sideToMove]. */
    val legalMoves: List<Move> by lazy {
        val us = sideToMove
        pseudoLegalMoves().filter { m ->
            val next = playUnchecked(m)
            !next.isAttacked(next.kingSquare(us), us.opposite)
        }
    }

    val isCheck: Boolean by lazy { isAttacked(kingSquare(sideToMove), sideToMove.opposite) }
    val isCheckmate: Boolean get() = isCheck && legalMoves.isEmpty()
    val isStalemate: Boolean get() = !isCheck && legalMoves.isEmpty()

    /** Polyglot-compatible Zobrist key; equal positions (for repetition purposes) share it. */
    val key: Long by lazy { Zobrist.key(this) }

    /** The legal move from [from] to [to] (promoting to [promotion]), or null if illegal. */
    fun findMove(from: Square, to: Square, promotion: PieceType? = null): Move? =
        legalMoves.firstOrNull { it.from == from && it.to == to && it.promotion == promotion }

    /** The position after [move], which must be one of [legalMoves]. */
    fun play(move: Move): Position {
        require(move in legalMoves) { "Illegal move $move in ${toFen()}" }
        return playUnchecked(move)
    }

    fun toFen(): String = Fen.write(this)

    override fun toString(): String = toFen()

    override fun equals(other: Any?): Boolean =
        other is Position && toFen() == other.toFen()

    override fun hashCode(): Int = toFen().hashCode()

    // ── Internals ──────────────────────────────────────────────────────────

    internal fun code(index: Int): Int = board[index]

    internal fun kingSquare(color: Color): Int {
        val king = codeOf(PieceType.KING, color)
        for (i in 0 until 64) if (board[i] == king) return i
        error("No ${color.name.lowercase()} king in ${toFen()}")
    }

    /** Whether [square] is attacked by any piece of [by]. */
    internal fun isAttacked(square: Int, by: Color): Boolean {
        val byBlack = if (by == Color.BLACK) BLACK_BIT else 0
        // A pawn of [by] attacks [square] if it stands where a pawn of the other color on
        // [square] would capture.
        for (target in Attacks.pawn[if (by == Color.WHITE) 1 else 0][square]) {
            if (board[target] == (PAWN or byBlack)) return true
        }
        for (target in Attacks.knight[square]) if (board[target] == (KNIGHT or byBlack)) return true
        for (target in Attacks.king[square]) if (board[target] == (KING or byBlack)) return true
        for (dir in 0 until 8) {
            val diagonal = dir >= 4
            for (target in Attacks.rays[dir][square]) {
                val c = board[target]
                if (c == 0) continue
                if (c and BLACK_BIT == byBlack) {
                    val t = c and TYPE_MASK
                    if (t == QUEEN || t == (if (diagonal) BISHOP else ROOK)) return true
                }
                break
            }
        }
        return false
    }

    private fun pseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>(48)
        val us = sideToMove
        val usBlack = if (us == Color.BLACK) BLACK_BIT else 0
        for (from in 0 until 64) {
            val c = board[from]
            if (c == 0 || c and BLACK_BIT != usBlack) continue
            when (c and TYPE_MASK) {
                PAWN -> pawnMoves(from, us, moves)
                KNIGHT -> stepMoves(from, Attacks.knight[from], usBlack, moves)
                KING -> {
                    stepMoves(from, Attacks.king[from], usBlack, moves)
                    castlingMoves(us, moves)
                }
                BISHOP -> slideMoves(from, 4 until 8, usBlack, moves)
                ROOK -> slideMoves(from, 0 until 4, usBlack, moves)
                QUEEN -> slideMoves(from, 0 until 8, usBlack, moves)
            }
        }
        return moves
    }

    private fun isEnemy(index: Int, usBlack: Int): Boolean {
        val c = board[index]
        return c != 0 && c and BLACK_BIT != usBlack
    }

    private fun stepMoves(from: Int, targets: IntArray, usBlack: Int, moves: MutableList<Move>) {
        for (to in targets) {
            if (board[to] == 0 || isEnemy(to, usBlack)) moves += Move.of(from, to)
        }
    }

    private fun slideMoves(from: Int, dirs: IntRange, usBlack: Int, moves: MutableList<Move>) {
        for (dir in dirs) {
            for (to in Attacks.rays[dir][from]) {
                if (board[to] == 0) {
                    moves += Move.of(from, to)
                } else {
                    if (isEnemy(to, usBlack)) moves += Move.of(from, to)
                    break
                }
            }
        }
    }

    private fun pawnMoves(from: Int, us: Color, moves: MutableList<Move>) {
        val usBlack = if (us == Color.BLACK) BLACK_BIT else 0
        val forward = if (us == Color.WHITE) 8 else -8
        val startRank = if (us == Color.WHITE) 1 else 6
        val lastRank = if (us == Color.WHITE) 7 else 0
        val one = from + forward

        fun add(to: Int, flags: Int = 0) {
            if (to shr 3 == lastRank) {
                for (p in PROMOTIONS) moves += Move.of(from, to, p, flags)
            } else {
                moves += Move.of(from, to, null, flags)
            }
        }

        if (board[one] == 0) {
            add(one)
            val two = one + forward
            if (from shr 3 == startRank && board[two] == 0) moves += Move.of(from, two, flags = Move.DOUBLE_PUSH)
        }
        for (to in Attacks.pawn[if (us == Color.WHITE) 0 else 1][from]) {
            if (isEnemy(to, usBlack)) add(to)
            else if (to == enPassantSquare?.index) moves += Move.of(from, to, flags = Move.EN_PASSANT)
        }
    }

    private fun castlingMoves(us: Color, moves: MutableList<Move>) {
        val them = us.opposite
        val base = if (us == Color.WHITE) 0 else 56
        val kingside = if (us == Color.WHITE) WHITE_KINGSIDE else BLACK_KINGSIDE
        val queenside = if (us == Color.WHITE) WHITE_QUEENSIDE else BLACK_QUEENSIDE
        val king = codeOf(PieceType.KING, us)
        val rook = codeOf(PieceType.ROOK, us)
        if (board[base + 4] != king) return

        if (castlingRights and kingside != 0 &&
            board[base + 7] == rook && board[base + 5] == 0 && board[base + 6] == 0 &&
            !isAttacked(base + 4, them) && !isAttacked(base + 5, them) && !isAttacked(base + 6, them)
        ) {
            moves += Move.of(base + 4, base + 6, flags = Move.CASTLING)
        }
        if (castlingRights and queenside != 0 &&
            board[base] == rook && board[base + 1] == 0 && board[base + 2] == 0 && board[base + 3] == 0 &&
            !isAttacked(base + 4, them) && !isAttacked(base + 3, them) && !isAttacked(base + 2, them)
        ) {
            moves += Move.of(base + 4, base + 2, flags = Move.CASTLING)
        }
    }

    private fun playUnchecked(move: Move): Position {
        val b = board.copyOf()
        val from = move.from.index
        val to = move.to.index
        val moving = b[from]
        val captured = b[to] != 0 || move.isEnPassant

        b[to] = move.promotion?.let { codeOf(it, sideToMove) } ?: moving
        b[from] = 0
        if (move.isEnPassant) {
            // The captured pawn stands beside the moving pawn, on the square it skipped from.
            b[(from and 56) or (to and 7)] = 0
        }
        if (move.isCastling) {
            val kingside = to > from
            val rookFrom = if (kingside) from + 3 else from - 4
            val rookTo = if (kingside) from + 1 else from - 1
            b[rookTo] = b[rookFrom]
            b[rookFrom] = 0
        }

        return Position(
            board = b,
            sideToMove = sideToMove.opposite,
            castlingRights = castlingRights and CASTLING_MASK[from] and CASTLING_MASK[to],
            enPassantSquare = if (move.isDoublePush) Square((from + to) / 2) else null,
            halfmoveClock = if (captured || moving and TYPE_MASK == PAWN) 0 else halfmoveClock + 1,
            fullmoveNumber = if (sideToMove == Color.BLACK) fullmoveNumber + 1 else fullmoveNumber,
        )
    }

    companion object {
        const val WHITE_KINGSIDE = 1
        const val WHITE_QUEENSIDE = 2
        const val BLACK_KINGSIDE = 4
        const val BLACK_QUEENSIDE = 8

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        val start: Position by lazy { fromFen(START_FEN) }

        fun fromFen(fen: String): Position = Fen.read(fen)

        internal const val PAWN = 1
        internal const val KNIGHT = 2
        internal const val BISHOP = 3
        internal const val ROOK = 4
        internal const val QUEEN = 5
        internal const val KING = 6
        internal const val TYPE_MASK = 7
        internal const val BLACK_BIT = 8

        private val PROMOTIONS = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

        /** Castling rights that survive a move touching each square (king or rook squares). */
        private val CASTLING_MASK = IntArray(64) { 15 }.also {
            it[0] = 15 and WHITE_QUEENSIDE.inv()
            it[7] = 15 and WHITE_KINGSIDE.inv()
            it[4] = 15 and (WHITE_KINGSIDE or WHITE_QUEENSIDE).inv()
            it[56] = 15 and BLACK_QUEENSIDE.inv()
            it[63] = 15 and BLACK_KINGSIDE.inv()
            it[60] = 15 and (BLACK_KINGSIDE or BLACK_QUEENSIDE).inv()
        }

        internal fun codeOf(type: PieceType, color: Color): Int =
            (type.ordinal + 1) or (if (color == Color.BLACK) BLACK_BIT else 0)

        private val PIECES = Array(16) { c ->
            val t = c and TYPE_MASK
            if (t == 0 || t > 6) null
            else Piece(PieceType.entries[t - 1], if (c and BLACK_BIT != 0) Color.BLACK else Color.WHITE)
        }

        internal fun pieceOf(code: Int): Piece? = PIECES[code]
    }
}

/** Precomputed target squares per square. */
internal object Attacks {
    private fun targets(from: Int, deltas: List<Pair<Int, Int>>): IntArray {
        val f = from and 7
        val r = from shr 3
        return deltas.mapNotNull { (df, dr) ->
            val nf = f + df
            val nr = r + dr
            if (nf in 0..7 && nr in 0..7) nr * 8 + nf else null
        }.toIntArray()
    }

    val knight: Array<IntArray> = Array(64) {
        targets(it, listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2))
    }

    val king: Array<IntArray> = Array(64) {
        targets(it, listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1))
    }

    /** [0] = squares a White pawn on the square attacks, [1] = same for a Black pawn. */
    val pawn: Array<Array<IntArray>> = arrayOf(
        Array(64) { targets(it, listOf(-1 to 1, 1 to 1)) },
        Array(64) { targets(it, listOf(-1 to -1, 1 to -1)) },
    )

    /** Rays per direction: 0–3 orthogonal (N, E, S, W), 4–7 diagonal (NE, SE, SW, NW). */
    val rays: Array<Array<IntArray>> = listOf(
        0 to 1, 1 to 0, 0 to -1, -1 to 0,
        1 to 1, 1 to -1, -1 to -1, -1 to 1,
    ).map { (df, dr) ->
        Array(64) { from ->
            val squares = ArrayList<Int>(7)
            var f = (from and 7) + df
            var r = (from shr 3) + dr
            while (f in 0..7 && r in 0..7) {
                squares += r * 8 + f
                f += df
                r += dr
            }
            squares.toIntArray()
        }
    }.toTypedArray()
}
