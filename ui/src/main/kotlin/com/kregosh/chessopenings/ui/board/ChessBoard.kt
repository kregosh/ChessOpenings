package com.kregosh.chessopenings.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.kregosh.chessopenings.chess.Color
import com.kregosh.chessopenings.chess.Move
import com.kregosh.chessopenings.chess.PieceType
import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.Square
import com.kregosh.chessopenings.core.training.Arrow
import com.kregosh.chessopenings.core.training.ArrowKind
import com.kregosh.chessopenings.ui.theme.BoardColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The chess board. Draws [position] from [orientation]'s side with highlights and arrows,
 * and reports taps and drag-and-drop moves of [movableColor]'s pieces while [interactive].
 */
@Composable
fun ChessBoard(
    position: Position,
    orientation: Color,
    modifier: Modifier = Modifier,
    lastMove: Move? = null,
    selected: Square? = null,
    targets: Set<Square> = emptySet(),
    arrows: List<Arrow> = emptyList(),
    interactive: Boolean = false,
    movableColor: Color = orientation,
    onTap: (Square) -> Unit = {},
    onDrop: (from: Square, to: Square) -> Unit = { _, _ -> },
) {
    val painters = HashMap<Pair<Color, PieceType>, VectorPainter>()
    for (c in Color.entries) for (t in PieceType.entries) painters[c to t] = rememberVectorPainter(Pieces.image(c, t))
    val textMeasurer = rememberTextMeasurer()

    // The piece being dragged and where the pointer is.
    var dragFrom by remember { mutableStateOf<Square?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }
    val currentPosition by rememberUpdatedState(position)
    val currentInteractive by rememberUpdatedState(interactive)
    val currentMovable by rememberUpdatedState(movableColor)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDrop by rememberUpdatedState(onDrop)

    fun squareAt(offset: Offset, boardSize: Float): Square? {
        val sq = boardSize / 8
        val col = (offset.x / sq).toInt()
        val row = (offset.y / sq).toInt()
        if (offset.x < 0 || offset.y < 0 || col !in 0..7 || row !in 0..7) return null
        return if (orientation == Color.WHITE) Square.of(col, 7 - row) else Square.of(7 - col, row)
    }

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(orientation) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!currentInteractive) return@awaitEachGesture
                    val boardSize = min(size.width, size.height).toFloat()
                    val start = squareAt(down.position, boardSize) ?: return@awaitEachGesture
                    val ownPiece = currentPosition.pieceAt(start)?.color == currentMovable
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!dragging && ownPiece && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            dragFrom = start
                        }
                        if (dragging) dragAt = change.position
                        // Checked before consuming: changedToUp() ignores consumed events.
                        val released = change.changedToUp()
                        if (dragging) change.consume()
                        if (released) {
                            if (dragging) {
                                dragFrom = null
                                squareAt(change.position, boardSize)?.let { to -> if (to != start) currentOnDrop(start, to) }
                            } else {
                                currentOnTap(start)
                            }
                            break
                        }
                    }
                    dragFrom = null
                }
            },
    ) {
        val boardSize = min(size.width, size.height)
        val sq = boardSize / 8

        fun topLeft(s: Square): Offset =
            if (orientation == Color.WHITE) Offset(s.file * sq, (7 - s.rank) * sq) else Offset((7 - s.file) * sq, s.rank * sq)

        fun center(s: Square): Offset = topLeft(s) + Offset(sq / 2, sq / 2)

        // Squares and coordinates.
        for (s in Square.all) {
            val light = (s.file + s.rank) % 2 == 1
            drawRect(if (light) BoardColors.light else BoardColors.dark, topLeft(s), Size(sq, sq))
        }
        val labelStyle = TextStyle(fontSize = (sq / 5.5f / density).sp)
        for (i in 0..7) {
            val fileSquare = if (orientation == Color.WHITE) Square.of(i, 0) else Square.of(7 - i, 7)
            val rankSquare = if (orientation == Color.WHITE) Square.of(0, i) else Square.of(7, i)
            val fileLabel = textMeasurer.measure("${'a' + fileSquare.file}", labelStyle.copy(color = coordinateColor(fileSquare)))
            drawText(fileLabel, topLeft = topLeft(fileSquare) + Offset(sq - fileLabel.size.width - sq * 0.05f, sq - fileLabel.size.height))
            val rankLabel = textMeasurer.measure("${'1' + rankSquare.rank}", labelStyle.copy(color = coordinateColor(rankSquare)))
            drawText(rankLabel, topLeft = topLeft(rankSquare) + Offset(sq * 0.05f, 0f))
        }

        // Highlights.
        lastMove?.let { m -> for (s in listOf(m.from, m.to)) drawRect(BoardColors.lastMove, topLeft(s), Size(sq, sq)) }
        selected?.let { drawRect(BoardColors.selected, topLeft(it), Size(sq, sq)) }

        // Pieces; the dragged one follows the pointer, drawn last.
        for (s in Square.all) {
            if (s == dragFrom) continue
            val piece = position.pieceAt(s) ?: continue
            drawPiece(painters.getValue(piece.color to piece.type), topLeft(s), sq)
        }

        // Move targets: dots on empty squares, rings on captures.
        for (t in targets) {
            if (position.pieceAt(t) != null) {
                drawCircle(BoardColors.target, radius = sq * 0.46f, center = center(t), style = Stroke(width = sq * 0.08f))
            } else {
                drawCircle(BoardColors.target, radius = sq * 0.16f, center = center(t))
            }
        }

        for (a in arrows) drawArrow(center(a.from), center(a.to), sq, if (a.kind == ArrowKind.BOOK) BoardColors.bookArrow else BoardColors.engineArrow)

        dragFrom?.let { from ->
            val piece = position.pieceAt(from)
            if (piece != null) drawPiece(painters.getValue(piece.color to piece.type), dragAt - Offset(sq / 2, sq / 2), sq * 1.1f)
        }
    }
}

private fun coordinateColor(s: Square) = if ((s.file + s.rank) % 2 == 1) BoardColors.coordinateOnLight else BoardColors.coordinateOnDark

private fun DrawScope.drawPiece(painter: VectorPainter, topLeft: Offset, size: Float) {
    translate(topLeft.x, topLeft.y) {
        with(painter) { draw(Size(size, size)) }
    }
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, sq: Float, color: androidx.compose.ui.graphics.Color) {
    val angle = atan2(to.y - from.y, to.x - from.x)
    val head = sq * 0.38f
    val width = sq * 0.16f
    // Stop the shaft where the head starts, so the two don't overlap.
    val shaftEnd = Offset(to.x - cos(angle) * head * 0.8f, to.y - sin(angle) * head * 0.8f)
    drawLine(color, from, shaftEnd, strokeWidth = width)
    val left = Offset(to.x - head * cos(angle - 0.5f), to.y - head * sin(angle - 0.5f))
    val right = Offset(to.x - head * cos(angle + 0.5f), to.y - head * sin(angle + 0.5f))
    drawPath(Path().apply { moveTo(to.x, to.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }, color)
}
