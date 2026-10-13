package com.kregosh.chessopenings.ui.board

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.kregosh.chessopenings.chess.Color
import com.kregosh.chessopenings.chess.PieceType
import androidx.compose.ui.graphics.Color as UiColor

/** One path of a piece drawing, as in the source SVG. Colors are ARGB, null = none. */
internal class PiecePath(
    val d: String,
    val fill: Long?,
    val stroke: Long?,
    val strokeWidth: Float,
    val cap: String,
    val join: String,
    val evenOdd: Boolean,
)

/** The classic black and white pieces as vector images. */
object Pieces {
    private val cache = HashMap<Pair<Color, PieceType>, ImageVector>()

    fun image(color: Color, type: PieceType): ImageVector = cache.getOrPut(color to type) {
        ImageVector.Builder(
            name = "${color.name.lowercase()}_${type.name.lowercase()}",
            defaultWidth = 45.dp,
            defaultHeight = 45.dp,
            viewportWidth = 45f,
            viewportHeight = 45f,
        ).apply {
            for (p in PIECE_PATHS.getValue(color to type)) {
                addPath(
                    pathData = addPathNodes(p.d),
                    pathFillType = if (p.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                    fill = p.fill?.let { SolidColor(UiColor(it)) },
                    stroke = p.stroke?.let { SolidColor(UiColor(it)) },
                    strokeLineWidth = p.strokeWidth,
                    strokeLineCap = when (p.cap) {
                        "round" -> StrokeCap.Round
                        "square" -> StrokeCap.Square
                        else -> StrokeCap.Butt
                    },
                    strokeLineJoin = when (p.join) {
                        "round" -> StrokeJoin.Round
                        "bevel" -> StrokeJoin.Bevel
                        else -> StrokeJoin.Miter
                    },
                )
            }
        }.build()
    }
}
