package com.kregosh.chessopenings.ui.pet

import com.kregosh.chessopenings.core.pet.Mood
import com.kregosh.chessopenings.core.pet.SessionGrade

/** What the pup's card and reactions say. */
object PetStrings {
    fun mood(mood: Mood, fedToday: Boolean): String = when (mood) {
        Mood.HAPPY -> if (fedToday) "Your pup is full and super happy!" else "Your pup is happy to see you!"
        Mood.CONTENT -> if (fedToday) "Your pup is full and content." else "Your pup is waiting for today's training."
        Mood.BORED -> "Your pup is bored. A good session would cheer it up!"
        Mood.HUNGRY -> "Your pup is hungry… one session fills its bowl."
        Mood.SAD -> "Your pup misses you. Train a little to cheer it up."
        Mood.SICK -> "Your pup feels poorly after days without training. One session perks it up!"
        Mood.ASLEEP -> "Shh… your pup is asleep. Training wakes it up."
    }

    fun streak(days: Int, fedToday: Boolean): String = when {
        days == 0 -> "No streak yet. Train today to start one!"
        fedToday -> "$days ${if (days == 1) "day" else "days"} fed in a row"
        else -> "$days ${if (days == 1) "day" else "days"} in a row. Train today to keep it going!"
    }

    fun treat(grade: SessionGrade): String = when (grade) {
        SessionGrade.GREAT -> "Perfect session! Your pup gobbled it all up and does a happy dance."
        SessionGrade.GOOD -> "Yum! Your pup loved that session."
        SessionGrade.OK -> "A tasty meal. Your pup is full."
        SessionGrade.ROUGH -> "Your pup ate it all up. Tough session, but it believes in you!"
    }

    fun reaction(r: PupReaction): String = when (r) {
        PupReaction.CHEER -> "Woof!"
        PupReaction.WORRIED -> "Hmm…"
        PupReaction.COVER_EYES -> "Oh no…"
        PupReaction.PATTED -> ""
        PupReaction.EATING -> "Munch munch…"
    }
}
