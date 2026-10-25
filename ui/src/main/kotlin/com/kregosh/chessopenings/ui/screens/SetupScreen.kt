package com.kregosh.chessopenings.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kregosh.chessopenings.chess.Color
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.Uci
import com.kregosh.chessopenings.core.training.Choice
import com.kregosh.chessopenings.core.training.FeedbackMode
import com.kregosh.chessopenings.core.training.SessionOptions
import com.kregosh.chessopenings.core.training.SetupCatalog
import com.kregosh.chessopenings.core.training.TrainingSetup
import com.kregosh.chessopenings.ui.Strings
import com.kregosh.chessopenings.ui.board.ChessBoard

/** What the setup screen remembers between sessions. */
data class SetupSelection(
    val color: Color = Color.WHITE,
    /** The user's first move (UCI); null = the most popular one. */
    val firstMove: String? = null,
    /** The opponent's chosen move (UCI): White's reply or White's first move; null = random. */
    val opponentMove: String? = null,
    val options: SessionOptions = SessionOptions(),
)

/** How many choices a row shows at most. */
private const val MAX_CHOICES = 8

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    catalog: SetupCatalog,
    initial: SetupSelection,
    header: @Composable () -> Unit = {},
    onStart: (TrainingSetup, SetupSelection) -> Unit,
) {
    var selection by remember { mutableStateOf(initial) }
    val color = selection.color

    val firstChoices = remember(color) {
        (if (color == Color.WHITE) catalog.userMoves(emptyList()) else catalog.blackFirstMoves())
            .filter { it.share >= 0.01 }.take(MAX_CHOICES)
    }
    val first = firstChoices.firstOrNull { it.uci == selection.firstMove } ?: firstChoices.firstOrNull()
    val opponentChoices = remember(color, first?.uci) {
        when {
            first == null -> emptyList()
            color == Color.WHITE -> catalog.opponentReplies(listOf(first.uci))
            else -> catalog.whiteFirstMovesFor(first.uci)
        }.filter { it.share >= 0.01 }.take(MAX_CHOICES)
    }
    val opponent = opponentChoices.firstOrNull { it.uci == selection.opponentMove }

    val setup = first?.let {
        when {
            color == Color.WHITE -> TrainingSetup(Color.WHITE, listOfNotNull(it.uci, opponent?.uci), options = selection.options)
            opponent == null -> TrainingSetup(Color.BLACK, listOf(it.uci), randomWhiteFirstMove = true, options = selection.options)
            else -> TrainingSetup(Color.BLACK, listOf(opponent.uci, it.uci), options = selection.options)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(Strings.APP_NAME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Pick a first move and learn how to answer what your opponents really play.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            header()

            Section("Play as") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(Color.WHITE to "White", Color.BLACK to "Black").forEachIndexed { i, (c, label) ->
                        SegmentedButton(
                            selected = color == c,
                            onClick = { selection = selection.copy(color = c, firstMove = null, opponentMove = null) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }

            Section(if (color == Color.WHITE) "Your first move" else "Your answer to White's first move") {
                ChoiceRow(firstChoices, first?.uci) { selection = selection.copy(firstMove = it, opponentMove = null) }
            }

            Section(if (color == Color.WHITE) "Opponent's reply" else "White's first move") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = opponent == null,
                        onClick = { selection = selection.copy(opponentMove = null) },
                        label = { Text("Random") },
                    )
                    for (c in opponentChoices) {
                        FilterChip(
                            selected = opponent?.uci == c.uci,
                            onClick = { selection = selection.copy(opponentMove = c.uci) },
                            label = { ChoiceLabel(c) },
                        )
                    }
                }
                Text(
                    if (opponent == null) "The bot mixes the replies people play most." else "The bot always starts with ${opponent.san}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            setup?.let { PreviewBoard(it) }

            Section("Session") {
                Text("Feedback")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(FeedbackMode.INSTANT to "After every move", FeedbackMode.END to "At the end").forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = selection.options.feedback == mode,
                            onClick = { selection = selection.copy(options = selection.options.copy(feedback = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
                Toggle("Allow move suggestions", selection.options.hints) {
                    selection = selection.copy(options = selection.options.copy(hints = it))
                }
                Toggle("Show the Stockfish eval bar", selection.options.evalBar) {
                    selection = selection.copy(options = selection.options.copy(evalBar = it))
                }
            }

            Button(
                onClick = { setup?.let { onStart(it, selection) } },
                enabled = setup != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Start training", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(choices: List<Choice>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in choices) {
            FilterChip(selected = selected == c.uci, onClick = { onSelect(c.uci) }, label = { ChoiceLabel(c) })
        }
    }
}

@Composable
private fun ChoiceLabel(c: Choice) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("${c.san}  ${(c.share * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
        c.name?.let { Text(it.substringBefore(':'), style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A small board showing where the session starts (with "?" when White's first move is random). */
@Composable
private fun PreviewBoard(setup: TrainingSetup) {
    val position = setup.line.fold(if (setup.randomWhiteFirstMove) null else Position.start) { p, uci ->
        p?.let { Uci.parse(it, uci)?.let(it::play) }
    }
    if (position == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        ChessBoard(position, setup.color, Modifier.size(200.dp), lastMove = null)
    }
}
