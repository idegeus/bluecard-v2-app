package nl.bluecard.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.bluecard.app.R
import nl.bluecard.app.platform.nowMillis
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.theme.TableColors

/**
 * What is happening ([label]) above a bar that drains smoothly from full to empty between [startedAt] and [endsAt]
 * (wall-clock millis), with the time left ("nog 1:12"). The bar turns from gold to red in its last fifth.
 */
@Composable
fun CountdownBar(label: String, startedAt: Long, endsAt: Long, modifier: Modifier = Modifier) {
    val total = (endsAt - startedAt).coerceAtLeast(1L)
    val left = remember(startedAt, endsAt) { Animatable(fractionLeft(startedAt, endsAt)) }
    LaunchedEffect(startedAt, endsAt) {
        val remaining = (endsAt - nowMillis()).coerceAtLeast(0L)
        left.snapTo(remaining.toFloat() / total)
        left.animateTo(0f, tween(remaining.toInt(), easing = LinearEasing))
    }
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(startedAt, endsAt) {
        while (true) {
            now = nowMillis()
            if (now >= endsAt) break
            delay(TICK_MS)
        }
    }
    val fraction = left.value
    val color = if (fraction > 0.2f) TableColors.TurnGlow else lerp(TableColors.Danger, TableColors.TurnGlow, fraction / 0.2f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TableColors.OnFelt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.countdown_left, clock(endsAt - now)),
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.3f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

private fun fractionLeft(startedAt: Long, endsAt: Long): Float =
    ((endsAt - nowMillis()).toFloat() / (endsAt - startedAt).coerceAtLeast(1L)).coerceIn(0f, 1f)

/** "1:05", "0:09": minutes and seconds, rounded up so it reads 0:00 only when time is up. */
internal fun clock(millis: Long): String {
    val seconds = ((millis.coerceAtLeast(0L) + 999L) / 1000L).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private const val TICK_MS = 250L
