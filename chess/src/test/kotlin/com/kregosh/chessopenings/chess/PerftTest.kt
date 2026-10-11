package com.kregosh.chessopenings.chess

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Counts leaf nodes of the legal move tree and compares them with the published values
 * (chessprogramming.org "Perft Results"). Nearly every move generation bug shows up here.
 */
class PerftTest {
    private fun perft(p: Position, depth: Int): Long {
        if (depth == 0) return 1
        if (depth == 1) return p.legalMoves.size.toLong()
        return p.legalMoves.sumOf { perft(p.play(it), depth - 1) }
    }

    private fun check(fen: String, vararg expected: Long) {
        val p = Position.fromFen(fen)
        expected.forEachIndexed { i, nodes -> assertEquals(nodes, perft(p, i + 1), "perft ${i + 1} of $fen") }
    }

    @Test
    fun startPosition() = check(Position.START_FEN, 20, 400, 8_902, 197_281)

    @Test
    fun kiwipete() = check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2_039, 97_862)

    @Test
    fun position3() = check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2_812, 43_238, 674_624)

    @Test
    fun position4() = check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9_467, 422_333)

    @Test
    fun position4Mirrored() = check("r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1", 6, 264, 9_467, 422_333)

    @Test
    fun position5() = check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1_486, 62_379)

    @Test
    fun position6() = check("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 46, 2_079, 89_890)

    /** The deeper counts take a few seconds each; they catch rarer bugs. */
    @Test
    fun deeper() {
        check(Position.START_FEN, 20, 400, 8_902, 197_281, 4_865_609)
        check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2_039, 97_862, 4_085_603)
        check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1_486, 62_379, 2_103_487)
    }
}
