package com.kregosh.chessopenings.ui.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.chessopenings.core.engine.Score
import com.kregosh.chessopenings.ui.Strings
import kotlin.math.exp
import com.kregosh.chessopenings.chess.Color as Side

/** Vertical Stockfish bar: White's share fills from White's side of the board. */
@Composable
fun EvalBar(eval: Score?, orientation: Side, modifier: Modifier = Modifier) {
    val target = when {
        eval == null -> 0.5f
        eval.mate != null -> if (eval.value > 0) 1f else 0f
        // Logistic squash: ±4 pawns already look clearly decided, but never full.
        else -> (1 / (1 + exp(-eval.cp / 250.0))).toFloat()
    }
    val whiteShare by animateFloatAsState(target)
    val whiteAtBottom = orientation == Side.WHITE
    Box(modifier.width(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3F4A))) {
        Column(Modifier.matchParentSize()) {
            if (whiteAtBottom) {
                Box(Modifier.weight(1f - whiteShare + 0.0001f).fillMaxWidth())
                Box(Modifier.weight(whiteShare + 0.0001f).fillMaxWidth().background(Color(0xFFF5F5F0)))
            } else {
                Box(Modifier.weight(whiteShare + 0.0001f).fillMaxWidth().background(Color(0xFFF5F5F0)))
                Box(Modifier.weight(1f - whiteShare + 0.0001f).fillMaxWidth())
            }
        }
        if (eval != null) {
            val whiteAhead = eval.value >= 0
            Text(
                Strings.eval(eval).removePrefix("+").removePrefix("-"),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (whiteAhead) Color(0xFF3A3F4A) else Color(0xFFF5F5F0),
                modifier = Modifier
                    .align(if (whiteAhead == whiteAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(vertical = 4.dp),
            )
        }
    }
}
