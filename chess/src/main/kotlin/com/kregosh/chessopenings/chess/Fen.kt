package com.kregosh.chessopenings.chess

/** Forsyth–Edwards Notation. */
internal object Fen {
    fun read(fen: String): Position {
        val fields = fen.trim().split(Regex("\\s+"))
        require(fields.size == 4 || fields.size == 6) { "FEN needs 4 or 6 fields: $fen" }

        val board = IntArray(64)
        val ranks = fields[0].split('/')
        require(ranks.size == 8) { "FEN board needs 8 ranks: $fen" }
        for ((i, row) in ranks.withIndex()) {
            val rank = 7 - i
            var file = 0
            for (c in row) {
                if (c in '1'..'8') {
                    file += c - '0'
                } else {
                    val piece = Piece.fromFenChar(c) ?: throw IllegalArgumentException("Bad piece '$c' in FEN: $fen")
                    require(file < 8) { "Rank too long in FEN: $fen" }
                    board[rank * 8 + file] = Position.codeOf(piece.type, piece.color)
                    file++
                }
            }
            require(file == 8) { "Rank ${rank + 1} doesn't have 8 squares in FEN: $fen" }
        }
        for (color in Color.entries) {
            val king = Position.codeOf(PieceType.KING, color)
            require(board.count { it == king } == 1) { "FEN needs exactly one ${color.name.lowercase()} king: $fen" }
        }

        val side = when (fields[1]) {
            "w" -> Color.WHITE
            "b" -> Color.BLACK
            else -> throw IllegalArgumentException("Bad side to move in FEN: $fen")
        }

        var castling = 0
        if (fields[2] != "-") {
            for (c in fields[2]) {
                castling = castling or when (c) {
                    'K' -> Position.WHITE_KINGSIDE
                    'Q' -> Position.WHITE_QUEENSIDE
                    'k' -> Position.BLACK_KINGSIDE
                    'q' -> Position.BLACK_QUEENSIDE
                    else -> throw IllegalArgumentException("Bad castling field in FEN: $fen")
                }
            }
        }

        val ep = if (fields[3] == "-") {
            null
        } else {
            val sq = runCatching { Square.parse(fields[3]) }.getOrNull()
            require(sq != null && sq.rank == (if (side == Color.WHITE) 5 else 2)) { "Bad en passant square in FEN: $fen" }
            sq
        }

        val halfmove = if (fields.size == 6) fields[4].toIntOrNull() else 0
        val fullmove = if (fields.size == 6) fields[5].toIntOrNull() else 1
        require(halfmove != null && halfmove >= 0 && fullmove != null && fullmove >= 1) { "Bad move counters in FEN: $fen" }

        val position = Position(board, side, castling, ep, halfmove, fullmove)
        require(!position.isAttacked(position.kingSquare(side.opposite), side)) {
            "The side not to move is in check in FEN: $fen"
        }
        return position
    }

    fun write(p: Position): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val piece = p.pieceAt(Square.of(file, rank))
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) append(empty).also { empty = 0 }
                    append(piece.fenChar)
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(if (p.sideToMove == Color.WHITE) " w " else " b ")
        val c = p.castlingRights
        if (c == 0) append('-')
        if (c and Position.WHITE_KINGSIDE != 0) append('K')
        if (c and Position.WHITE_QUEENSIDE != 0) append('Q')
        if (c and Position.BLACK_KINGSIDE != 0) append('k')
        if (c and Position.BLACK_QUEENSIDE != 0) append('q')
        append(' ').append(p.enPassantSquare?.name ?: "-")
        append(' ').append(p.halfmoveClock).append(' ').append(p.fullmoveNumber)
    }
}
