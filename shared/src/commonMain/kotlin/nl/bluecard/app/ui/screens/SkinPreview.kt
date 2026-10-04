package nl.bluecard.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.components.CardBack
import nl.bluecard.app.ui.components.DiscardHeap
import nl.bluecard.app.ui.components.DrawStack
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.feltTable
import nl.bluecard.app.ui.theme.CardBackSkin
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.app.ui.theme.TableSkin
import nl.bluecard.engine.model.Card

/** Most the table tilts either way (degrees). */
private const val MAX_TILT = 38f

/**
 * A little table in 3D with [table] as cloth and [back] on the cards: drag to tilt it, let go and it swings back.
 * Works for locked skins too, so you can see what you are playing for.
 */
@Composable
fun SkinPreviewDialog(back: CardBackSkin, table: TableSkin, canChoose: Boolean, onChoose: () -> Unit, onDismiss: () -> Unit) {
    val tiltX = remember { Animatable(18f) }
    val tiltY = remember { Animatable(-8f) }
    val scope = rememberCoroutineScope()
    // A slow idle sway, so it is clearly 3D even before you touch it.
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(3_600, easing = LinearEasing), RepeatMode.Reverse), label = "swayValue",
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Color(0xFF101418), contentColor = Color.White, shape = RoundedCornerShape(20.dp)) {
            Column(
                Modifier.padding(16.dp).widthIn(max = 380.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.skins_preview_title), fontWeight = FontWeight.Black)
                Text(
                    stringResource(R.string.skins_preview_hint),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.78f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch { tiltX.animateTo(18f, spring(dampingRatio = 0.4f)) }
                                    scope.launch { tiltY.animateTo(-8f, spring(dampingRatio = 0.4f)) }
                                },
                            ) { change, drag ->
                                change.consume()
                                scope.launch { tiltY.snapTo((tiltY.value + drag.x * 0.25f).coerceIn(-MAX_TILT, MAX_TILT)) }
                                scope.launch { tiltX.snapTo((tiltX.value - drag.y * 0.25f).coerceIn(-MAX_TILT, MAX_TILT + 20f)) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    MiniTable(
                        back,
                        table,
                        Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                            .graphicsLayer {
                                rotationX = tiltX.value
                                rotationY = tiltY.value + sway * 4f
                                cameraDistance = 9f * density
                            },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) { Text(stringResource(R.string.back), maxLines = 1) }
                    if (canChoose) {
                        Button(
                            onClick = onChoose,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
                        ) { Text(stringResource(R.string.skins_choose), fontWeight = FontWeight.Bold, maxLines = 1) }
                    }
                }
            }
        }
    }
}

/** Just the card back, big, tilting with your finger (and a little by itself) so the pattern catches the light. */
@Composable
fun CardBackPreviewDialog(back: CardBackSkin, canChoose: Boolean, onChoose: () -> Unit, onDismiss: () -> Unit) {
    val tiltX = remember { Animatable(10f) }
    val tiltY = remember { Animatable(-14f) }
    val scope = rememberCoroutineScope()
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Reverse), label = "swayValue",
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Color(0xFF101418), contentColor = Color.White, shape = RoundedCornerShape(20.dp)) {
            Column(
                Modifier.padding(16.dp).widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.skins_preview_title), fontWeight = FontWeight.Black)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.9f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch { tiltX.animateTo(10f, spring(dampingRatio = 0.4f)) }
                                    scope.launch { tiltY.animateTo(-14f, spring(dampingRatio = 0.4f)) }
                                },
                            ) { change, drag ->
                                change.consume()
                                scope.launch { tiltY.snapTo((tiltY.value + drag.x * 0.3f).coerceIn(-MAX_TILT, MAX_TILT)) }
                                scope.launch { tiltX.snapTo((tiltX.value - drag.y * 0.3f).coerceIn(-MAX_TILT, MAX_TILT)) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CardBack(
                        170.dp,
                        Modifier.graphicsLayer {
                            rotationX = tiltX.value
                            rotationY = tiltY.value + sway * 6f
                            cameraDistance = 10f * density
                        },
                        skin = back,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) { Text(stringResource(R.string.back), maxLines = 1) }
                    if (canChoose) {
                        Button(
                            onClick = onChoose,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
                        ) { Text(stringResource(R.string.skins_choose), fontWeight = FontWeight.Bold, maxLines = 1) }
                    }
                }
            }
        }
    }
}

/** A table in the middle of a game: opponents' cards, the piles and your hand. */
@Composable
private fun MiniTable(back: CardBackSkin, table: TableSkin, modifier: Modifier) {
    Box(
        modifier
            .shadow(18.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .feltTable(table),
    ) {
        // Opponents at the top.
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            repeat(3) { i ->
                Box {
                    for (k in 0 until 3) {
                        CardBack(26.dp, Modifier.offset(x = (k * 7).dp).rotate((k - 1) * 9f + i), skin = back)
                    }
                }
            }
        }
        // The piles.
        Row(
            Modifier.align(Alignment.Center).offset(y = (-10).dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawStack(26, 50.dp, skin = back)
            DiscardHeap(listOf(Card.of("7C"), Card.of("9D"), Card.of("QH")), total = 12, width = 54.dp, fanned = true)
        }
        Text(
            stringResource(R.string.game_your_turn),
            color = TableColors.Highlight,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
        )
        // Your hand.
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
            val hand = listOf("4S", "8H", "JC", "KD", "AS")
            hand.forEachIndexed { i, code ->
                val angle = (i - 2) * 9f
                PlayingCard(
                    Card.of(code),
                    48.dp,
                    Modifier.offset(x = ((i - 2) * 30).dp, y = (kotlin.math.abs(i - 2) * 6).dp).rotate(angle),
                    highlighted = i == 3,
                )
            }
            Spacer(Modifier.size(width = 200.dp, height = 70.dp))
        }
    }
}
