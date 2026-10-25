package com.kregosh.chessopenings.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kregosh.chessopenings.core.pet.Mood
import com.kregosh.chessopenings.core.pet.PetView
import com.kregosh.chessopenings.core.pet.SessionGrade
import com.kregosh.chessopenings.core.pet.Treat
import kotlinx.coroutines.delay

/** A short reaction that plays over the pup's mood: to a move, a pat or a meal. */
enum class PupReaction { CHEER, WORRIED, COVER_EYES, PATTED, EATING }

/** A pixel sprite, parsed once: colours per pixel, null for transparent. */
private class Sprite(rows: List<String>) {
    val width = rows.first().length
    val height = rows.size
    val pixels: Array<Color?> = Array(width * height) { i ->
        PUP_PALETTE[rows[i / width][i % width]]?.let { Color(it) }
    }
}

private val frames = PupFrame.entries.associateWith { Sprite(it.rows) }
private val icons = PupIcon.entries.associateWith { Sprite(it.rows) }

/** Draws [sprite] with square, unsmoothed pixels, [pixel] dp each. */
@Composable
private fun PixelArt(sprite: Sprite, pixel: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(pixel * sprite.width, pixel * sprite.height)) {
        val p = size.width / sprite.width
        for (i in sprite.pixels.indices) {
            val c = sprite.pixels[i] ?: continue
            // A hair of overlap so no seams show between pixels.
            drawRect(c, Offset((i % sprite.width) * p, (i / sprite.width) * p), Size(p + 0.6f, p + 0.6f))
        }
    }
}

@Composable
internal fun PixelIcon(icon: PupIcon, modifier: Modifier = Modifier, pixel: Dp = 2.dp) = PixelArt(icons.getValue(icon), pixel, modifier)

/** The frames a mood or reaction cycles through, and whether the pup hops. */
private fun animation(mood: Mood, reaction: PupReaction?, tick: Int): Pair<PupFrame, Boolean> {
    val blink = tick % 16 == 15
    return when (reaction) {
        PupReaction.CHEER -> (if (tick % 2 == 0) PupFrame.HAPPY else PupFrame.HAPPY_UP) to (tick % 2 == 0)
        PupReaction.PATTED -> (if (tick % 2 == 0) PupFrame.HAPPY else PupFrame.WAG) to false
        PupReaction.WORRIED -> PupFrame.WORRIED to false
        PupReaction.COVER_EYES -> PupFrame.COVER to false
        PupReaction.EATING -> (if (tick % 2 == 0) PupFrame.EAT else PupFrame.CHEW) to false
        null -> when (mood) {
            Mood.HAPPY -> (if (blink) PupFrame.BLINK else if (tick % 2 == 0) PupFrame.IDLE else PupFrame.WAG) to (tick % 8 == 0)
            Mood.CONTENT -> (if (blink) PupFrame.BLINK else if (tick % 4 < 2) PupFrame.IDLE else PupFrame.WAG) to false
            Mood.BORED -> (if (blink) PupFrame.BLINK else PupFrame.BORED) to false
            Mood.HUNGRY -> PupFrame.HUNGRY to false
            Mood.SAD -> (if (blink) PupFrame.BLINK else PupFrame.SAD) to false
            Mood.SICK -> PupFrame.SICK to false
            Mood.ASLEEP -> PupFrame.SLEEP to false
        }
    }
}

