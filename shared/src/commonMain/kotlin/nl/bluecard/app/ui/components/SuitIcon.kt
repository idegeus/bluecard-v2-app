package nl.bluecard.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Suit

/**
 * Suit symbols drawn as vector paths (24×24 design grid). Drawing them ourselves avoids fonts that
 * render ♥/♦ as coloured emoji and works fully offline.
 */
private object SuitPaths {
    val heart: Path = PathParser().parsePathString(
        "M12,21.35 L10.55,20.03 C5.4,15.36 2,12.28 2,8.5 C2,5.42 4.42,3 7.5,3 C9.24,3 10.91,3.81 12,5.09 " +
            "C13.09,3.81 14.76,3 16.5,3 C19.58,3 22,5.42 22,8.5 C22,12.28 18.6,15.36 13.45,20.04 Z",
    ).toPath()
    val diamond: Path = PathParser().parsePathString("M12,1.5 L20.5,12 L12,22.5 L3.5,12 Z").toPath()
    val spade: Path = PathParser().parsePathString(
        "M12,1.5 C9.2,5.6 3.5,9.3 3.5,13.6 C3.5,16.6 6,18.4 8.7,17.7 C9.9,17.4 10.7,16.7 11.2,16 " +
            "L10,22.5 L14,22.5 L12.8,16 C13.3,16.7 14.1,17.4 15.3,17.7 C18,18.4 20.5,16.6 20.5,13.6 C20.5,9.3 14.8,5.6 12,1.5 Z",
    ).toPath()
    val clubStem: Path = PathParser().parsePathString("M12,11 L9.8,22.5 L14.2,22.5 Z").toPath()
}

fun suitColor(suit: Suit): Color = if (suit.isRed) TableColors.CardRed else TableColors.CardBlack

/** Draws [suit] into a square of [size] px whose top-left corner is [topLeft]. */
fun DrawScope.drawSuit(suit: Suit, color: Color, topLeft: Offset, size: Float) {
    translate(topLeft.x, topLeft.y) {
        scale(size / 24f, size / 24f, pivot = Offset.Zero) {
            when (suit) {
                Suit.HEARTS -> drawPath(SuitPaths.heart, color)
                Suit.DIAMONDS -> drawPath(SuitPaths.diamond, color)
                Suit.SPADES -> drawPath(SuitPaths.spade, color)
                Suit.CLUBS -> {
                    drawCircle(color, radius = 4.6f, center = Offset(12f, 6.6f))
                    drawCircle(color, radius = 4.6f, center = Offset(6.9f, 13.6f))
                    drawCircle(color, radius = 4.6f, center = Offset(17.1f, 13.6f))
                    drawCircle(color, radius = 2.6f, center = Offset(12f, 11.5f))
                    drawPath(SuitPaths.clubStem, color)
                }
            }
        }
    }
}

@Composable
fun SuitIcon(suit: Suit, size: Dp, modifier: Modifier = Modifier, color: Color = suitColor(suit)) {
    Canvas(modifier.size(size)) {
        drawSuit(suit, color, Offset.Zero, this.size.minDimension)
    }
}
