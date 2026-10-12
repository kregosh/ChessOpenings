package com.kregosh.chessopenings.bookgen

import com.kregosh.chessopenings.chess.Position
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UciEngineTest {
    private val fake = File(javaClass.getResource("/fake-uci.py")!!.toURI()).path

    @Test
    fun parsesScoresIgnoringBoundsAndConvertsMates() {
        UciEngine(listOf("python3", fake), depth = 5).use { engine ->
            val e = engine.evaluate(Position.START_FEN, listOf("e2e4", "d2d4", "g2g4"))
            assertEquals(35, e.best)
            assertEquals(mapOf("e2e4" to 35, "d2d4" to 15, "g2g4" to -UciEngine.MATE + 3), e.moves)
            assertEquals(0, e.loss("e2e4"))
            assertEquals(20, e.loss("d2d4"))
            assertEquals(null, e.loss("a2a3"))
        }
    }

    @Test
    fun includesTheBestMoveWhenNotACandidate() {
        UciEngine(listOf("python3", fake), depth = 5).use { engine ->
            // e2e4 (the full search's best) is scored along with the candidates.
            val e = engine.evaluate(Position.START_FEN, listOf("d2d4"))
            assertEquals(mapOf("d2d4" to 15), e.moves)
            assertEquals(35, e.best)
            assertEquals(20, e.loss("d2d4"))
        }
    }

    @Test
    fun evaluatesInParallel() {
        val jobs = List(20) { Position.START_FEN to listOf("e2e4", "d2d4") }
        val results = evaluateAll(jobs, threads = 3, newEngine = { UciEngine(listOf("python3", fake), depth = 5) })
        assertEquals(20, results.size)
        assertTrue(results.all { it.loss("e2e4") == 0 && it.loss("d2d4") == 20 })
    }

    /** Runs against a real engine when STOCKFISH names one (CI installs it). */
    @Test
    fun realStockfish() {
        val command = System.getenv("STOCKFISH")
        assumeTrue(!command.isNullOrBlank(), "STOCKFISH not set")
        UciEngine(command!!.split(' '), depth = 10).use { engine ->
            val e = engine.evaluate(Position.START_FEN, listOf("e2e4", "d2d4", "g2g4"))
            assertTrue(e.loss("e2e4")!! < 40, "1.e4 should be sound: $e")
            assertTrue(e.loss("g2g4")!! > e.loss("e2e4")!!, "1.g4 should be worse than 1.e4: $e")
        }
    }
}
