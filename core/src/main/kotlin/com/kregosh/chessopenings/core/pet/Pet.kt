package com.kregosh.chessopenings.core.pet

import com.kregosh.chessopenings.core.training.Quality
import com.kregosh.chessopenings.core.training.Report

/** A point in time as the platform sees it: epoch milliseconds plus the user's local calendar day and hour. */
data class Moment(val epochMs: Long, val localDay: Long, val localHour: Int)

/** How the pup feels; the screens pick its sprite and words from this. */
enum class Mood { HAPPY, CONTENT, BORED, HUNGRY, SAD, SICK, ASLEEP }

/** How well a session went, for the pup's reaction to it. */
enum class SessionGrade { GREAT, GOOD, OK, ROUGH }

/**
 * The pup's needs and the training streak. [food] and [happiness] run from 0 to 1 and
 * slowly drop while the user is away; any finished session refills [food], and good
 * ones add more [happiness].
 */
data class PetState(
    val food: Double = START_FOOD,
    val happiness: Double = START_HAPPINESS,
    /** When the needs were last brought up to date; 0 for a brand-new pup. */
    val updatedAt: Long = 0,
    /** When the user last did something with the pup: trained, or petted it. */
    val activeAt: Long = 0,
    /** The local day of the last finished session; null if never fed. */
    val lastFedDay: Long? = null,
    /** Days in a row with a finished session, up to [lastFedDay]. */
    val streak: Int = 0,
    val longestStreak: Int = 0,
) {
    companion object {
        const val START_FOOD = 0.4
        const val START_HAPPINESS = 0.35
    }
}

/** What a finished session did for the pup. */
data class Treat(val grade: SessionGrade, val food: Double, val happiness: Double, val streak: Int, val streakGrew: Boolean)

/** The pup's rules. Pure: time comes in as a [Moment]. */
object PetRules {
    /** A full bowl lasts about a day and a half (three sessions a day keep it full); happiness about three weeks. */
    const val FOOD_PER_HOUR = 1.0 / 40
    const val HAPPINESS_PER_HOUR = 1.0 / 480
    /** Any session: three of them fill the bowl of a new pup. */
    const val MEAL = 0.2
    /**
     * Happiness for a perfect session of at least [FULL_SESSION_MOVES] of the user's moves:
     * about ten of them take a new pup to full happiness.
     */
    const val BEST_JOY = 0.065
    const val FULL_SESSION_MOVES = 7
    /** Happiness from which the pup is happy rather than content. */
    const val HAPPY_FROM = 0.9
    const val PAT = 0.01
    /** Petting alone cheers the pup up only this far; training does the rest. */
    const val PAT_LIMIT = 0.4
    /** Days without a session after which the pup gets sick. */
    const val SICK_AFTER_DAYS = 3
    /** The pup sleeps at night unless the user was just with it. */
    val NIGHT_HOURS = (22..23) + (0..6)
    const val AWAKE_AFTER_ACTIVITY_MS = 30 * 60 * 1000L

    /** [state] with its needs run down to [now]. */
    fun elapse(state: PetState, now: Moment): PetState {
        if (state.updatedAt == 0L) return state.copy(updatedAt = now.epochMs)
        val hours = (now.epochMs - state.updatedAt).coerceAtLeast(0) / 3_600_000.0
        return state.copy(
            food = (state.food - hours * FOOD_PER_HOUR).coerceIn(0.0, 1.0),
            happiness = (state.happiness - hours * HAPPINESS_PER_HOUR).coerceIn(0.0, 1.0),
            updatedAt = maxOf(now.epochMs, state.updatedAt),
        )
    }

    fun grade(report: Report): SessionGrade {
        val user = report.moves.filter { it.byUser && !it.startingLine }
        val errors = user.count { (it.verdict?.quality ?: Quality.BOOK) >= Quality.MISTAKE }
        val bookShare = if (report.bookPositions == 0) 1.0 else report.bookMoves.toDouble() / report.bookPositions
        return when {
            user.isEmpty() -> SessionGrade.ROUGH
            bookShare >= 1.0 && errors == 0 && report.takebacks == 0 && report.hintsUsed == 0 -> SessionGrade.GREAT
            bookShare >= 0.75 && errors == 0 -> SessionGrade.GOOD
            bookShare >= 0.5 && errors <= 1 -> SessionGrade.OK
            else -> SessionGrade.ROUGH
        }
    }

