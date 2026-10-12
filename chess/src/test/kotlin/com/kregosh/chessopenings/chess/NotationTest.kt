package com.kregosh.chessopenings.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotationTest {
    private fun line(vararg sans: String): Position =
        sans.fold(Position.start) { p, san -> p.play(San.parse(p, san) ?: error("Can't play $san in $p")) }

    private fun san(fen: String, uci: String): String {
        val p = Position.fromFen(fen)
        return San.write(p, Uci.parse(p, uci) ?: error("Illegal $uci"))
    }

    @Test
    fun plainMoves() {
        assertEquals("e4", San.write(Position.start, Uci.parse(Position.start, "e2e4")!!))
        assertEquals("Nf3", San.write(Position.start, Uci.parse(Position.start, "g1f3")!!))
        assertEquals("exd5", san("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2", "e4d5"))
    }

    @Test
    fun disambiguation() {
        // Knights on b1 and f3 can both reach d2: file decides.
        assertEquals("Nbd2", san("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1", "b1d2"))
        // Rooks on a1 and a7 can both reach a4: rank decides.
        assertEquals("R1a4", san("4k3/R7/8/8/8/8/8/R3K3 w - - 0 1", "a1a4"))
        // Queens on a1, a3 and c1 all reach b2: only the full square is unique for a1.
        assertEquals("Qa1b2", san("4k3/8/8/8/8/Q7/8/Q1Q1K3 w - - 0 1", "a1b2"))
        // A pinned rival doesn't need disambiguation: the knight on e2 can't move.
        assertEquals("Nc3", san("4k3/4r3/8/8/8/8/4N3/1N2K3 w - - 0 1", "b1c3"))
    }

    @Test
    fun promotionsCastlingAndChecks() {
        assertEquals("e8=Q+", san("8/4P3/8/8/k7/8/8/4K3 w - - 0 1", "e7e8q"))
        assertEquals("e8=Q", san("8/4P3/8/8/8/8/k7/4K3 w - - 0 1", "e7e8q"))
        assertEquals("exd8=N", san("3r4/4P3/8/8/8/8/k7/4K3 w - - 0 1", "e7d8n"))
        assertEquals("O-O", san("4k3/8/8/8/8/8/8/4K2R w K - 0 1", "e1g1"))
        assertEquals("O-O-O", san("4k3/8/8/8/8/8/8/R3K3 w Q - 0 1", "e1c1"))
        assertEquals("Qh4#", San.write(line("f3", "e5", "g4"), Uci.parse(line("f3", "e5", "g4"), "d8h4")!!))
    }

    @Test
    fun lenientParsing() {
        val p = line("e4", "e5")
        assertEquals("g1f3", San.parse(p, "Nf3!?")?.uci)
        assertEquals("e1g1", San.parse(Position.fromFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1"), "0-0")?.uci)
        assertEquals("e7e8q", San.parse(Position.fromFen("8/4P3/8/8/8/8/k7/4K3 w - - 0 1"), "e8Q")?.uci)
        assertNull(San.parse(p, "Ke3"))
        assertNull(San.parse(p, "Nf6"))
        assertNull(San.parse(p, ""))
        assertNull(Uci.parse(p, "e1e3"))
        assertNull(Uci.parse(p, "z9"))
    }

    @Test
    fun specialRules() {
        // En passant is generated, and removes the captured pawn.
        val ep = line("e4", "a6", "e5", "d5")
        val afterEp = ep.play(San.parse(ep, "exd6")!!)
        assertNull(afterEp.pieceAt(Square.parse("d5")))
        // En passant that would expose the king along the rank is illegal.
        assertNull(Uci.parse(Position.fromFen("8/8/8/K2pP2r/8/8/8/7k w - d6 0 1"), "e5d6"))
        // No castling through an attacked square.
        assertNull(Uci.parse(Position.fromFen("4k3/8/8/8/8/8/5r2/4K2R w K - 0 1"), "e1g1"))
        // Moving the king or rook loses the right; capturing a rook removes the opponent's.
        assertEquals("KQkq", line("e4", "e5").toFen().split(' ')[2])
        assertEquals("kq", line("e4", "e5", "Ke2").toFen().split(' ')[2])
        assertEquals("Qq", Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
            .let { it.play(Uci.parse(it, "h1h8")!!) }.toFen().split(' ')[2])
    }

    @Test
    fun gameEnd() {
        val mate = line("f3", "e5", "g4", "Qh4")
        assertTrue(mate.isCheckmate)
        assertTrue(mate.legalMoves.isEmpty())
        assertTrue(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").isStalemate)
    }

    @Test
    fun fen() {
        assertEquals(Position.START_FEN, Position.start.toFen())
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", line("e4").toFen())
        assertEquals(Position.START_FEN, Position.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -").toFen())
        for (bad in listOf(
            "",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1",          // 7 ranks
            "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", // 9 squares
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1BNR w kq - 0 1",   // no white king
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e4 0 1",
            "4k3/8/8/8/8/8/4R3/4K3 b - - 0 1".replace(" b ", " w "),   // side not to move in check
        )) {
            assertFailsWith<IllegalArgumentException>(bad) { Position.fromFen(bad) }
        }
    }

    @Test
    fun polyglotKeys() {
        // Test vectors from the Polyglot book format specification.
        val vectors = mapOf(
            "" to "463b96181691fc9c",
            "e2e4" to "823c9b50fd114196",
            "e2e4 d7d5" to "0756b94461c50fb0",
            "e2e4 d7d5 e4e5" to "662fafb965db29d4",
            "e2e4 d7d5 e4e5 f7f5" to "22a48b5a8e47ff78",
            "e2e4 d7d5 e4e5 f7f5 e1e2" to "652a607ca3f242c1",
            "e2e4 d7d5 e4e5 f7f5 e1e2 e8f7" to "00fdd303c946bdd9",
            "a2a4 b7b5 h2h4 b5b4 c2c4" to "3c8123ea7b067637",
            "a2a4 b7b5 h2h4 b5b4 c2c4 b4c3 a1a3" to "5c3f9b829b279560",
        )
        for ((moves, key) in vectors) {
            val p = moves.split(' ').filter { it.isNotEmpty() }.fold(Position.start) { p, uci -> p.play(Uci.parse(p, uci)!!) }
            assertEquals(key, p.key.toULong().toString(16).padStart(16, '0'), moves)
        }
        // Transpositions reach the same key.
        val a = listOf("Nf3", "d5", "d4").fold(Position.start) { p, s -> p.play(San.parse(p, s)!!) }
        val b = listOf("d4", "d5", "Nf3").fold(Position.start) { p, s -> p.play(San.parse(p, s)!!) }
        assertEquals(a.key, b.key)
    }
}
