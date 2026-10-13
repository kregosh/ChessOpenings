package com.kregosh.chessopenings.core.training

import com.kregosh.chessopenings.chess.Color
import com.kregosh.chessopenings.chess.Move
import com.kregosh.chessopenings.chess.PieceType
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.Square
import com.kregosh.chessopenings.core.engine.Score

enum class Phase {
    /** No session: the setup screen. */
    IDLE,
    BOT_THINKING,
    YOUR_MOVE,
    /** The user's move is being graded. */
    CHECKING,
    /** Instant mode: waiting for Retry or Continue after a non-book move. */
    FEEDBACK,
    /** The user is picking the opponent's move instead of the bot's. */
    CHOOSE_OPPONENT_MOVE,
    /** Session over; grading the moves for the report. */
    ANALYZING,
    REPORT,
}

enum class ArrowKind { BOOK, ENGINE }

data class Arrow(val from: Square, val to: Square, val kind: ArrowKind)

/** A move of the session, as the move list and the report show it. */
data class PlayedMove(
    val san: String,
    val uci: String,
    val color: Color,
    val byUser: Boolean,
    /** Part of the fixed starting line, not trained. */
    val startingLine: Boolean,
    /** Set for the user's moves once graded. */
    val verdict: Verdict? = null,
    /** How often the user took this move back (undo or retry) before playing it. */
    val takebacks: Int = 0,
    val hintUsed: Boolean = false,
    /** An opponent move the user picked in place of the bot's. */
    val chosenByUser: Boolean = false,
)

data class Feedback(
    val move: PlayedMove,
    val verdict: Verdict,
    /** Waits for Retry or Continue; otherwise just shown while play goes on. */
    val blocking: Boolean,
)

/** One candidate in the hint list. */
data class HintMove(
    val san: String,
    /** A book move for the user here. */
    val book: Boolean,
    /** The opening the move leads to, if that position has a name. */
    val openingName: String?,
    /** Stockfish's evaluation after the move, from the user's side; null if unknown. */
    val score: Score?,
    /** Ties for Stockfish's top evaluation. */
    val best: Boolean,
)

/**
 * "Show best move": the book moves (most popular first), then Stockfish's own picks that
 * aren't book but are as good. Scores arrive once Stockfish has answered ([engineDone]).
 */
data class Hint(val moves: List<HintMove>, val engineDone: Boolean)

data class Report(
    /** The whole session, starting line included; the user's moves carry verdicts. */
    val moves: List<PlayedMove>,
    val userMoves: Int,
    val bookMoves: Int,
    val takebacks: Int,
    val hintsUsed: Int,
    val openingName: String?,
    /** Final evaluation from White's point of view, if the engine answered. */
    val finalEval: Score?,
)

/** Everything the screens show. They render it and send [TrainerAction]s; nothing else. */
data class TrainerState(
    val phase: Phase = Phase.IDLE,
    val setup: TrainingSetup? = null,
    val position: Position = Position.start,
    /** The user's color: the board is drawn from this side. */
    val orientation: Color = Color.WHITE,
    val lastMove: Move? = null,
    /** Tap-to-move: the selected piece and where it can go. */
    val selected: Square? = null,
    val targets: Set<Square> = emptySet(),
    /** A pawn move waiting for the promotion piece. */
    val pendingPromotion: Pair<Square, Square>? = null,
    val moves: List<PlayedMove> = emptyList(),
    val feedback: Feedback? = null,
    val hint: Hint? = null,
    val arrows: List<Arrow> = emptyList(),
    /** Eval bar value from White's point of view; null when off or not yet known. */
    val eval: Score? = null,
    val openingName: String? = null,
    /** The user's moves played after the starting line, and how many the session has. */
    val userMovesPlayed: Int = 0,
    val userMovesTotal: Int = 0,
    val canUndo: Boolean = false,
    /** The last move is the bot's (not the starting line's), so the user may replace it. */
    val canChangeOpponentMove: Boolean = false,
    /** While choosing the opponent's move: its book replies, most played first. */
    val opponentChoices: List<Choice> = emptyList(),
    val report: Report? = null,
    /** Something went wrong (e.g. the engine failed); shown to the user. */
    val error: String? = null,
)

sealed interface TrainerAction {
    data class Start(val setup: TrainingSetup) : TrainerAction
    data class SquareTapped(val square: Square) : TrainerAction
    data class PieceDropped(val from: Square, val to: Square) : TrainerAction
    data class PromotionChosen(val type: PieceType) : TrainerAction
    data object PromotionCancelled : TrainerAction
    data object ShowHint : TrainerAction
    /** Take back the move just graded and try another (instant feedback). */
    data object Retry : TrainerAction
    /** Keep the move just graded and play on (instant feedback). */
    data object Continue : TrainerAction
    /** Take back the user's last move and the bot's reply. */
    data object Undo : TrainerAction
    /** Take back the bot's last move and let the user pick the opponent's move instead. */
    data object ChangeOpponentMove : TrainerAction
    /** While choosing the opponent's move: play [uci] (from [TrainerState.opponentChoices]). */
    data class OpponentMoveChosen(val uci: String) : TrainerAction
    /** While choosing the opponent's move: put the bot's move back. */
    data object CancelOpponentMove : TrainerAction
    /** Same setup again. */
    data object Restart : TrainerAction
    /**
     * From the report: go back to just before the user's move at [moveIndex] (an index into
     * [TrainerState.moves]) and play on from there.
     */
    data class ResumeFrom(val moveIndex: Int) : TrainerAction
    /** Back to the setup screen. */
    data object Quit : TrainerAction
}
