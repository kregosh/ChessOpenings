package com.kregosh.chessopenings.chess

import kotlin.jvm.JvmInline

enum class Color {
    WHITE,
    BLACK;

    val opposite: Color get() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val letter: Char) {
    PAWN('p'),
    KNIGHT('n'),
    BISHOP('b'),
    ROOK('r'),
    QUEEN('q'),
    KING('k');

    companion object {
        fun fromLetter(c: Char): PieceType? = entries.firstOrNull { it.letter == c.lowercaseChar() }
    }
}

data class Piece(val type: PieceType, val color: Color) {
    /** FEN letter: upper case for White, lower case for Black. */
    val fenChar: Char get() = if (color == Color.WHITE) type.letter.uppercaseChar() else type.letter

    companion object {
        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromLetter(c) ?: return null
            return Piece(type, if (c.isUpperCase()) Color.WHITE else Color.BLACK)
        }
    }
}

/** A board square, 0 = a1, 7 = h1, 56 = a8, 63 = h8. */
@JvmInline
value class Square(val index: Int) {
    init {
        require(index in 0..63) { "Square index out of range: $index" }
    }

    /** 0 = a-file, 7 = h-file. */
    val file: Int get() = index and 7

    /** 0 = first rank, 7 = eighth rank. */
    val rank: Int get() = index shr 3

    val name: String get() = "${'a' + file}${'1' + rank}"

    override fun toString(): String = name

    companion object {
        fun of(file: Int, rank: Int): Square = Square(rank * 8 + file)

        fun parse(name: String): Square {
            require(name.length == 2 && name[0] in 'a'..'h' && name[1] in '1'..'8') { "Not a square: $name" }
            return of(name[0] - 'a', name[1] - '1')
        }

        val all: List<Square> = (0..63).map(::Square)
    }
}

/**
 * A move, packed into an Int: from (6 bits), to (6 bits), promotion piece (3 bits) and
 * flags. Moves come from [Position.legalMoves]; the flags record how the move changes
 * the board beyond moving one piece.
 */
@JvmInline
value class Move internal constructor(val bits: Int) {
    val from: Square get() = Square(bits and 63)
    val to: Square get() = Square((bits shr 6) and 63)

    val promotion: PieceType?
        get() = ((bits shr 12) and 7).let { if (it == 0) null else PieceType.entries[it - 1] }

    val isCastling: Boolean get() = bits and CASTLING != 0
    val isEnPassant: Boolean get() = bits and EN_PASSANT != 0
    val isDoublePush: Boolean get() = bits and DOUBLE_PUSH != 0

    /** UCI notation, e.g. "e2e4", "e7e8q"; castling is the king's move ("e1g1"). */
    val uci: String get() = from.name + to.name + (promotion?.letter ?: "")

    override fun toString(): String = uci

    companion object {
        internal const val CASTLING = 1 shl 15
        internal const val EN_PASSANT = 1 shl 16
        internal const val DOUBLE_PUSH = 1 shl 17

        internal fun of(from: Int, to: Int, promotion: PieceType? = null, flags: Int = 0): Move =
            Move(from or (to shl 6) or ((promotion?.let { it.ordinal + 1 } ?: 0) shl 12) or flags)
    }
}
