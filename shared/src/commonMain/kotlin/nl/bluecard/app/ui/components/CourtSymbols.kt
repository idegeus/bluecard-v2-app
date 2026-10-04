package nl.bluecard.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import nl.bluecard.engine.model.Rank

/** Gold for jewels and bells on the court symbols. */
private val Gold = Color(0xFFE0A800)

/**
 * A small picture for the court cards and the joker: a crown for the king, a tiara for the queen, a feathered cap
 * for the jack and a jester's hat for the joker. [width] is the symbol's width; it is 0.7 × as high.
 */
@Composable
fun CourtSymbol(rank: Rank, color: Color, width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width, width * 0.7f)) {
        when (rank) {
            Rank.KING -> crown(color)
            Rank.QUEEN -> tiara(color)
            Rank.JACK -> cap(color)
            else -> if (rank.isJoker) jesterHat(color)
        }
    }
}

private fun DrawScope.p(x: Float, y: Float) = Offset(x * size.width, y * size.height)

private fun DrawScope.path(vararg points: Pair<Float, Float>): Path = Path().apply {
    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height) }
    close()
}

private fun DrawScope.crown(color: Color) {
    drawPath(path(0.1f to 0.74f, 0.02f to 0.28f, 0.3f to 0.52f, 0.5f to 0.12f, 0.7f to 0.52f, 0.98f to 0.28f, 0.9f to 0.74f), color)
    drawRect(color, topLeft = p(0.1f, 0.76f), size = Size(size.width * 0.8f, size.height * 0.16f))
    val r = size.minDimension * 0.08f
    for ((x, y) in listOf(0.02f to 0.26f, 0.5f to 0.1f, 0.98f to 0.26f)) drawCircle(Gold, r, p(x, y))
    for (x in listOf(0.3f, 0.5f, 0.7f)) drawCircle(Gold, r * 0.7f, p(x, 0.84f))
}

private fun DrawScope.tiara(color: Color) {
    val w = size.width
    val h = size.height
    // A diadem: five pointed peaks (the middle one highest) on a curved band, a pearl on every peak.
    val peaks = listOf(0.08f to 0.56f, 0.29f to 0.38f, 0.5f to 0.12f, 0.71f to 0.38f, 0.92f to 0.56f)
    val valleys = listOf(0.19f to 0.72f, 0.4f to 0.62f, 0.6f to 0.62f, 0.81f to 0.72f)
    val path = Path().apply {
        moveTo(0.03f * w, 0.86f * h)
        lineTo(peaks[0].first * w, peaks[0].second * h)
        for (i in valleys.indices) {
            lineTo(valleys[i].first * w, valleys[i].second * h)
            lineTo(peaks[i + 1].first * w, peaks[i + 1].second * h)
        }
        lineTo(0.97f * w, 0.86f * h)
        quadraticTo(0.5f * w, 1.0f * h, 0.03f * w, 0.86f * h)
        close()
    }
    drawPath(path, color)
    val r = size.minDimension * 0.075f
    peaks.forEachIndexed { i, (x, y) ->
        val c = p(x, y - 0.04f)
        drawCircle(if (i == 2) Gold else Color.White, if (i == 2) r * 1.35f else r, c)
        drawCircle(Gold, if (i == 2) r * 1.35f else r, c, style = Stroke(size.minDimension * 0.02f))
    }
    drawCircle(Color.White, r * 0.55f, p(0.5f, 0.78f))
}

private fun DrawScope.cap(color: Color) {
    val w = size.width
    val h = size.height
    // Feather first, so the cap overlaps its quill.
    val feather = Path().apply {
        moveTo(0.62f * w, 0.5f * h)
        cubicTo(0.75f * w, 0.1f * h, 0.95f * w, 0.0f * h, 1.0f * w, 0.02f * h)
        cubicTo(0.95f * w, 0.25f * h, 0.85f * w, 0.45f * h, 0.66f * w, 0.56f * h)
        close()
    }
    drawPath(feather, Gold)
    drawLine(color, p(0.64f, 0.53f), p(0.96f, 0.06f), strokeWidth = size.minDimension * 0.03f, cap = StrokeCap.Round)
    val dome = Path().apply {
        moveTo(0.2f * w, 0.72f * h)
        cubicTo(0.2f * w, 0.3f * h, 0.8f * w, 0.3f * h, 0.8f * w, 0.72f * h)
        close()
    }
    drawPath(dome, color)
    drawOval(color, topLeft = p(0.02f, 0.66f), size = Size(w * 0.96f, h * 0.22f))
    drawOval(Gold, topLeft = p(0.22f, 0.62f), size = Size(w * 0.56f, h * 0.08f), style = Stroke(size.minDimension * 0.05f))
}

private fun DrawScope.jesterHat(color: Color) {
    val w = size.width
    val h = size.height
    val left = Path().apply {
        moveTo(0.14f * w, 0.8f * h)
        quadraticTo(0.0f * w, 0.55f * h, 0.03f * w, 0.24f * h)
        quadraticTo(0.3f * w, 0.35f * h, 0.42f * w, 0.8f * h)
        close()
    }
    val middle = Path().apply {
        moveTo(0.3f * w, 0.8f * h)
        quadraticTo(0.35f * w, 0.3f * h, 0.5f * w, 0.07f * h)
        quadraticTo(0.65f * w, 0.3f * h, 0.7f * w, 0.8f * h)
        close()
    }
    val right = Path().apply {
        moveTo(0.86f * w, 0.8f * h)
        quadraticTo(1.0f * w, 0.55f * h, 0.97f * w, 0.24f * h)
        quadraticTo(0.7f * w, 0.35f * h, 0.58f * w, 0.8f * h)
        close()
    }
    drawPath(left, color)
    drawPath(right, color)
    // The middle point in the other colour, like a harlequin.
    drawPath(middle, Gold)
    drawRect(color, topLeft = p(0.1f, 0.78f), size = Size(w * 0.8f, h * 0.14f))
    val r = size.minDimension * 0.09f
    for ((x, y) in listOf(0.03f to 0.22f, 0.5f to 0.06f, 0.97f to 0.22f)) {
        drawCircle(Gold, r, p(x, y))
        drawCircle(color, r * 0.35f, p(x, y + 0.03f))
    }
}
