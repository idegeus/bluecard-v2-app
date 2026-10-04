package nl.bluecard.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import nl.bluecard.engine.model.Suit
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    val x: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val size: Float,
    val color: Color,
    /** Some pieces are little card suits instead of paper strips. */
    val suit: Suit?,
)

/** A burst of confetti and card suits from the top of the screen, for the winner. Plays once. */
@Composable
fun Confetti(modifier: Modifier = Modifier, seed: Int = 0, durationMs: Long = 4_500) {
    val pieces = remember(seed) {
        val r = Random(seed)
        val colors = listOf(Color(0xFFFFC107), Color(0xFFE53935), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFFFFF), Color(0xFFAB47BC))
        List(110) {
            Piece(
                x = r.nextFloat(),
                vx = (r.nextFloat() - 0.5f) * 0.35f,
                vy = 0.15f + r.nextFloat() * 0.35f,
                spin = (r.nextFloat() - 0.5f) * 720f,
                size = 0.012f + r.nextFloat() * 0.018f,
                color = colors[r.nextInt(colors.size)],
                suit = if (r.nextInt(5) == 0) Suit.entries[r.nextInt(4)] else null,
            )
        }
    }
    var seconds by remember(seed) { mutableFloatStateOf(0f) }
    LaunchedEffect(seed) {
        val start = withFrameNanos { it }
        while (seconds * 1000 < durationMs) {
            withFrameNanos { now -> seconds = (now - start) / 1_000_000_000f }
        }
    }
    if (seconds * 1000 >= durationMs) return
    Canvas(modifier.fillMaxSize()) {
        val t = seconds
        val fade = (1f - (t * 1000 / durationMs - 0.75f) * 4f).coerceIn(0f, 1f)
        for (p in pieces) {
            // Thrown up a little, then falling with gravity and a sideways sway.
            val x = (p.x + p.vx * t) * size.width + sin(t * 3f + p.x * 20f) * size.width * 0.02f
            val y = (-0.08f - 0.25f * t + p.vy * t + 0.35f * t * t) * size.height
            if (y > size.height) continue
            val s = p.size * size.width
            translate(x, y) {
                rotate(p.spin * t, Offset.Zero) {
                    if (p.suit != null) {
                        drawSuit(p.suit, (if (p.suit.isRed) Color(0xFFE53935) else Color(0xFF1F2328)).copy(alpha = fade), Offset(-s, -s), s * 2f)
                    } else {
                        drawRect(p.color.copy(alpha = fade), topLeft = Offset(-s / 2f, -s), size = Size(s, s * 2f))
                    }
                }
            }
        }
    }
}
