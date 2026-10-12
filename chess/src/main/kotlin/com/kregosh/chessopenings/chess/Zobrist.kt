package com.kregosh.chessopenings.chess

/** Position keys exactly as the Polyglot book format defines them. */
internal object Zobrist {
    private const val CASTLING = 768
    private const val EN_PASSANT = 772
    private const val WHITE_TO_MOVE = 780

    fun key(p: Position): Long {
        var key = 0L
        for (sq in 0 until 64) {
            val code = p.code(sq)
            if (code == 0) continue
            val type = (code and Position.TYPE_MASK) - 1
            val white = if (code and Position.BLACK_BIT == 0) 1 else 0
            // Polyglot piece kinds: black pawn 0, white pawn 1, black knight 2, … white king 11.
            key = key xor POLYGLOT_RANDOM[64 * (2 * type + white) + sq]
        }
        if (p.castlingRights and Position.WHITE_KINGSIDE != 0) key = key xor POLYGLOT_RANDOM[CASTLING]
        if (p.castlingRights and Position.WHITE_QUEENSIDE != 0) key = key xor POLYGLOT_RANDOM[CASTLING + 1]
        if (p.castlingRights and Position.BLACK_KINGSIDE != 0) key = key xor POLYGLOT_RANDOM[CASTLING + 2]
        if (p.castlingRights and Position.BLACK_QUEENSIDE != 0) key = key xor POLYGLOT_RANDOM[CASTLING + 3]
        p.enPassantSquare?.let { ep -> if (canCaptureEnPassant(p, ep)) key = key xor POLYGLOT_RANDOM[EN_PASSANT + ep.file] }
        if (p.sideToMove == Color.WHITE) key = key xor POLYGLOT_RANDOM[WHITE_TO_MOVE]
        return key
    }

    /** Polyglot only hashes the en passant file if a pawn of the side to move stands ready to capture. */
    private fun canCaptureEnPassant(p: Position, ep: Square): Boolean {
        val pawn = Position.codeOf(PieceType.PAWN, p.sideToMove)
        val rank = if (p.sideToMove == Color.WHITE) 4 else 3
        return (ep.file > 0 && p.code(rank * 8 + ep.file - 1) == pawn) ||
            (ep.file < 7 && p.code(rank * 8 + ep.file + 1) == pawn)
    }
}
