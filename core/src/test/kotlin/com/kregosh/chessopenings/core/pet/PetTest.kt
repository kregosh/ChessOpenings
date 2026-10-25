package com.kregosh.chessopenings.core.pet

import com.kregosh.chessopenings.chess.Color
import com.kregosh.chessopenings.core.training.PlayedMove
import com.kregosh.chessopenings.core.training.Quality
import com.kregosh.chessopenings.core.training.Report
import com.kregosh.chessopenings.core.training.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetTest {
    private val hour = 3_600_000L

    /** Noon on local day [day], plus [hours]. */
    private fun at(day: Long, hours: Int = 0): Moment {
        val h = 12 + hours
        val d = day + Math.floorDiv(h, 24)
        return Moment(d * 24 * hour + Math.floorMod(h, 24) * hour, d, Math.floorMod(h, 24))
    }

    private fun move(q: Quality, pastBook: Boolean = false) = PlayedMove(
        san = "x", uci = "a2a3", color = Color.WHITE, byUser = true, startingLine = false,
        verdict = Verdict(q, if (pastBook) emptyList() else listOf("e4"), null, null, emptyList()),
    )

    private fun report(vararg moves: PlayedMove, takebacks: Int = 0, hints: Int = 0) = Report(
        moves = moves.toList(),
        userMoves = moves.size,
        bookMoves = moves.count { it.verdict!!.inBook },
        bookPositions = moves.count { !it.verdict!!.pastBook },
        takebacks = takebacks,
        hintsUsed = hints,
        openingName = null,
        finalEval = null,
    )

    private val perfect = report(move(Quality.BOOK), move(Quality.BOOK), move(Quality.BOOK))

    @Test
    fun needsRunDownOverTime() {
        val start = PetRules.elapse(PetState(), at(10)) // a new pup starts the clock
        assertEquals(PetState.START_FOOD, start.food)
        val later = PetRules.elapse(start, at(10, hours = 6))
        assertEquals(PetState.START_FOOD - 6 * PetRules.FOOD_PER_HOUR, later.food, 1e-9)
        assertEquals(PetState.START_HAPPINESS - 6 * PetRules.HAPPINESS_PER_HOUR, later.happiness, 1e-9)
        val muchLater = PetRules.elapse(later, at(20))
        assertEquals(0.0, muchLater.food)
        assertEquals(0.0, muchLater.happiness)
    }

    @Test
    fun anySessionFeedsButGoodOnesCheerMore() {
        val hungry = PetRules.elapse(PetState(food = 0.1, happiness = 0.2), at(10))
        val (great, t1) = PetRules.feed(hungry, perfect, at(10))
        val (rough, t2) = PetRules.feed(hungry, report(move(Quality.BLUNDER), move(Quality.MISTAKE)), at(10))
        assertEquals(SessionGrade.GREAT, t1.grade)
        assertEquals(SessionGrade.ROUGH, t2.grade)
        assertEquals(great.food, rough.food, 1e-9) // the bowl is just as full
        assertEquals(0.1 + PetRules.MEAL, great.food, 1e-9)
        assertTrue(great.happiness > rough.happiness)
        assertTrue(rough.happiness > hungry.happiness)
    }

    @Test
    fun grades() {
        assertEquals(SessionGrade.GREAT, PetRules.grade(perfect))
        assertEquals(SessionGrade.GOOD, PetRules.grade(report(move(Quality.BOOK), move(Quality.BOOK), move(Quality.BOOK), takebacks = 1)))
        assertEquals(SessionGrade.GOOD, PetRules.grade(report(move(Quality.BOOK), move(Quality.BOOK), move(Quality.BOOK), move(Quality.PLAYABLE))))
        assertEquals(SessionGrade.OK, PetRules.grade(report(move(Quality.BOOK), move(Quality.MISTAKE))))
        assertEquals(SessionGrade.ROUGH, PetRules.grade(report()))
        // Moves past the book don't count against the book share.
        assertEquals(SessionGrade.GREAT, PetRules.grade(report(move(Quality.BOOK), move(Quality.PLAYABLE, pastBook = true))))
    }

    @Test
    fun streakCountsDaysInARow() {
        var s = PetState()
        s = PetRules.feed(s, perfect, at(10)).first
        assertEquals(1, s.streak)
        s = PetRules.feed(s, perfect, at(10, hours = 3)).first // same day: no change
        assertEquals(1, s.streak)
        val (next, treat) = PetRules.feed(s, perfect, at(11))
        assertEquals(2, next.streak)
        assertTrue(treat.streakGrew)
        assertEquals(2, PetRules.streak(next, at(12))) // still alive the next day
        assertEquals(0, PetRules.streak(next, at(13))) // a whole day missed
        val restarted = PetRules.feed(next, perfect, at(14)).first
        assertEquals(1, restarted.streak)
        assertEquals(2, restarted.longestStreak)
    }

    private val perfectSeven = report(*Array(7) { move(Quality.BOOK) })

    @Test
    fun threeSessionsFillTheBowl() {
        var s = PetState()
        repeat(2) { s = PetRules.feed(s, perfect, at(10)).first }
        assertTrue(s.food < 1.0)
        s = PetRules.feed(s, perfect, at(10)).first
        assertEquals(1.0, s.food, 1e-9)
    }

    @Test
    fun aboutTenPerfectLongSessionsMakeThePupHappy() {
        var s = PetState()
        repeat(8) { s = PetRules.feed(s, perfectSeven, at(10)).first }
        assertTrue(PetRules.mood(s, at(10)) != Mood.HAPPY)
        repeat(2) { s = PetRules.feed(s, perfectSeven, at(10)).first }
        assertEquals(Mood.HAPPY, PetRules.mood(s, at(10)))
        assertEquals(1.0, s.happiness, 1e-9)
    }

    @Test
    fun shortAndWeakSessionsCountForLess() {
        val start = PetState()
        val long = PetRules.feed(start, perfectSeven, at(10)).second.happiness
        val short = PetRules.feed(start, perfect, at(10)).second.happiness // 3 moves
        val rough = PetRules.feed(start, report(*Array(7) { move(Quality.BLUNDER) }), at(10)).second.happiness
        assertEquals(PetRules.BEST_JOY, long, 1e-9)
        assertEquals(PetRules.BEST_JOY * 3 / 7, short, 1e-9)
        assertTrue(rough in 0.0..short)
    }

    @Test
    fun moods() {
        val fed = PetState(food = 1.0, happiness = 0.95, updatedAt = at(10).epochMs, lastFedDay = 10, streak = 1)
        assertEquals(Mood.HAPPY, PetRules.mood(fed, at(10)))
        assertEquals(Mood.HUNGRY, PetRules.mood(fed, at(11, hours = 7))) // 31 hours later
        assertEquals(Mood.SICK, PetRules.mood(fed, at(13)))
        assertEquals(Mood.CONTENT, PetRules.mood(PetState(), at(10))) // a new pup
        assertEquals(Mood.SAD, PetRules.mood(PetState(food = 1.0, happiness = 0.1, updatedAt = at(10).epochMs), at(10)))
        assertEquals(Mood.BORED, PetRules.mood(PetState(food = 1.0, happiness = 0.2, updatedAt = at(10).epochMs), at(10)))
        assertEquals(Mood.CONTENT, PetRules.mood(PetState(food = 1.0, happiness = 0.6, updatedAt = at(10).epochMs), at(10)))
    }

    @Test
    fun sleepsAtNightUnlessTheUserIsAround() {
        val s = PetRules.feed(PetState(), perfect, at(10)).first
        assertEquals(Mood.ASLEEP, PetRules.mood(s, at(10, hours = 11))) // 23:00, hours after the session
        val justPlayed = PetRules.feed(s, perfect, at(10, hours = 11)).first
        assertTrue(PetRules.mood(justPlayed, at(10, hours = 11)) != Mood.ASLEEP)
    }

    @Test
    fun pattingHelpsALittle() {
        val s = PetState(happiness = 0.1, updatedAt = at(10).epochMs)
        val patted = PetRules.pat(s, at(10))
        assertEquals(0.1 + PetRules.PAT, patted.happiness, 1e-9)
        var many = s
        repeat(100) { many = PetRules.pat(many, at(10)) }
        assertEquals(PetRules.PAT_LIMIT, many.happiness, 1e-9)
        val happy = PetState(happiness = 0.9, updatedAt = at(10).epochMs)
        assertEquals(0.9, PetRules.pat(happy, at(10)).happiness, 1e-9)
    }

    @Test
    fun codecRoundTripsAndRejectsJunk() {
        val s = PetState(0.25, 0.75, 123, 456, 20_000, 3, 7)
        assertEquals(s, PetCodec.decode(PetCodec.encode(s)))
        assertEquals(PetState(), PetCodec.decode(PetCodec.encode(PetState())))
        assertNull(PetCodec.decode("garbage"))
        assertNull(PetCodec.decode("1;x;0;0;0;;0;0"))
        assertNull(PetCodec.decode("2;0.5;0.5;0;0;;0;0"))
    }

    @Test
    fun controllerSavesEveryChange() {
        val store = object : PetStore {
            var data: String? = null
            override fun load() = data
            override fun save(data: String) { this.data = data }
        }
        var now = at(10)
        val c = PetController(store) { now }
        val treat = c.sessionFinished(perfect)
        assertEquals(SessionGrade.GREAT, treat.grade)
        assertNotNull(store.data)
        assertTrue(c.view.value.fedToday)
        assertEquals(1, c.view.value.streak)

        // A new controller (the next visit) picks the pup up where it was.
        now = at(11)
        val again = PetController(store) { now }
        assertFalse(again.view.value.fedToday)
        assertEquals(1, again.view.value.streak)
        assertEquals(PetCodec.decode(store.data!!)!!.lastFedDay, 10)
    }
}
