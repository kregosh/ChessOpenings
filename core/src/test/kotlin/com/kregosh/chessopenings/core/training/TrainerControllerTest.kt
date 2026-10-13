package com.kregosh.chessopenings.core.training

import com.kregosh.chessopenings.chess.Color
import com.kregosh.chessopenings.chess.PieceType
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import com.kregosh.chessopenings.core.book.OpeningBook
import com.kregosh.chessopenings.core.engine.EngineDepths
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainerControllerTest {
    private val engine = FakeEngine()

    private fun TestScope.controller(book: OpeningBook = testBook(), seed: Int = 1) =
        TrainerController(book, engine, this, Random(seed), EngineDepths(4, 4, 4), botDelayMs = 100)

    private fun TrainerController.move(san: String) {
        val m = San.parse(state.value.position, san) ?: error("Illegal $san in ${state.value.position}")
        dispatch(TrainerAction.PieceDropped(m.from, m.to))
    }

    private fun white(vararg line: String, options: SessionOptions = SessionOptions()) =
        TrainingSetup(Color.WHITE, uci(*line), options = options)

    private val TrainerController.s get() = state.value

    @Test
    fun startsAndBotRepliesFromTheBook() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4")))
        assertEquals(Phase.BOT_THINKING, c.s.phase)
        assertEquals(listOf(true), c.s.moves.map { it.startingLine })
        assertEquals("King's Pawn Game", c.s.openingName)
        advanceUntilIdle()
        assertEquals(Phase.YOUR_MOVE, c.s.phase)
        assertTrue(c.s.moves.last().san in setOf("e5", "c5"))
        assertFalse(c.s.moves.last().startingLine)
        assertEquals(0, c.s.userMovesPlayed)
        assertFalse(c.s.canUndo)
    }

    @Test
    fun specificReplyStartsWithTheUserToMove() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4", "c5")))
        assertEquals(Phase.YOUR_MOVE, c.s.phase)
        assertEquals("Sicilian Defense", c.s.openingName)
        assertEquals(Color.WHITE, c.s.orientation)
    }

    @Test
    fun bookMoveGetsNonBlockingFeedbackAndTheBotReplies() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4", "e5")))
        c.move("Nf3")
        advanceUntilIdle()
        val f = assertNotNull(c.s.feedback)
        assertEquals(Quality.BOOK, f.verdict.quality)
        assertFalse(f.blocking)
        assertEquals("Nc6", c.s.moves.last().san)
        assertEquals(Phase.YOUR_MOVE, c.s.phase)
        assertEquals(1, c.s.userMovesPlayed)
        assertEquals("King's Knight Opening", c.s.openingName)
    }

    @Test
    fun nonBookMoveWaitsForRetryAndCountsTheTakeback() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4", "e5")))
        engine.set(line("e4", "e5"), "Nf3", 40)
        engine.set(line("e4", "e5"), "Qh5", -60)
        c.move("Qh5")
        advanceUntilIdle()
        assertEquals(Phase.FEEDBACK, c.s.phase)
        val f = assertNotNull(c.s.feedback)
        assertTrue(f.blocking)
        assertEquals(Quality.MISTAKE, f.verdict.quality)
        assertEquals(listOf("Nf3", "Bc4"), f.verdict.bookMoves)
        assertTrue(ReasonKind.EARLY_QUEEN in f.verdict.reasons.map { it.kind })

        c.dispatch(TrainerAction.Retry)
        assertEquals(Phase.YOUR_MOVE, c.s.phase)
        assertEquals(line("e4", "e5"), c.s.position)
        assertNull(c.s.feedback)
        c.move("Nf3")
        advanceUntilIdle()
        val nf3 = c.s.moves.first { it.san == "Nf3" }
        assertEquals(1, nf3.takebacks)
        assertEquals(Quality.BOOK, nf3.verdict?.quality)
    }

    @Test
    fun continueKeepsTheMoveAndTheEngineTakesOverOutOfBook() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4", "e5")))
        c.move("Qh5")
        advanceUntilIdle()
        c.dispatch(TrainerAction.Continue)
        advanceUntilIdle()
        assertEquals(Phase.YOUR_MOVE, c.s.phase)
        assertEquals(4, c.s.moves.size) // e4 e5 Qh5 + the engine's reply
        assertEquals("Qh5", c.s.moves[2].san)
        assertFalse(c.s.feedback!!.blocking)
    }

    @Test
    fun pastTheBookOnlyRealErrorsStopTheUser() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4", "e5", "Nf3", "Nc6")))
        engine.set(line("e4", "e5", "Nf3", "Nc6"), "Bc4", 20) // better than Bb5's 0
        c.move("Bb5") // 20 cp worse: playable, and the book has nothing here
        advanceUntilIdle()
        assertEquals(Phase.YOUR_MOVE, c.s.phase)
        assertEquals(Quality.PLAYABLE, c.s.moves[4].verdict?.quality)
    }

    @Test
    fun undoTakesBackTheUsersMoveAndTheBotsReply() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4", "e5")))
        c.move("Nf3")
        advanceUntilIdle()
        assertTrue(c.s.canUndo)
        c.dispatch(TrainerAction.Undo)
        assertEquals(line("e4", "e5"), c.s.position)
        assertEquals(2, c.s.moves.size)
        assertEquals(0, c.s.userMovesPlayed)
        assertFalse(c.s.canUndo)
        c.dispatch(TrainerAction.Undo) // nothing left to undo: ignored
        assertEquals(2, c.s.moves.size)
        c.move("Bc4")
        advanceUntilIdle()
        assertEquals(1, c.s.moves[2].takebacks)
    }

    @Test
    fun endModeGradesEverythingInTheReport() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4", "e5", options = SessionOptions(moves = 2, feedback = FeedbackMode.END))))
        c.move("Nf3")
        advanceUntilIdle()
        assertNull(c.s.feedback)
        assertNull(c.s.moves[2].verdict)
        c.move("Bc4")
        advanceUntilIdle()
        assertEquals(Phase.REPORT, c.s.phase)
        val r = assertNotNull(c.s.report)
        assertEquals(2, r.userMoves)
        assertEquals(1, r.bookMoves) // Nf3 is book; Bc4 after Nc6 is past the book
        assertEquals(Quality.BOOK, r.moves[2].verdict?.quality)
        assertTrue(r.moves[4].verdict!!.pastBook)
        assertEquals(5, r.moves.size) // the session ends after the user's 2nd move
    }

    @Test
    fun blackWithARandomWhiteFirstMove() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(TrainingSetup(Color.BLACK, listOf("c7c5"), randomWhiteFirstMove = true)))
        assertEquals(listOf("e4", "c5"), c.s.moves.map { it.san })
        assertEquals(listOf(false, true), c.s.moves.map { it.byUser })
        assertEquals(Color.BLACK, c.s.orientation)
        assertEquals(Phase.BOT_THINKING, c.s.phase)
        advanceUntilIdle()
        assertEquals("Nf3", c.s.moves.last().san)
        assertEquals(Phase.YOUR_MOVE, c.s.phase)
    }

    @Test
    fun hintShowsBookAndEngineArrows() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4", "e5")))
        engine.set(line("e4", "e5"), "d4", 99) // the engine prefers a non-book move
        c.dispatch(TrainerAction.ShowHint)
        assertEquals(listOf("Nf3", "Bc4"), c.s.hint?.bookMoves)
        advanceUntilIdle()
        assertEquals("d4", c.s.hint?.engineMove)
        assertEquals(listOf(ArrowKind.BOOK, ArrowKind.BOOK, ArrowKind.ENGINE), c.s.arrows.map { it.kind })
        c.move("Nf3")
        advanceUntilIdle()
        assertTrue(c.s.moves[2].hintUsed)
        assertTrue(c.s.arrows.isEmpty())
    }

    @Test
    fun hintsCanBeSwitchedOff() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(white("e4", "e5", options = SessionOptions(hints = false))))
        c.dispatch(TrainerAction.ShowHint)
        assertNull(c.s.hint)
    }

    @Test
    fun tapToMoveAndPromotion() = runTest {
        val c = controller()
        // 1.a4 b5 2.axb5 a6 3.bxa6 Bb7 4.axb7 Nc6: White can promote on a8 or b8.
        c.dispatch(TrainerAction.Start(white("a4", "b5", "axb5", "a6", "bxa6", "Bb7", "axb7", "Nc6")))
        c.dispatch(TrainerAction.SquareTapped(sq("b7")))
        assertEquals(sq("b7"), c.s.selected)
        assertEquals(setOf(sq("a8"), sq("b8")), c.s.targets)
        c.dispatch(TrainerAction.SquareTapped(sq("a8")))
        assertEquals(sq("b7") to sq("a8"), c.s.pendingPromotion)
        c.dispatch(TrainerAction.PromotionChosen(PieceType.KNIGHT))
        assertNull(c.s.pendingPromotion)
        assertEquals("bxa8=N", c.s.moves[8].san)

        // Tapping an empty square or the selected piece again clears the selection.
        val d = controller()
        d.dispatch(TrainerAction.Start(white("e4", "e5")))
        d.dispatch(TrainerAction.SquareTapped(sq("g1")))
        assertEquals(setOf(sq("f3"), sq("h3"), sq("e2")), d.s.targets)
        d.dispatch(TrainerAction.SquareTapped(sq("g1")))
        assertNull(d.s.selected)
        d.dispatch(TrainerAction.SquareTapped(sq("e5"))) // opponent's piece: nothing
        assertNull(d.s.selected)
    }

    @Test
    fun evalBarIsFromWhitesPointOfView() = runTest {
        val c = controller()
        engine.score = { fen, _ -> if (fen.split(' ')[1] == "b") 50 else 0 } // Black to move: Black is better
        c.dispatch(TrainerAction.Start(white("e4", options = SessionOptions(evalBar = true))))
        advanceUntilIdle()
        assertNotNull(c.s.eval)
        // After the bot's reply White is to move and scores 0.
        assertEquals(0, c.s.eval!!.cp)
        // Playing Black after 1.e4: Black to move scores +50 for Black, so -50 for White.
        val d = controller()
        d.dispatch(TrainerAction.Start(TrainingSetup(Color.BLACK, uci("e4"), options = SessionOptions(evalBar = true))))
        advanceUntilIdle()
        assertEquals(Phase.YOUR_MOVE, d.s.phase)
        assertEquals(-50, d.s.eval!!.cp)
    }

    @Test
    fun badSetupsAndEngineFailuresShowAnError() = runTest {
        val c = controller()
        c.dispatch(TrainerAction.Start(TrainingSetup(Color.WHITE, listOf("e2e5"))))
        assertNotNull(c.s.error)
        c.dispatch(TrainerAction.Start(TrainingSetup(Color.BLACK, listOf("h7h5"), randomWhiteFirstMove = true)))
        assertNotNull(c.s.error)

        engine.fail = true
        c.dispatch(TrainerAction.Start(white("e4", "e5")))
        assertNull(c.s.error)
        c.move("Qh5")
        advanceUntilIdle()
        assertNotNull(c.s.error)
    }

    @Test
    fun restartAndQuit() = runTest {
        val c = controller()
        val setup = white("e4", "e5")
        c.dispatch(TrainerAction.Start(setup))
        c.move("Nf3")
        advanceUntilIdle()
        c.dispatch(TrainerAction.Restart)
        assertEquals(line("e4", "e5"), c.s.position)
        assertEquals(setup, c.s.setup)
        c.dispatch(TrainerAction.Quit)
        assertEquals(TrainerState(), c.s)
    }

    @Test
    fun wholeSessionAgainstTheRealBook() = runTest {
        val real = OpeningBook.decode(File("../data/book.bin").readBytes())
        val c = controller(real, seed = 7)
        c.dispatch(TrainerAction.Start(white("e4", options = SessionOptions(moves = 6))))
        var guard = 0
        while (c.s.phase != Phase.REPORT && guard++ < 50) {
            advanceUntilIdle()
            when (c.s.phase) {
                // Always play the most popular book move, or the first legal move past the book.
                Phase.YOUR_MOVE -> {
                    val p = c.s.position
                    val m = real.moves(p).filter { it.userBook }.maxByOrNull { it.strongGames }?.toMove(p) ?: p.legalMoves.first()
                    c.dispatch(TrainerAction.PieceDropped(m.from, m.to))
                }
                Phase.FEEDBACK -> c.dispatch(TrainerAction.Continue)
                else -> Unit
            }
        }
        val r = assertNotNull(c.s.report)
        assertEquals(6, r.userMoves)
        // The bot's book replies are all bot candidates of the real book.
        var p = Position.start
        for (m in r.moves) {
            val move = San.parse(p, m.san)!!
            if (!m.byUser && !m.startingLine && real.moves(p).any { it.botCandidate }) {
                assertTrue(real.moves(p).any { it.uci == move.uci && it.botCandidate }, "bot move ${m.san} in $p")
            }
            p = p.play(move)
        }
        assertTrue(r.bookMoves >= 3, "following the book should give book moves: $r")
    }
}
