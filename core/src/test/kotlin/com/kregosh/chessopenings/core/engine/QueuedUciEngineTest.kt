package com.kregosh.chessopenings.core.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// advanceUntilIdle() is still marked experimental in kotlinx-coroutines-test.
@OptIn(ExperimentalCoroutinesApi::class)
class QueuedUciEngineTest {
    /** Runs on the test scheduler (so advanceUntilIdle drives it) but is cancelled by [test]. */
    private fun test(body: suspend TestScope.(CoroutineScope) -> Unit) = runTest {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            body(scope)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun parsesFinalPvLinesOnly() {
        assertEquals("e2e4" to Score(31), QueuedUciEngine.parseInfo("info depth 12 multipv 1 score cp 31 nodes 1 pv e2e4 e7e5"))
        assertEquals("d1h5" to Score.mate(-2), QueuedUciEngine.parseInfo("info depth 5 score mate -2 pv d1h5 g7g6"))
        assertNull(QueuedUciEngine.parseInfo("info depth 12 score cp 31 lowerbound pv e2e4"))
        assertNull(QueuedUciEngine.parseInfo("info string NNUE loaded"))
        assertNull(QueuedUciEngine.parseInfo("bestmove e2e4"))
    }

    @Test
    fun handshakeThenOneSearchAtATime() = test { engineScope ->
        val sent = ArrayList<String>()
        lateinit var engine: QueuedUciEngine
        engine = QueuedUciEngine(engineScope) { cmd ->
            sent += cmd
            when {
                cmd == "uci" -> engine.lineReceived("uciok")
                cmd == "isready" -> engine.lineReceived("readyok")
                cmd.startsWith("go") -> {
                    engine.lineReceived("info depth 10 multipv 1 score cp 20 pv e2e4 e7e5")
                    engine.lineReceived("info depth 10 multipv 2 score cp 15 pv d2d4 d7d5")
                    engine.lineReceived("bestmove e2e4")
                }
            }
        }
        val a = async { engine.analyze("startpos-fen", 10, multiPv = 2) }
        val b = async { engine.analyze("other-fen", 8, searchMoves = listOf("g1f3")) }
        advanceUntilIdle()
        assertEquals(listOf(EngineLine("e2e4", Score(20)), EngineLine("d2d4", Score(15))), a.await())
        assertEquals(listOf(EngineLine("e2e4", Score(20))), b.await())
        assertEquals(
            listOf(
                "uci", "isready",
                "setoption name MultiPV value 2", "position fen startpos-fen", "go depth 10",
                "setoption name MultiPV value 1", "position fen other-fen", "go depth 8 searchmoves g1f3",
            ),
            sent,
        )
    }

    @Test
    fun aFailedEngineFailsItsRequests() = test { engineScope ->
        lateinit var engine: QueuedUciEngine
        engine = QueuedUciEngine(engineScope) { if (it == "uci") engine.failed("could not load") }
        val result = async { runCatching { engine.analyze("fen", 10) } }
        advanceUntilIdle()
        val error = result.await().exceptionOrNull()
        assertTrue(error?.message?.contains("could not load") == true, "$error")
        assertFailsWith<IllegalStateException> { engine.analyze("fen", 10) }
    }

    @Test
    fun cancelledRequestsAreSkipped() = test { engineScope ->
        val gos = ArrayList<String>()
        lateinit var engine: QueuedUciEngine
        var answer: (() -> Unit)? = null
        engine = QueuedUciEngine(engineScope) { cmd ->
            when {
                cmd == "uci" -> engine.lineReceived("uciok")
                cmd == "isready" -> engine.lineReceived("readyok")
                cmd.startsWith("go") -> {
                    gos += cmd
                    answer = { engine.lineReceived("info depth 1 score cp 1 pv a2a3"); engine.lineReceived("bestmove a2a3") }
                }
            }
        }
        val first = async { engine.analyze("one", 5) }
        val second = launch { engine.analyze("two", 5) }
        advanceUntilIdle()
        second.cancel() // queued behind the first: never searched
        answer!!()
        advanceUntilIdle()
        assertEquals("a2a3", first.await().single().move)
        assertEquals(listOf("go depth 5"), gos)
    }
}
