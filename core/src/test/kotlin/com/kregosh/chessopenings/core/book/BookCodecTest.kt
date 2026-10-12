package com.kregosh.chessopenings.core.book

import com.kregosh.chessopenings.chess.PieceType
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import com.kregosh.chessopenings.chess.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BookCodecTest {
    private fun move(uci: String, strong: Int = 10, club: Int = 20, loss: Int? = 5, user: Boolean = true, bot: Boolean = true) =
        BookMove(
            from = Square.parse(uci.substring(0, 2)),
            to = Square.parse(uci.substring(2, 4)),
            promotion = uci.getOrNull(4)?.let { PieceType.fromLetter(it) },
            strongGames = strong, clubGames = club, lossCp = loss, userBook = user, botCandidate = bot,
        )

    private val afterE4 = Position.start.play(San.parse(Position.start, "e4")!!)

    private val book = OpeningBook(
        metadata = mapOf("source" to "test – ünïcode", "depth" to "16"),
        positions = mapOf(
            Position.start.key to listOf(move("e2e4", 5000, 9000, 0), move("d2d4", 4000, 6000, 3, bot = false)),
            afterE4.key to listOf(move("c7c5", loss = null, user = false)),
            -1L to listOf(move("a7a8q", 1, 2, 65_534), move("b7b8n", 0, 0, 0, user = false, bot = false)),
        ),
        names = mapOf(afterE4.key to "King's Pawn Game"),
    )

    @Test
    fun roundTrip() {
        val decoded = OpeningBook.decode(BookCodec.encode(book))
        assertEquals(book.metadata, decoded.metadata)
        assertEquals(book.names, decoded.names)
        assertEquals(book.entries().toList(), decoded.entries().toList())
        assertEquals("King's Pawn Game", decoded.name(afterE4))
        assertEquals(listOf("e2e4", "d2d4"), decoded.moves(Position.start).map { it.uci })
        assertEquals(PieceType.QUEEN, decoded.moves(-1L)[0].promotion)
        assertEquals(PieceType.KNIGHT, decoded.moves(-1L)[1].promotion)
        assertEquals(null, decoded.moves(afterE4)[0].lossCp)
        assertTrue(decoded.moves(12345L).isEmpty())
    }

    @Test
    fun movesResolveToLegalMoves() {
        val e4 = book.moves(Position.start).first().toMove(Position.start)
        assertEquals("e2e4", e4?.uci)
        assertEquals(null, move("e2e5").toMove(Position.start))
    }

    @Test
    fun lookupIsSortedBinarySearch() {
        val many = OpeningBook(emptyMap(), (1..1000L).associate { it * 7919 to listOf(move("e2e4", strong = it.toInt())) }, emptyMap())
        val decoded = OpeningBook.decode(BookCodec.encode(many))
        for (k in 1..1000L) assertEquals(k.toInt(), decoded.moves(k * 7919).single().strongGames)
        assertTrue(decoded.moves(5L).isEmpty())
    }

    @Test
    fun rejectsDamagedFiles() {
        val bytes = BookCodec.encode(book)
        assertFailsWith<IllegalArgumentException> { OpeningBook.decode(bytes.copyOf(bytes.size - 3)) }
        assertFailsWith<IllegalArgumentException> { OpeningBook.decode(bytes + 0) }
        assertFailsWith<IllegalArgumentException> { OpeningBook.decode(byteArrayOf(1, 2, 3, 4, 0, 1)) }
        val wrongVersion = bytes.copyOf().also { it[5] = 99 }
        assertFailsWith<IllegalArgumentException> { OpeningBook.decode(wrongVersion) }
    }
}
