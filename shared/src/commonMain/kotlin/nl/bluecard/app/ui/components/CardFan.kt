package nl.bluecard.app.ui.components

import kotlin.math.PI
import nl.bluecard.app.platform.nowMillis
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.bluecard.engine.model.Card

/** Geometry of one card in the fan. */
private data class FanSlot(val x: Dp, val y: Dp, val angle: Float)

/** Rotation pivot below the card (fraction of its height), which makes the cards spread like a hand. */
const val FAN_PIVOT = 1.35f

/**
 * The closed fan: cards along a gentle arc, using the full width of the screen. Spacing and angle shrink as
 * the hand grows, so even a big hand fits the screen (the rotated corners of the outer cards are taken into
 * account). Scrolling sideways opens it up further ([fanSlotsOpen]).
 */
private fun fanSlots(count: Int, cardWidth: Dp, availableWidth: Dp): List<FanSlot> {
    if (count == 0) return emptyList()
    if (count == 1) return listOf(FanSlot(0.dp, 0.dp, 0f))
    val w = cardWidth.value
    val h = w * CARD_ASPECT
    val half = availableWidth.value / 2f - 6f
    val mid = (count - 1) / 2f
    var anglePer = minOf(7f, 30f / (count - 1))
    var step = w * 0.6f
    // Shrink spacing (and if needed the angle) until the outermost rotated card fits.
    repeat(60) {
        if (outerExtent(step, anglePer, mid, w, h) <= half) return@repeat
        if (step > w * 0.2f) step *= 0.95f else anglePer *= 0.9f
    }
    val radius = w * 4.5f
    return List(count) { i ->
        val d = i - mid
        val angle = d * anglePer
        val drop = radius * (1f - kotlin.math.cos(((angle.toDouble()) * PI / 180.0)).toFloat())
        FanSlot((step * d).dp, drop.dp, angle)
    }
}

/** How long an opened fan stays open after the last scroll while no card is selected. */
private const val FAN_IDLE_MS = 3_000L

/**
 * The opened fan: comfortable spacing regardless of screen width (it may run off the screen and is scrolled
 * sideways) and a flatter arc, so every card is easy to tap or swipe.
 */
private fun fanSlotsOpen(count: Int, cardWidth: Dp): List<FanSlot> {
    if (count == 0) return emptyList()
    if (count == 1) return listOf(FanSlot(0.dp, 0.dp, 0f))
    val w = cardWidth.value
    val step = w * OPEN_STEP
    val anglePer = openAnglePer(count)
    val mid = (count - 1) / 2f
    val radius = w * 7f
    return List(count) { i ->
        val d = i - mid
        val angle = d * anglePer
        val drop = radius * (1f - kotlin.math.cos(((angle.toDouble()) * PI / 180.0)).toFloat())
        FanSlot((step * d).dp, drop.dp, angle)
    }
}

private const val OPEN_STEP = 0.58f

private fun openAnglePer(count: Int): Float = minOf(4f, 24f / (count - 1))

/** Horizontal distance from the fan centre to the outer corner of the outermost card of the opened fan. */
private fun openFanExtent(count: Int, cardWidth: Dp): Dp {
    if (count < 2) return cardWidth / 2
    val w = cardWidth.value
    return outerExtent(w * OPEN_STEP, openAnglePer(count), (count - 1) / 2f, w, w * CARD_ASPECT).dp
}

/** Horizontal distance from the fan centre to the furthest corner of the outermost (rotated) card. */
private fun outerExtent(step: Float, anglePer: Float, mid: Float, w: Float, h: Float): Float {
    val angle = (mid * anglePer).toDouble() * PI / 180.0
    val sin = kotlin.math.sin(angle).toFloat()
    val cos = kotlin.math.cos(angle).toFloat()
    val pivotY = (FAN_PIVOT - 0.5f) * h // pivot below the card centre
    var max = 0f
    for ((cx, cy) in listOf(-w / 2f to -h / 2f, w / 2f to -h / 2f, w / 2f to h / 2f, -w / 2f to h / 2f)) {
        // Rotate the corner around the pivot.
        val px = cx
        val py = cy - pivotY
        val rx = px * cos - py * sin
        max = maxOf(max, rx)
    }
    return step * mid + max
}

