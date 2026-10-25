package com.kregosh.chessopenings.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kregosh.chessopenings.core.training.Quality
import com.kregosh.chessopenings.core.training.Report
import com.kregosh.chessopenings.core.training.TrainerAction
import com.kregosh.chessopenings.ui.Strings

@Composable
fun ReportScreen(report: Report, dispatch: (TrainerAction) -> Unit, header: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(Strings.headline(report.bookMoves, report.bookPositions), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "${report.bookMoves} of ${report.bookPositions} moves were book" +
                    (report.userMoves - report.bookPositions).let { past -> if (past > 0) " · $past past the book" else "" } +
                    (report.openingName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
            val extras = listOfNotNull(
                report.takebacks.takeIf { it > 0 }?.let { "$it takeback${if (it > 1) "s" else ""}" },
                report.hintsUsed.takeIf { it > 0 }?.let { "$it hint${if (it > 1) "s" else ""}" },
                report.finalEval?.let { "final eval ${Strings.eval(it)}" },
            )
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            header()

            Text(
                "Tap one of your moves to go back and try something else from there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoveList(report.moves) { dispatch(TrainerAction.ResumeFrom(it)) }
            if (report.moves.any { it.chosenByUser }) {
                Text("Opponent moves in italics are ones you picked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            report.moves.forEachIndexed { i, m ->
                val v = m.verdict ?: return@forEachIndexed
                val number = "${i / 2 + 1}.${if (i % 2 == 1) ".." else ""} ${m.san}"
                Card(onClick = { dispatch(TrainerAction.ResumeFrom(i)) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(number, fontWeight = FontWeight.Bold)
                            Badge(Strings.quality(v.quality), qualityColor(v.quality))
                            if (m.takebacks > 0) Text("retried ${m.takebacks}×", style = MaterialTheme.typography.bodySmall)
                            if (m.hintUsed) Text("hint", style = MaterialTheme.typography.bodySmall)
                        }
                        for (r in v.reasons.take(2)) Text("• " + Strings.reason(r), style = MaterialTheme.typography.bodyMedium)
                        if (!v.inBook) {
                            if (v.bookMoves.isNotEmpty()) Text("Book: ${v.bookMoves.take(4).joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                            v.bestMove?.takeIf { it != m.san && v.quality != Quality.BEST }?.let { Text("Stockfish: $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { dispatch(TrainerAction.Restart) }) { Text("Train again") }
                OutlinedButton(onClick = { dispatch(TrainerAction.Quit) }) { Text("New setup") }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
    }
}
