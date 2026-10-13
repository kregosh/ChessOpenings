package com.kregosh.chessopenings.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kregosh.chessopenings.chess.PieceType
import com.kregosh.chessopenings.core.training.Feedback
import com.kregosh.chessopenings.core.training.Hint
import com.kregosh.chessopenings.core.training.FeedbackMode
import com.kregosh.chessopenings.core.training.Phase
import com.kregosh.chessopenings.core.training.PlayedMove
import com.kregosh.chessopenings.core.training.Quality
import com.kregosh.chessopenings.core.training.TrainerAction
import com.kregosh.chessopenings.core.training.TrainerState
import com.kregosh.chessopenings.ui.Strings
import com.kregosh.chessopenings.ui.board.ChessBoard
import com.kregosh.chessopenings.ui.board.EvalBar
import com.kregosh.chessopenings.ui.board.Pieces
import com.kregosh.chessopenings.ui.theme.QualityColors

@Composable
fun TrainingScreen(state: TrainerState, dispatch: (TrainerAction) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val w = maxWidth
        val h = maxHeight
        val wide = w > h * 1.2f
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BoardArea(state, dispatch, minOf(h, w * 0.6f))
                SidePanel(state, dispatch, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header(state, dispatch)
                BoardArea(state, dispatch, minOf(w, h * 0.62f))
                SidePanelContent(state, dispatch, showHeader = false)
            }
        }
    }
    state.pendingPromotion?.let { PromotionDialog(state, dispatch) }
}

@Composable
private fun BoardArea(state: TrainerState, dispatch: (TrainerAction) -> Unit, size: Dp) {
    val evalBar = state.setup?.options?.evalBar == true
    // The board stays square; the eval bar takes its share of the width.
    Row(Modifier.height(if (evalBar) size - 30.dp else size)) {
        if (evalBar) {
            EvalBar(state.eval, state.orientation, Modifier.fillMaxHeight())
            Spacer(Modifier.width(8.dp))
        }
        ChessBoard(
            position = state.position,
            orientation = state.orientation,
            lastMove = state.lastMove,
            selected = state.selected,
            targets = state.targets,
            arrows = state.arrows,
            interactive = state.phase == Phase.YOUR_MOVE,
            onTap = { dispatch(TrainerAction.SquareTapped(it)) },
            onDrop = { from, to -> dispatch(TrainerAction.PieceDropped(from, to)) },
            modifier = Modifier.fillMaxHeight(),
        )
    }
}

@Composable
private fun SidePanel(state: TrainerState, dispatch: (TrainerAction) -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SidePanelContent(state, dispatch, showHeader = true)
    }
}

@Composable
private fun SidePanelContent(state: TrainerState, dispatch: (TrainerAction) -> Unit, showHeader: Boolean) {
    if (showHeader) Header(state, dispatch)
    state.error?.let { ErrorCard(it, dispatch) }
    StatusLine(state)
    state.feedback?.takeIf { state.setup?.options?.feedback == FeedbackMode.INSTANT }?.let { FeedbackCard(it, dispatch) }
    Actions(state, dispatch)
    state.hint?.let { HintCard(it) }
    MoveList(state.moves)
}

@Composable
private fun Header(state: TrainerState, dispatch: (TrainerAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(state.openingName ?: Strings.APP_NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Move ${minOf(state.userMovesPlayed + if (state.phase == Phase.YOUR_MOVE) 1 else 0, state.userMovesTotal)} of ${state.userMovesTotal}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { dispatch(TrainerAction.Quit) }) { Text("New setup") }
    }
}

