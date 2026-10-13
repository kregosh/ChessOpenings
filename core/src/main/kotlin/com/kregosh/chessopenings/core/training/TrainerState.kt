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
)

data class Feedback(
    val move: PlayedMove,
    val verdict: Verdict,
    /** Waits for Retry or Continue; otherwise just shown while play goes on. */
    val blocking: Boolean,
)

data class Hint(
    /** Book moves (SAN), most popular first. */
    val bookMoves: List<String>,
    /** Stockfish's best move (SAN), if it answered. */
    val engineMove: String?,
)

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
    /** Same setup again. */
    data object Restart : TrainerAction
    /** Back to the setup screen. */
    data object Quit : TrainerAction
}
