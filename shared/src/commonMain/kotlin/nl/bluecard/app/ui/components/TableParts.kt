package nl.bluecard.app.ui.components

import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.bluecard.app.ui.theme.TableColors

/* Pieces of the card table shared by every game screen. */

/** Soft warm light under the hand when it is your turn. */
@Composable
fun TurnGlowBackground(modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.28f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    Box(
        modifier.drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(TableColors.TurnGlow.copy(alpha = pulse), TableColors.TurnGlow.copy(alpha = pulse * 0.35f), Color.Transparent),
                    // Centred low and fading out before any edge, so there is no visible border.
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.82f),
                    radius = minOf(size.width * 0.5f, size.height * 0.8f),
                ),
            )
        },
    )
}

/** An opponent's hand: a small fan of card backs with the number of cards on it. */
@Composable
fun MiniFanBacks(count: Int, width: Dp) {
    val shown = minOf(count, 5)
    val mid = (shown - 1) / 2f
    Box(Modifier.size(width * 2.2f, width * CARD_ASPECT + 4.dp), contentAlignment = Alignment.BottomCenter) {
        repeat(shown) { i ->
            val d = i - mid
            CardBack(
                width,
                Modifier.graphicsLayer {
                    translationX = d * width.toPx() * 0.2f
                    rotationZ = d * 8f
                    transformOrigin = TransformOrigin(0.5f, 1.3f)
                },
            )
        }
        Text(
            count.toString(),
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp),
        )
    }
}

@Composable
fun KeepScreenOn(enabled: Boolean) {
    platformUi().KeepScreenOn(enabled)
}


/** How a button in the table's action bar looks. */
enum class BarTone { PRIMARY, DANGER, ALERT, OUTLINE, HIGHLIGHT_OUTLINE, TONAL }

data class BarAction(val label: String, val tone: BarTone, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The buttons at the bottom of the table, always on one line: each gets an equal share of the width at most and
 * its text shrinks when the screen is narrow, instead of wrapping onto a second row.
 */
@Composable
fun TableActionBar(actions: List<BarAction>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (action in actions) BarButton(action, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun BarButton(action: BarAction, modifier: Modifier) {
    val (background, content, border) = when (action.tone) {
        BarTone.PRIMARY -> Triple(TableColors.TurnGlow, TableColors.CardBlack, null)
        BarTone.DANGER -> Triple(TableColors.Danger, Color.White, null)
        BarTone.ALERT -> Triple(TableColors.Highlight, TableColors.CardBlack, null)
        BarTone.TONAL -> Triple(TableColors.Ink.copy(alpha = 0.16f), TableColors.OnFelt, null)
        BarTone.OUTLINE -> Triple(Color.Transparent, TableColors.OnFelt, TableColors.Ink.copy(alpha = 0.55f))
        BarTone.HIGHLIGHT_OUTLINE -> Triple(Color.Transparent, TableColors.TurnGlow, TableColors.TurnGlow)
    }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 46.dp)
            .alpha(if (action.enabled) 1f else 0.45f)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            action.label,
            maxLines = 1,
            style = TextStyle(
                color = content,
                fontWeight = if (action.tone == BarTone.OUTLINE || action.tone == BarTone.TONAL) FontWeight.SemiBold else FontWeight.Black,
                textAlign = TextAlign.Center,
            ),
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp, stepSize = 0.5.sp),
        )
    }
}

/** Text that slides in when it changes (the latest event, what is required, …). */
@Composable
fun SlidingText(text: String, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn(tween(220))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(160)))
        },
        label = "slidingText",
    ) { content(it) }
}

/** Makes an element bounce briefly every time [trigger] becomes true (e.g. "Jij bent aan de beurt!"). */
@Composable
fun Modifier.popWhen(trigger: Boolean): Modifier {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            scale.snapTo(0.8f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
