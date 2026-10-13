package com.kregosh.chessopenings.core.training

import com.kregosh.chessopenings.chess.Color
import com.kregosh.chessopenings.chess.Move
import com.kregosh.chessopenings.chess.PieceType
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import com.kregosh.chessopenings.chess.Square
import com.kregosh.chessopenings.chess.Uci
import com.kregosh.chessopenings.core.book.OpeningBook
import com.kregosh.chessopenings.core.engine.Engine
import com.kregosh.chessopenings.core.engine.EngineDepths
import com.kregosh.chessopenings.core.engine.Score
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Runs training sessions. The screens observe [state] and call [dispatch]; all rules,
 * timing and engine calls live here. Not thread-safe: call [dispatch] from the thread
 * [scope] runs on (the UI thread).
 */
class TrainerController(
    private val book: OpeningBook,
    private val engine: Engine,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    private val depths: EngineDepths = EngineDepths(),
    /** Pause before the bot moves, so its move doesn't appear instantly. */
    private val botDelayMs: Long = 400,
) {
    private val _state = MutableStateFlow(TrainerState())
    val state: StateFlow<TrainerState> = _state.asStateFlow()

    private val policy = BotPolicy(book, random)
    private val grader = Grader(book, engine, depths.review)

    /** Every ply of the session as (position before, move), starting line included. */
    private val plies = ArrayList<Pair<Position, Move>>()
    private var startPlies = 0

    /** Takebacks per ply index, carried to whatever move is played there next. */
    private val takebacks = HashMap<Int, Int>()
    private var hintUsed = false

    private var sessionJob: Job? = null
    private var evalJob: Job? = null
    private var hintJob: Job? = null

    private val setup: TrainingSetup get() = _state.value.setup ?: error("No session")
    private val position: Position get() = plies.lastOrNull()?.let { (p, m) -> p.play(m) } ?: Position.start

    fun dispatch(action: TrainerAction) {
        val s = _state.value
        when (action) {
            is TrainerAction.Start -> start(action.setup)
            TrainerAction.Restart -> s.setup?.let(::start)
            is TrainerAction.ResumeFrom -> if (s.phase == Phase.REPORT) resumeFrom(action.moveIndex)
            TrainerAction.Quit -> quit()
            is TrainerAction.SquareTapped -> if (s.phase == Phase.YOUR_MOVE) tap(action.square)
            is TrainerAction.PieceDropped -> if (s.phase == Phase.YOUR_MOVE) userMove(action.from, action.to, null)
            is TrainerAction.PromotionChosen -> s.pendingPromotion?.let { (from, to) -> userMove(from, to, action.type) }
            TrainerAction.PromotionCancelled -> _state.update { it.copy(pendingPromotion = null, selected = null, targets = emptySet()) }
            TrainerAction.ShowHint -> if (s.phase == Phase.YOUR_MOVE && setup.options.hints) showHint()
            TrainerAction.Retry -> if (s.phase == Phase.FEEDBACK) retry()
            TrainerAction.Continue -> if (s.phase == Phase.FEEDBACK) {
                _state.update { it.copy(feedback = it.feedback?.copy(blocking = false)) }
                afterUserMove()
            }
            TrainerAction.Undo -> if (s.phase == Phase.YOUR_MOVE && s.canUndo) undo()
        }
    }

    // ── Session lifecycle ──────────────────────────────────────────────────

    private fun start(newSetup: TrainingSetup) {
        cancelJobs()
        plies.clear()
        takebacks.clear()
        hintUsed = false

        val line = if (newSetup.randomWhiteFirstMove) {
            val white = policy.whiteFirstMove(newSetup.line.first())
            if (white == null) {
                _state.value = TrainerState(setup = newSetup, error = "No White first move fits ${newSetup.line.first()}")
                return
            }
            listOf(white.uci) + newSetup.line
        } else {
            newSetup.line
        }
        var p = Position.start
        for (uci in line) {
            val move = Uci.parse(p, uci)
            if (move == null) {
                _state.value = TrainerState(setup = newSetup, error = "Illegal move $uci in the starting line")
                return
            }
            plies += p to move
            p = p.play(move)
        }
        startPlies = plies.size

        _state.value = TrainerState(
            setup = newSetup,
            orientation = newSetup.color,
            userMovesTotal = newSetup.options.moves,
        )
        refreshBoard()
        if (p.sideToMove == newSetup.color) _state.update { it.copy(phase = Phase.YOUR_MOVE) } else botTurn()
    }

    private fun quit() {
        cancelJobs()
        plies.clear()
        _state.value = TrainerState()
    }

    private fun cancelJobs() {
        sessionJob?.cancel()
        evalJob?.cancel()
        hintJob?.cancel()
    }

    private val userPliesPlayed: Int get() = plies.indices.count { it >= startPlies && plies[it].first.sideToMove == setup.color }

    private val sessionOver: Boolean get() = userPliesPlayed >= setup.options.moves || position.legalMoves.isEmpty()

    // ── Moves ──────────────────────────────────────────────────────────────

    /** Plays [move] and updates everything derived from the move history. */
    private fun play(move: Move, verdict: Verdict? = null) {
        val index = plies.size
        val before = position
        val byUser = before.sideToMove == setup.color
        plies += before to move
        val played = PlayedMove(
            san = San.write(before, move),
            uci = move.uci,
            color = before.sideToMove,
            byUser = byUser,
            startingLine = false,
            verdict = verdict,
            takebacks = if (byUser) takebacks.remove(index) ?: 0 else 0,
            hintUsed = byUser && hintUsed,
        )
        if (byUser) hintUsed = false
        _state.update { it.copy(moves = it.moves + played) }
        refreshBoard()
    }

    /** Removes the last ply; returns the removed move's record. */
    private fun takeBack(): PlayedMove {
        plies.removeAt(plies.lastIndex)
        val removed = _state.value.moves.last()
        _state.update { it.copy(moves = it.moves.dropLast(1)) }
        refreshBoard()
        return removed
    }

    /** Recomputes the board-derived parts of the state from [plies]. */
    private fun refreshBoard() {
        val p = position
        // The latest named position along the moves played.
        var name: String? = null
        for ((before, move) in plies) book.name(before.play(move))?.let { name = it }
        _state.update { s ->
            s.copy(
                position = p,
                lastMove = plies.lastOrNull()?.second,
                moves = if (s.moves.size == plies.size) s.moves else startingLineMoves(),
                selected = null,
                targets = emptySet(),
                pendingPromotion = null,
                hint = null,
                arrows = emptyList(),
                openingName = name,
                userMovesPlayed = userPliesPlayed,
                canUndo = plies.size > startPlies && userPliesPlayed > 0,
            )
        }
        updateEval()
    }

    private fun startingLineMoves(): List<PlayedMove> = plies.take(startPlies).map { (before, move) ->
        PlayedMove(San.write(before, move), move.uci, before.sideToMove, before.sideToMove == setup.color, startingLine = true)
    }

    private fun tap(square: Square) {
        val s = _state.value
        if (s.selected != null && square in s.targets) {
            userMove(s.selected, square, null)
            return
        }
        val piece = position.pieceAt(square)
        if (piece != null && piece.color == setup.color && square != s.selected) {
            val targets = position.legalMoves.filter { it.from == square }.map { it.to }.toSet()
            _state.update { it.copy(selected = square, targets = targets) }
        } else {
            _state.update { it.copy(selected = null, targets = emptySet()) }
        }
    }

    private fun userMove(from: Square, to: Square, promotion: PieceType?) {
        val candidates = position.legalMoves.filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) {
            _state.update { it.copy(selected = null, targets = emptySet(), pendingPromotion = null) }
            return
        }
        val move = if (candidates.size > 1) {
            if (promotion == null) {
                _state.update { it.copy(pendingPromotion = from to to) }
                return
            }
            candidates.firstOrNull { it.promotion == promotion } ?: return
        } else {
            candidates.single()
        }

        val before = position
        val history = plies.toList()
        play(move)
        val index = plies.lastIndex

        if (setup.options.feedback == FeedbackMode.END) {
            afterUserMove()
            return
        }
        _state.update { it.copy(phase = Phase.CHECKING) }
        sessionJob = launchSafely {
            val verdict = grader.grade(before, move, history)
            val graded = _state.value.moves[index].copy(verdict = verdict)
            _state.update { s -> s.copy(moves = s.moves.toMutableList().also { it[index] = graded }) }
            val q = verdict.quality
            // Past the end of theory only real errors stop the user; a non-book move where
            // book moves exist always does, since that's what the session trains.
            val blocking = q != Quality.BOOK && (!verdict.pastBook || q >= Quality.INACCURACY)
            _state.update { it.copy(feedback = Feedback(graded, verdict, blocking)) }
            if (blocking) _state.update { it.copy(phase = Phase.FEEDBACK) } else afterUserMove()
        }
    }

    private fun afterUserMove() {
        if (sessionOver) finish() else botTurn()
    }

    private fun resumeFrom(index: Int) {
        val moves = _state.value.moves
        if (index !in startPlies until plies.size || !moves[index].byUser) return
        cancelJobs()
        // Keep the earlier moves and their verdicts; the move gone back to counts as a takeback.
        while (plies.size > index) plies.removeAt(plies.lastIndex)
        takebacks.clear()
        takebacks[index] = moves[index].takebacks + 1
        hintUsed = false
        _state.update { it.copy(moves = moves.take(index), report = null, feedback = null, phase = Phase.YOUR_MOVE) }
        refreshBoard()
    }

    private fun retry() {
        val index = plies.lastIndex
        val removed = takeBack()
        takebacks[index] = removed.takebacks + 1
        if (removed.hintUsed) hintUsed = true
        _state.update { it.copy(feedback = null, phase = Phase.YOUR_MOVE) }
    }

    private fun undo() {
        // Take back the bot's reply (if any) and the user's move before it.
        if (plies.isNotEmpty() && plies.last().first.sideToMove != setup.color && plies.size > startPlies) takeBack()
        val index = plies.lastIndex
        val removed = takeBack()
        takebacks[index] = removed.takebacks + 1
        _state.update { it.copy(feedback = null, phase = Phase.YOUR_MOVE) }
    }

    private fun botTurn() {
        _state.update { it.copy(phase = Phase.BOT_THINKING) }
        sessionJob = launchSafely {
            delay(botDelayMs)
            val p = position
            val move = policy.bookMove(p)?.toMove(p)
                ?: engine.analyze(p.toFen(), depths.bot).firstOrNull()?.let { Uci.parse(p, it.move) }
            if (move == null) {
                finish()
                return@launchSafely
            }
            play(move)
            if (sessionOver) finish() else _state.update { it.copy(phase = Phase.YOUR_MOVE) }
        }
    }

    private fun finish() {
        _state.update { it.copy(phase = Phase.ANALYZING, selected = null, targets = emptySet()) }
        sessionJob = launchSafely {
            val moves = _state.value.moves.toMutableList()
            for (i in moves.indices) {
                if (!moves[i].byUser || moves[i].startingLine || moves[i].verdict != null) continue
                val (before, move) = plies[i]
                moves[i] = moves[i].copy(verdict = grader.grade(before, move, plies.take(i)))
            }
            val finalEval = runCatching { engine.analyze(position.toFen(), depths.eval).firstOrNull()?.score?.let(::whitePov) }
                .getOrNull()
            val user = moves.filter { it.byUser && !it.startingLine }
            val report = Report(
                moves = moves,
                userMoves = user.size,
                bookMoves = user.count { it.verdict?.inBook == true },
                takebacks = user.sumOf { it.takebacks },
                hintsUsed = user.count { it.hintUsed },
                openingName = _state.value.openingName,
                finalEval = finalEval,
            )
            _state.update { it.copy(moves = moves, report = report, phase = Phase.REPORT, feedback = null) }
        }
    }

    // ── Hint and eval bar ──────────────────────────────────────────────────

    private fun showHint() {
        hintUsed = true
        val p = position
        val bookMoves = grader.bookMoves(p).take(HINT_BOOK_MOVES).mapNotNull { it.toMove(p) }
        fun hintMove(m: Move, book: Boolean, score: Score?, best: Boolean) =
            HintMove(San.write(p, m), book, this.book.name(p.play(m)), score, best)
        hintJob?.cancel()
        _state.update { s ->
            s.copy(
                hint = Hint(bookMoves.map { hintMove(it, true, null, false) }, engineDone = false),
                arrows = bookMoves.map { Arrow(it.from, it.to, ArrowKind.BOOK) },
            )
        }
        hintJob = scope.launch {
            // Stockfish's own top moves first; then one search scoring those and the book moves
            // together, so every candidate gets a score and the scores are comparable.
            val lines = runCatching {
                val fen = p.toFen()
                val top = engine.analyze(fen, depths.eval, multiPv = HINT_LINES).map { it.move }
                val candidates = (bookMoves.map { it.uci } + top).distinct()
                engine.analyze(fen, depths.eval, searchMoves = candidates, multiPv = candidates.size)
            }.getOrNull()
            if (position != p) return@launch
            val scores = lines.orEmpty().mapNotNull { l -> Uci.parse(p, l.move)?.let { it to l.score } }.toMap()
            val top = scores.values.maxOfOrNull { it.value }
            val isBest = { s: Score? -> s != null && top != null && s.value >= top }
            val extras = scores.entries
                .filter { (m, s) -> m !in bookMoves && top != null && s.value >= top - HINT_MARGIN_CP }
                .sortedByDescending { it.value.value }
                .take(HINT_ENGINE_MOVES)
            _state.update { s ->
                s.copy(
                    hint = Hint(
                        bookMoves.map { hintMove(it, true, scores[it], isBest(scores[it])) } +
                            extras.map { (m, score) -> hintMove(m, false, score, isBest(score)) },
                        engineDone = true,
                    ),
                    arrows = s.arrows + extras.map { (m, _) -> Arrow(m.from, m.to, ArrowKind.ENGINE) },
                )
            }
        }
    }

    private fun updateEval() {
        if (_state.value.setup?.options?.evalBar != true) return
        evalJob?.cancel()
        val p = position
        if (p.legalMoves.isEmpty()) {
            _state.update { it.copy(eval = if (p.isCheckmate) whitePov(Score.mate(0), p) else Score(0)) }
            return
        }
        evalJob = scope.launch {
            val score = runCatching { engine.analyze(p.toFen(), depths.eval).firstOrNull()?.score }.getOrNull() ?: return@launch
            _state.update { it.copy(eval = whitePov(score, p)) }
        }
    }

    private fun whitePov(score: Score, p: Position = position): Score = if (p.sideToMove == Color.WHITE) score else score.negate()

    /** Runs [block]; an engine failure ends up in [TrainerState.error] instead of crashing. */
    private fun launchSafely(block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: e.toString()) }
        }
    }

    private companion object {
        const val HINT_BOOK_MOVES = 4
        /** Stockfish's own candidates considered. */
        const val HINT_LINES = 3
        /** Non-book engine moves this close to the best are listed. */
        const val HINT_MARGIN_CP = 20
        const val HINT_ENGINE_MOVES = 2
    }
}
