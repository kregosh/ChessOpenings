package com.kregosh.chessopenings.core.training

import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {
    private val book = testBook()

    private fun reasons(vararg sans: String): Set<ReasonKind> {
        val history = ArrayList<Pair<Position, com.kregosh.chessopenings.chess.Move>>()
        var p = Position.start
        for (s in sans.dropLast(1)) {
            val m = San.parse(p, s)!!
            history += p to m
            p = p.play(m)
        }
        return Explainer.explain(p, San.parse(p, sans.last())!!, history).map { it.kind }.toSet()
    }

    @Test
    fun explainsGoodOpeningMoves() {
        assertEquals(setOf(ReasonKind.CENTER_PAWN), reasons("e4"))
        assertEquals(setOf(ReasonKind.DEVELOPS_PIECE), reasons("Nf3"))
        assertEquals(setOf(ReasonKind.CASTLES), reasons("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "O-O"))
        assertTrue(ReasonKind.CAPTURES in reasons("e4", "d5", "exd5"))
        assertTrue(ReasonKind.GIVES_CHECK in reasons("e4", "d5", "Bb5+"))
    }

    @Test
    fun explainsBeginnerMistakes() {
        assertTrue(ReasonKind.EARLY_QUEEN in reasons("e4", "e5", "Qh5"))
        assertTrue(ReasonKind.SAME_PIECE_TWICE in reasons("Nf3", "d5", "Ng5"))
        assertTrue(ReasonKind.BLOCKS_BISHOP in reasons("d4", "d5", "Nd2"))
        assertTrue(ReasonKind.LOSES_CASTLING in reasons("e4", "e5", "Ke2"))
        assertTrue(ReasonKind.HANGS_PIECE in reasons("e4", "e5", "Ba6"))
        assertTrue(ReasonKind.FLANK_PAWN in reasons("h4"))
        // Not mistakes: recapturing with a moved piece, castling, a defended piece.
        assertTrue(ReasonKind.SAME_PIECE_TWICE !in reasons("e4", "d5", "exd5", "Qxd5", "Nc3", "Qa5"))
        assertTrue(ReasonKind.LOSES_CASTLING !in reasons("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "O-O"))
        assertTrue(ReasonKind.HANGS_PIECE !in reasons("e4", "e5", "Nf3"))
    }

    @Test
    fun negativeReasonsComeFirst() {
        val p = line("e4", "e5")
        val kinds = Explainer.explain(p, San.parse(p, "Qh5")!!).map { it.kind }
        assertTrue(kinds.isNotEmpty() && !kinds.first().positive)
    }

    @Test
    fun qualityThresholds() {
        assertEquals(Quality.PLAYABLE, Quality.fromLoss(0))
        assertEquals(Quality.PLAYABLE, Quality.fromLoss(30))
        assertEquals(Quality.INACCURACY, Quality.fromLoss(31))
        assertEquals(Quality.MISTAKE, Quality.fromLoss(200))
        assertEquals(Quality.BLUNDER, Quality.fromLoss(201))
    }

    @Test
    fun gradesBookMovesWithoutTheEngine() = runTest {
        val engine = FakeEngine()
        val p = line("e4", "e5")
        val v = Grader(book, engine, 10).grade(p, San.parse(p, "Bc4")!!, emptyList())
        assertEquals(Quality.BOOK, v.quality)
        assertEquals(listOf("Nf3", "Bc4"), v.bookMoves)
        assertEquals("Nf3", v.bestMove)
        assertEquals(15, v.lossCp)
        assertTrue(engine.calls.isEmpty())
        assertTrue(v.reasons.all { it.kind.positive })
    }

    @Test
    fun gradesOtherMovesByEngineLoss() = runTest {
        val p = line("e4", "e5")
        val engine = FakeEngine().apply {
            set(p, "Nf3", 40)
            set(p, "Qh5", -60)
            set(p, "Ke2", -300)
        }
        val grader = Grader(book, engine, 10)
        val qh5 = grader.grade(p, San.parse(p, "Qh5")!!, emptyList())
        assertEquals(Quality.MISTAKE, qh5.quality) // 100 cp
        assertEquals(100, qh5.lossCp)
        assertEquals("Nf3", qh5.bestMove)
        assertTrue(ReasonKind.EARLY_QUEEN in qh5.reasons.map { it.kind })
        assertEquals(Quality.BLUNDER, grader.grade(p, San.parse(p, "Ke2")!!, emptyList()).quality)

        // Past the end of the book: graded by the engine alone.
        val deep = line("e4", "e5", "Nf3", "Nc6")
        val v = grader.grade(deep, San.parse(deep, "Bb5")!!, emptyList())
        assertTrue(v.pastBook)
        assertEquals(Quality.PLAYABLE, v.quality)
    }

    @Test
    fun botPicksByClubPopularity() {
        val policy = BotPolicy(book, Random(1))
        val counts = HashMap<String, Int>()
        repeat(10_000) { counts.merge(policy.bookMove(Position.start)!!.uci, 1, Int::plus) }
        // Club games: e4 700, d4 200, b3 100.
        assertEquals(0.7, counts.getValue("e2e4") / 10_000.0, 0.03)
        assertEquals(0.2, counts.getValue("d2d4") / 10_000.0, 0.03)
        assertEquals(0.1, counts.getValue("b2b3") / 10_000.0, 0.03)
        // The bot also plays its bot-only moves (Qh5), never user-only ones (Nf6 after 1.d4).
        val afterD4 = HashSet<String>()
        repeat(200) { afterD4 += policy.bookMove(line("d4"))!!.uci }
        assertEquals(setOf("d7d5"), afterD4)
        assertNull(policy.bookMove(line("e4", "e5", "Nf3", "Nc6")))
    }

    @Test
    fun whiteFirstMoveFitsBlacksReply() {
        val policy = BotPolicy(book, Random(2))
        // ...e5 is book after 1.e4 and 1.b3; ...c5 only after 1.e4; ...Nf6 only after 1.d4.
        val forE5 = HashSet<String>()
        repeat(200) { forE5 += policy.whiteFirstMove("e7e5")!!.uci }
        assertEquals(setOf("e2e4", "b2b3"), forE5)
        assertEquals("e2e4", policy.whiteFirstMove("c7c5")!!.uci)
        assertEquals("d2d4", policy.whiteFirstMove("g8f6")!!.uci)
        assertNull(policy.whiteFirstMove("h7h5"))
    }

    @Test
    fun setupCatalog() {
        val catalog = SetupCatalog(book)
        assertEquals(listOf("e4", "d4"), catalog.userMoves(emptyList()).map { it.san })
        assertEquals(2.0 / 3, catalog.userMoves(emptyList())[0].share, 0.001)
        assertEquals(listOf("e5", "c5"), catalog.opponentReplies(uci("e4")).map { it.san })
        assertEquals("Sicilian Defense", catalog.opponentReplies(uci("e4"))[1].name)
        // Black's first moves across White's: c5 (400) > e5 (300 + 10) > Nf6 (250) > d5 (200).
        assertEquals(listOf("c5", "e5", "Nf6", "d5"), catalog.blackFirstMoves().map { it.san })
        assertEquals(listOf("e4", "b3"), catalog.whiteFirstMovesFor("e7e5").map { it.san })
        assertTrue(catalog.userMoves(listOf("e2e5")).isEmpty())
    }
}