/**
 * The player's hand as a curved, overlapping fan, used by every game. Cards can be dragged up (to play) and
 * tapped; scrolling sideways opens the fan so the right card is easy to pick.
 *
 * @param hidden cards that were swiped away and wait for the new state; they leave the fan.
 * @param anySelected while a card is selected the opened fan stays open.
 */
@Composable
fun CardFan(
    cards: List<Card>,
    hidden: Set<Card>,
    isMyTurn: Boolean,
    anySelected: Boolean,
    dragState: CardDragState,
    onDrop: DropHandler,
    dragEnabled: Boolean,
    dragItem: (Card) -> DragItem,
    cardWidth: Dp,
    availableWidth: Dp,
    modifier: Modifier = Modifier,
    cardContent: @Composable (Card) -> Unit,
) {
    // Swiped cards have left the hand: drop them from the fan so the others close up (and nothing invisible
    // stays touchable).
    val shown = cards.filter { it !in hidden }

    // Scrolling sideways through the hand opens the fan: more room between the cards (it may run off the
    // screen) so the right card is easy to pick. It closes when your turn is over, or after a few seconds
    // without a selected card.
    var expanded by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var scrolling by remember { mutableStateOf(false) }
    val scroll = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val compactSlots = fanSlots(shown.size, cardWidth, availableWidth)
    val openSlots = fanSlotsOpen(shown.size, cardWidth)
    // A small hand already has room; only open up when that actually spreads the cards further.
    val canOpen = openSlots.isNotEmpty() && openSlots.last().x > compactSlots.last().x
    val slots = if (expanded && canOpen) openSlots else compactSlots
    // Scroll far enough that even the rotated corner of the outermost card comes fully on screen, with a margin.
    val maxScrollPx = with(density) {
        if (!expanded || !canOpen) 0f else maxOf(0f, (openFanExtent(shown.size, cardWidth) + 16.dp - availableWidth / 2).toPx())
    }
    val currentMax by rememberUpdatedState(maxScrollPx)

    fun collapse() {
        expanded = false
        scope.launch { scroll.animateTo(0f) }
    }
    // Done laying: the turn has passed.
    LaunchedEffect(isMyTurn) { if (!isMyTurn) collapse() }
    // Nothing picked after a short while (or just browsing outside your turn): close again.
    LaunchedEffect(expanded, lastTouch, anySelected, scrolling) {
        if (expanded && !anySelected && !scrolling) {
            delay(FAN_IDLE_MS)
            collapse()
        }
    }
    LaunchedEffect(maxScrollPx) {
        if (kotlin.math.abs(scroll.value) > maxScrollPx) scroll.snapTo(scroll.value.coerceIn(-maxScrollPx, maxScrollPx))
    }

    Box(
        modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = {
                    expanded = true
                    scrolling = true
                },
                onDragEnd = {
                    scrolling = false
                    lastTouch = nowMillis()
                },
                onDragCancel = {
                    scrolling = false
                    lastTouch = nowMillis()
                },
            ) { change, dragAmount ->
                change.consume()
                scope.launch { scroll.snapTo((scroll.value + dragAmount).coerceIn(-currentMax, currentMax)) }
            }
        },
        contentAlignment = Alignment.BottomCenter,
    ) {
        shown.forEachIndexed { index, card ->
            key(card) {
                val slot = slots[index]
                val x by animateDpAsState(slot.x, label = "fanX")
                val y by animateDpAsState(slot.y, label = "fanY")
                val angle by animateFloatAsState(slot.angle, label = "fanAngle")
                DraggableCard(
                    dragState,
                    dragItem(card),
                    enabled = dragEnabled,
                    width = cardWidth,
                    onDrop = onDrop,
                    restingRotation = angle,
                    modifier = Modifier
                        // Leave room at the bottom for the arc and at the top for lifted cards.
                        .padding(bottom = cardWidth * 0.3f)
                        .graphicsLayer {
                            translationX = with(density) { x.toPx() } + scroll.value
                            translationY = with(density) { y.toPx() }
                            rotationZ = angle
                            transformOrigin = TransformOrigin(0.5f, FAN_PIVOT)
                        },
                    hidden = card in hidden,
                ) {
                    cardContent(card)
                }
            }
        }
    }
}
