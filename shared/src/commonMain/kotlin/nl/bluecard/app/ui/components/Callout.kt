package nl.bluecard.app.ui.components

import kotlin.math.PI
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.bluecard.app.platform.Sound
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import kotlin.math.sin

enum class CalloutKind(val color: Color) {
    /** A cheat was caught. */
    CHEAT(Color(0xFFE53935)),

    /** Somebody called "Vals!" wrongly. */
    FALSE_ALARM(Color(0xFFFFA000)),

    /** Somebody forgot to call "Laatste kaart!". */
    FORGOT(Color(0xFFFF7043)),
}

/**
 * A big moment at the table, shown as a stamp. [id] makes every occurrence play again. [mine]: it is about this
 * phone's player (caught, wrong or forgotten). At a [sharedTable] only that player's phone makes the sound, so
 * everybody hears whose phone it is.
 */
data class Callout(
    val id: Long,
    val kind: CalloutKind,
    val stamp: String,
    val text: String,
    val cards: List<Card> = emptyList(),
    val mine: Boolean = false,
    val sharedTable: Boolean = false,
)

/**
 * The stamp slams down onto the table, shakes, shows what happened and goes away by itself after a few seconds
 * (or when tapped).
 */
@Composable
fun CalloutOverlay(callout: Callout?, onDone: () -> Unit) {
    if (callout == null) return
    val container = appContainer()
    val sounds = container.sounds
    val slam = remember(callout.id) { Animatable(0f) }
    val shake = remember(callout.id) { Animatable(0f) }
    val fade = remember(callout.id) { Animatable(1f) }
    LaunchedEffect(callout.id) {
        val sound = when {
            // Caught: your own phone gives you away with a siren and a rattle.
            callout.kind == CalloutKind.CHEAT && callout.mine -> Sound.BUSTED
            callout.sharedTable && !callout.mine -> null
            callout.kind == CalloutKind.CHEAT -> Sound.CHEAT
            callout.kind == CalloutKind.FALSE_ALARM -> Sound.FALSE_ALARM
            else -> Sound.FORGOT
        }
        sound?.let(sounds::play)
        if (callout.mine) container.platform.haptics.buzz()
        launch { slam.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        delay(180)
        shake.animateTo(1f, tween(520))
        delay(2_100)
        fade.animateTo(0f, tween(300))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * fade.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp).graphicsLayer {
                alpha = fade.value
                val scale = 3f - 2f * slam.value
                scaleX = scale
                scaleY = scale
                // A few quick shakes right after the slam.
                translationX = sin(shake.value * 6 * PI).toFloat() * (1f - shake.value) * 18.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Always one line: long words ("ONTERECHT!", "¡FALSA ALARMA!") shrink to fit the screen.
            BasicText(
                callout.stamp,
                style = TextStyle(color = callout.kind.color, fontWeight = FontWeight.Black, letterSpacing = 3.sp, textAlign = TextAlign.Center),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 64.sp, stepSize = 2.sp),
                modifier = Modifier
                    .rotate(-9f)
                    .border(5.dp, callout.kind.color, RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 2.dp),
            )
            if (callout.cards.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                    callout.cards.take(4).forEachIndexed { i, card ->
                        PlayingCard(card, 62.dp, Modifier.rotate((i - (callout.cards.size - 1) / 2f) * 8f))
                    }
                }
            }
            Text(
                callout.text,
                color = TableColors.OnFelt,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TableColors.FeltDark.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
