package com.kregosh.chessopenings.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Board and highlight colors: a blue board with classic black and white pieces. */
object BoardColors {
    val light = Color(0xFFDDE8F4)
    val dark = Color(0xFF7898C2)
    val lastMove = Color(0x8AA6E3FF)
    val selected = Color(0x8059A8E8)
    val target = Color(0x47203050)
    val bookArrow = Color(0xD02E9E5B)
    val engineArrow = Color(0xD0E8833A)
    val coordinateOnLight = dark
    val coordinateOnDark = light
}

/** Verdict colors, from "book" green to "blunder" red. */
object QualityColors {
    val book = Color(0xFF2E9E5B)
    val playable = Color(0xFF4A86C8)
    val inaccuracy = Color(0xFFD9A520)
    val mistake = Color(0xFFE07B30)
    val blunder = Color(0xFFD0453B)
}

private val scheme = lightColorScheme(
    primary = Color(0xFF2F5F98),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3F7),
    onPrimaryContainer = Color(0xFF0E2A4A),
    secondary = Color(0xFF2F5F98),
    // Selected chips and segments.
    secondaryContainer = Color(0xFFCFE0F5),
    onSecondaryContainer = Color(0xFF0E2A4A),
    background = Color(0xFFF3F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6ECF4),
    onSurfaceVariant = Color(0xFF4A5566),
    // Cards and dialogs: a light blue-grey instead of Material's default lavender.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFEAF0F7),
    surfaceContainerHighest = Color(0xFFE4EBF4),
    outline = Color(0xFF8A9AB0),
    outlineVariant = Color(0xFFC5D0DE),
)

@Composable
fun TrainerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