    /** Feeds the pup for a finished session: any session fills its bowl, good ones cheer it up more. */
    fun feed(state: PetState, report: Report, now: Moment): Pair<PetState, Treat> {
        val s = elapse(state, now)
        val grade = grade(report)
        // Better and longer sessions make the pup happier; short ones count for less.
        val length = minOf(report.userMoves, FULL_SESSION_MOVES).toDouble() / FULL_SESSION_MOVES
        val joy = BEST_JOY * length * when (grade) {
            SessionGrade.GREAT -> 1.0
            SessionGrade.GOOD -> 0.6
            SessionGrade.OK -> 0.3
            SessionGrade.ROUGH -> 0.15
        }
        val streak = when (s.lastFedDay) {
            now.localDay -> s.streak
            now.localDay - 1 -> s.streak + 1
            else -> 1
        }
        val fed = s.copy(
            food = (s.food + MEAL).coerceAtMost(1.0),
            happiness = (s.happiness + joy).coerceAtMost(1.0),
            activeAt = now.epochMs,
            lastFedDay = now.localDay,
            streak = streak,
            longestStreak = maxOf(s.longestStreak, streak),
        )
        return fed to Treat(grade, fed.food - s.food, fed.happiness - s.happiness, streak, streak > s.streak)
    }

    /** The user petted the pup. */
    fun pat(state: PetState, now: Moment): PetState {
        val s = elapse(state, now)
        val happiness = if (s.happiness >= PAT_LIMIT) s.happiness else minOf(s.happiness + PAT, PAT_LIMIT)
        return s.copy(happiness = happiness, activeAt = now.epochMs)
    }

    fun mood(state: PetState, now: Moment): Mood {
        val s = elapse(state, now)
        val daysSinceFed = s.lastFedDay?.let { now.localDay - it }
        return when {
            daysSinceFed != null && daysSinceFed >= SICK_AFTER_DAYS -> Mood.SICK
            now.localHour in NIGHT_HOURS && now.epochMs - s.activeAt > AWAKE_AFTER_ACTIVITY_MS -> Mood.ASLEEP
            s.food < 0.25 -> Mood.HUNGRY
            s.happiness < 0.15 -> Mood.SAD
            s.happiness < 0.3 -> Mood.BORED
            s.happiness >= HAPPY_FROM && s.food >= 0.5 -> Mood.HAPPY
            else -> Mood.CONTENT
        }
    }

    /** The streak as it stands [now]: 0 once a whole day was missed. */
    fun streak(state: PetState, now: Moment): Int {
        val last = state.lastFedDay ?: return 0
        return if (now.localDay - last <= 1) state.streak else 0
    }

    /** Whether the pup was already fed today. */
    fun fedToday(state: PetState, now: Moment): Boolean = state.lastFedDay == now.localDay
}

/** Where the pup lives between visits: browser storage on the web. */
interface PetStore {
    fun load(): String?
    fun save(data: String)
}

/** The stored form of a [PetState]: a short versioned line. */
object PetCodec {
    private const val VERSION = "1"

    fun encode(s: PetState): String =
        listOf(VERSION, s.food, s.happiness, s.updatedAt, s.activeAt, s.lastFedDay ?: "", s.streak, s.longestStreak).joinToString(";")

    /** The state in [data], or null if it can't be read (then the pup starts afresh). */
    fun decode(data: String): PetState? {
        val f = data.split(';')
        if (f.size != 8 || f[0] != VERSION) return null
        return try {
            PetState(
                food = f[1].toDouble().coerceIn(0.0, 1.0),
                happiness = f[2].toDouble().coerceIn(0.0, 1.0),
                updatedAt = f[3].toLong(),
                activeAt = f[4].toLong(),
                lastFedDay = f[5].takeIf { it.isNotEmpty() }?.toLong(),
                streak = f[6].toInt(),
                longestStreak = f[7].toInt(),
            )
        } catch (e: NumberFormatException) {
            null
        }
    }
}