@Composable
private fun StatusLine(state: TrainerState) {
    val text = when (state.phase) {
        Phase.YOUR_MOVE -> "Your move"
        Phase.BOT_THINKING -> "The bot is thinking…"
        Phase.CHECKING -> "Checking your move…"
        Phase.FEEDBACK -> "Try again or continue?"
        Phase.ANALYZING -> "Session over, looking at your moves…"
        else -> return
    }
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun qualityColor(q: Quality): Color = when (q) {
    Quality.BOOK -> QualityColors.book
    Quality.BEST -> QualityColors.best
    Quality.PLAYABLE -> QualityColors.playable
    Quality.INACCURACY -> QualityColors.inaccuracy
    Quality.MISTAKE -> QualityColors.mistake
    Quality.BLUNDER -> QualityColors.blunder
}

@Composable
private fun FeedbackCard(feedback: Feedback, dispatch: (TrainerAction) -> Unit) {
    val v = feedback.verdict
    val color = qualityColor(v.quality)
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val san = feedback.move.san
                Text(
                    when {
                        v.inBook -> "$san: ${Strings.praise(san.length + feedback.move.uci.hashCode())}"
                        v.quality == Quality.BEST && v.pastBook -> "$san: Best move! Stockfish agrees."
                        v.quality == Quality.BEST -> "$san: not book, but Stockfish's top choice"
                        else -> "$san: ${if (v.pastBook) "" else "not book · "}${Strings.quality(v.quality)}"
                    },
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                for (r in v.reasons.take(3)) Text("• " + Strings.reason(r), style = MaterialTheme.typography.bodyMedium)
                if (!v.inBook) {
                    if (v.bookMoves.isNotEmpty()) Text("Book moves here: ${v.bookMoves.take(4).joinToString(", ")}")
                    else Text("You're past the end of the book.", style = MaterialTheme.typography.bodySmall)
                    v.bestMove?.takeIf { v.quality != Quality.BEST }?.let { best ->
                        Text("Stockfish prefers $best" + (v.lossCp?.takeIf { it > 0 }?.let { " (this move costs ${Strings.pawns(it)})" } ?: ""))
                    }
                }
                if (feedback.blocking) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Button(onClick = { dispatch(TrainerAction.Retry) }) { Text("Try again") }
                        OutlinedButton(onClick = { dispatch(TrainerAction.Continue) }) { Text("Continue anyway") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Actions(state: TrainerState, dispatch: (TrainerAction) -> Unit) {
    if (state.phase != Phase.YOUR_MOVE) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.setup?.options?.hints == true) OutlinedButton(onClick = { dispatch(TrainerAction.ShowHint) }) { Text("Show good moves") }
        OutlinedButton(onClick = { dispatch(TrainerAction.Undo) }, enabled = state.canUndo) { Text("Undo") }
    }
}

@Composable
private fun HintCard(hint: Hint) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Good moves here", fontWeight = FontWeight.Bold)
            if (hint.moves.isEmpty() && hint.engineDone) Text("Stockfish has no suggestion.")
            for (m in hint.moves) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(m.san, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                m.book -> m.openingName ?: Strings.HINT_BOOK
                                else -> Strings.HINT_ENGINE_ONLY
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (m.book) QualityColors.book else QualityColors.engine,
                        )
                    }
                    m.score?.let { Text(Strings.eval(it), style = MaterialTheme.typography.bodyMedium) }
                    if (m.best) {
                        Surface(color = QualityColors.best, shape = RoundedCornerShape(50)) {
                            Text("Top pick", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            if (!hint.engineDone) Text("Stockfish is thinking…", style = MaterialTheme.typography.bodySmall)
            else if (hint.moves.none { it.book }) Text("You're past the end of the book.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveList(moves: List<PlayedMove>, onUserMoveClick: ((Int) -> Unit)? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        moves.forEachIndexed { i, m ->
            val number = if (i % 2 == 0) "${i / 2 + 1}. " else ""
            val color = m.verdict?.let { qualityColor(it.quality) }
            val clickable = onUserMoveClick != null && m.byUser && !m.startingLine
            Text(
                number + m.san,
                fontWeight = if (m.byUser) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (clickable) TextDecoration.Underline else null,
                modifier = if (clickable) Modifier.clickable { onUserMoveClick(i) } else Modifier,
                color = when {
                    m.startingLine -> MaterialTheme.colorScheme.onSurfaceVariant
                    color != null -> color
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, dispatch: (TrainerAction) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = QualityColors.blunder.copy(alpha = 0.12f))) {
        Column(Modifier.padding(12.dp)) {
            Text("Something went wrong: $message")
            TextButton(onClick = { dispatch(TrainerAction.Restart) }) { Text("Start again") }
        }
    }
}

@Composable
private fun PromotionDialog(state: TrainerState, dispatch: (TrainerAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { dispatch(TrainerAction.PromotionCancelled) },
        title = { Text("Promote to") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (type in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                    Image(
                        rememberVectorPainter(Pieces.image(state.orientation, type)),
                        contentDescription = type.name.lowercase(),
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .clickable { dispatch(TrainerAction.PromotionChosen(type)) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { dispatch(TrainerAction.PromotionCancelled) }) { Text("Cancel") } },
    )
}