/** The animated pup. Tapping it calls [onTap] (petting). */
@Composable
fun Pup(mood: Mood, modifier: Modifier = Modifier, reaction: PupReaction? = null, pixel: Dp = 3.dp, onTap: (() -> Unit)? = null) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            tick++
        }
    }
    val (frame, hop) = animation(mood, reaction, tick)
    val tap = if (onTap == null) Modifier else Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
    Box(modifier.size(pixel * 32, pixel * 34).then(tap), contentAlignment = Alignment.BottomCenter) {
        PixelArt(frames.getValue(frame), pixel, Modifier.offset { IntOffset(0, if (hop) -(pixel * 2).roundToPx() else 0) })
        when {
            reaction == PupReaction.PATTED || (reaction == PupReaction.CHEER && tick % 2 == 0) ->
                PixelIcon(PupIcon.HEART, Modifier.align(Alignment.TopEnd).offset { IntOffset(0, -(pixel * (tick % 3)).roundToPx()) }, pixel)
            reaction == null && mood == Mood.ASLEEP ->
                PixelIcon(PupIcon.ZZZ, Modifier.align(Alignment.TopEnd).offset { IntOffset(-(pixel * 2).roundToPx(), (pixel * (8 - tick % 4)).roundToPx()) }, pixel)
        }
    }
}

/** Shows [reaction] for a moment whenever [key] changes, then goes back to the mood. */
@Composable
fun rememberReaction(key: Any?, reaction: PupReaction?, millis: Long = REACTION_MS): PupReaction? {
    var shown by remember { mutableStateOf<PupReaction?>(null) }
    LaunchedEffect(key) {
        shown = reaction
        if (reaction != null) {
            delay(millis)
            shown = null
        }
    }
    return shown
}

/** The pup's card on the setup screen: the pup, how it feels, its needs and the streak. */
@Composable
fun PetCard(view: PetView, onPat: () -> Unit) {
    var pats by remember { mutableIntStateOf(0) }
    val reaction = rememberReaction(pats, if (pats > 0) PupReaction.PATTED else null, 1200)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Pup(view.mood, reaction = reaction, pixel = 3.dp) {
                pats++
                onPat()
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(PetStrings.mood(view.mood, view.fedToday), fontWeight = FontWeight.SemiBold)
                Need(PupIcon.BONE, "Food", view.state.food)
                Need(PupIcon.HEART, "Happiness", view.state.happiness)
                Streak(view.streak, view.fedToday)
            }
        }
    }
}

@Composable
private fun Need(icon: PupIcon, label: String, value: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { PixelIcon(icon) }
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
        LinearProgressIndicator(
            progress = { value.toFloat() },
            modifier = Modifier.weight(1f).height(8.dp),
            color = if (value < 0.25) Color(0xFFD0453B) else MaterialTheme.colorScheme.primary,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun Streak(streak: Int, fedToday: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { PixelIcon(PupIcon.FLAME) }
        Text(PetStrings.streak(streak, fedToday), style = MaterialTheme.typography.bodySmall)
    }
}

/** The pup next to the board while training, reacting to [reaction]. */
@Composable
fun TrainingPup(view: PetView, reaction: PupReaction?) {
    // Training wakes the pup up.
    val mood = if (view.mood == Mood.ASLEEP) Mood.CONTENT else view.mood
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pup(mood, reaction = reaction, pixel = 2.dp)
        reaction?.let { Text(PetStrings.reaction(it), style = MaterialTheme.typography.bodyMedium) }
    }
}

/** The feeding moment on the report: the pup eats, then shows how happy the session made it. */
@Composable
fun FeedingCard(view: PetView, treat: Treat) {
    var eating by remember(treat) { mutableStateOf(true) }
    LaunchedEffect(treat) {
        delay(EATING_MS)
        eating = false
    }
    val after = when (treat.grade) {
        SessionGrade.GREAT, SessionGrade.GOOD -> PupReaction.CHEER
        else -> null
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Pup(if (after != null) Mood.HAPPY else Mood.CONTENT, reaction = if (eating) PupReaction.EATING else after, pixel = 3.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (eating) "Munch munch…" else PetStrings.treat(treat.grade), fontWeight = FontWeight.SemiBold)
                Need(PupIcon.BONE, "Food", view.state.food)
                Need(PupIcon.HEART, "Happiness", view.state.happiness)
                Streak(view.streak, fedToday = true)
            }
        }
    }
}

private const val TICK_MS = 260L
private const val REACTION_MS = 2400L
private const val EATING_MS = 2200L
