package com.kregosh.chessopenings.chess

/** Standard Algebraic Notation, as used in PGN. */
object San {
    /** SAN of [move] in [position], with "+" or "#" when it gives check or mate. */
    fun write(position: Position, move: Move): String {
        val base = withoutSuffix(position, move)
        val next = position.play(move)
        return when {
            next.isCheckmate -> "$base#"
            next.isCheck -> "$base+"
            else -> base
        }
    }

    /**
     * The legal move [san] denotes in [position], or null if there is none. Accepts check
     * and annotation suffixes ("+", "#", "!", "?"), "0-0" for castling and promotions with
     * or without "=".
     */
    fun parse(position: Position, san: String): Move? {
        val wanted = normalize(san)
        if (wanted.isEmpty()) return null
        // Only moves to the named square (or castling moves) can match; this keeps parsing
        // fast when reading millions of games.
        val castling = wanted.startsWith("O-O")
        val target = if (castling) null else DESTINATION.findAll(wanted).lastOrNull()?.value ?: return null
        return position.legalMoves.firstOrNull {
            (if (castling) it.isCastling else it.to.name == target) && normalize(withoutSuffix(position, it)) == wanted
        }
    }

    private val DESTINATION = Regex("[a-h][1-8]")

    private fun normalize(san: String): String =
        san.trim().trimEnd('+', '#', '!', '?').replace("0", "O").replace("=", "")

    internal fun withoutSuffix(position: Position, move: Move): String {
        if (move.isCastling) return if (move.to.file == 6) "O-O" else "O-O-O"
        val piece = position.pieceAt(move.from) ?: error("No piece on ${move.from}")
        val capture = position.pieceAt(move.to) != null || move.isEnPassant
        return buildString {
            if (piece.type == PieceType.PAWN) {
                if (capture) append('a' + move.from.file)
            } else {
                append(piece.type.letter.uppercaseChar())
                append(disambiguation(position, move, piece.type))
            }
            if (capture) append('x')
            append(move.to.name)
            move.promotion?.let { append('=').append(it.letter.uppercaseChar()) }
        }
    }

    private fun disambiguation(position: Position, move: Move, type: PieceType): String {
        val rivals = position.legalMoves.filter {
            it.to == move.to && it.from != move.from && position.pieceAt(it.from)?.type == type
        }
        if (rivals.isEmpty()) return ""
        val sameFile = rivals.any { it.from.file == move.from.file }
        val sameRank = rivals.any { it.from.rank == move.from.rank }
        return when {
            !sameFile -> "${'a' + move.from.file}"
            !sameRank -> "${'1' + move.from.rank}"
            else -> move.from.name
        }
    }
}

/** Universal Chess Interface move notation ("e2e4", "e7e8q"). */
object Uci {
    fun parse(position: Position, uci: String): Move? {
        if (uci.length !in 4..5) return null
        val from = runCatching { Square.parse(uci.substring(0, 2)) }.getOrNull() ?: return null
        val to = runCatching { Square.parse(uci.substring(2, 4)) }.getOrNull() ?: return null
        val promotion = if (uci.length == 5) PieceType.fromLetter(uci[4]) ?: return null else null
        return position.findMove(from, to, promotion)
    }
}
