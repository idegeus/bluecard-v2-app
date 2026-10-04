package nl.bluecard.app.ui.components

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.bluecard.engine.model.Card
import kotlin.math.abs
import kotlin.math.roundToInt

/** Something the player can pick up from their side of the table and drag. */
sealed interface DragItem {
    /** A face-up card (hand or table). [companions] are selected cards of the same rank that travel along. */
    data class Face(val card: Card, val companions: List<Card> = emptyList()) : DragItem

    /** A face-down table card, by position. */
    data class Blind(val index: Int) : DragItem
}

/** Places a dragged card can be dropped on. */
sealed interface DropTarget {
    data object Pile : DropTarget
    data class FaceUp(val card: Card) : DropTarget
}

/**
 * Decides what happens to a dropped card. Returns true when the move was submitted (the card flies to its
 * target), false to let it spring back. [swipedUp] is true for a clear upward swipe or flick, which counts
 * as "onto the pile" even when the finger did not end on it.
 */
typealias DropHandler = (item: DragItem, target: DropTarget?, swipedUp: Boolean) -> Boolean

/**
 * Shared state of the drag-and-drop on the card table. The dragged card is drawn by [DragOverlay] above
 * everything else, so it is never clipped by the (scrollable) hand it came from.
 */
@Stable
class CardDragState(private val scope: CoroutineScope) {
    var item by mutableStateOf<DragItem?>(null)
        private set
    var cardWidth by mutableStateOf(0.dp)
        private set
    var origin by mutableStateOf(Offset.Zero)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Rotation the card had where it was picked up (e.g. in a fanned hand); it straightens while dragged. */
    var restingRotation by mutableFloatStateOf(0f)
        private set
    private var size by mutableStateOf(IntSize.Zero)
    private val targets = mutableStateMapOf<DropTarget, Rect>()
    private var settleJob: Job? = null

    private val center: Offset get() = origin + offset + Offset(size.width / 2f, size.height / 2f)

    /** The drop target under the dragged card's centre. */
    val hovered: DropTarget?
        get() = if (item == null) null else targets.entries.firstOrNull { it.value.contains(center) }?.key

    /** True when [card] is currently being dragged (as main card or as companion). */
    fun isCarrying(card: Card): Boolean {
        val face = item as? DragItem.Face ?: return false
        return face.card == card || card in face.companions
    }

    fun isCarryingBlind(index: Int): Boolean = (item as? DragItem.Blind)?.index == index

    fun registerTarget(target: DropTarget, bounds: Rect) {
        targets[target] = bounds
    }

    fun unregisterTarget(target: DropTarget) {
        targets.remove(target)
    }

    internal fun begin(item: DragItem, origin: Offset, size: IntSize, width: Dp, rotation: Float) {
        settleJob?.cancel()
        this.restingRotation = rotation
        this.item = item
        this.origin = origin
        this.size = size
        this.cardWidth = width
        this.offset = Offset.Zero
    }

    internal fun moveBy(delta: Offset) {
        offset += delta
    }

    internal fun cancel() {
        settleJob?.cancel()
        item = null
        offset = Offset.Zero
    }

    internal fun release(velocity: Velocity, swipeThresholdPx: Float, onDrop: DropHandler) {
        val current = item ?: return
        val target = hovered
        val swipedUp = offset.y < -swipeThresholdPx ||
            (velocity.y < -FLING_VELOCITY && offset.y < -swipeThresholdPx / 4f)
        val accepted = onDrop(current, target, swipedUp)
        val half = Offset(size.width / 2f, size.height / 2f)
        val destination = if (accepted) {
            targets[target ?: DropTarget.Pile]?.center?.let { it - origin - half } ?: offset
        } else {
            Offset.Zero
        }
        settleJob = scope.launch {
            animate(
                typeConverter = Offset.VectorConverter,
                initialValue = offset,
                targetValue = destination,
                animationSpec = tween(if (accepted) FLY_MS else RETURN_MS),
            ) { value, _ -> offset = value }
            item = null
            offset = Offset.Zero
        }
    }

    private companion object {
        const val FLING_VELOCITY = 1_200f
        const val FLY_MS = 140
        const val RETURN_MS = 220
    }
}

@Composable
fun rememberCardDragState(): CardDragState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CardDragState(scope) }
}

/**
 * Makes [content] draggable. Only a mainly upward movement starts a drag, so sideways swipes keep scrolling
 * a hand of cards. Taps still reach [content] (selecting a card keeps working).
 */
@Composable
fun DraggableCard(
    state: CardDragState,
    item: DragItem,
    enabled: Boolean,
    width: Dp,
    onDrop: DropHandler,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    restingRotation: Float = 0f,
    content: @Composable () -> Unit,
) {
    var topLeft by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentItem by rememberUpdatedState(item)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentRotation by rememberUpdatedState(restingRotation)
    val thresholdPx = with(LocalDensity.current) { (width * CARD_ASPECT * 0.7f).toPx() }
    val dragged = when (item) {
        is DragItem.Face -> state.isCarrying(item.card)
        is DragItem.Blind -> state.isCarryingBlind(item.index)
    }
    Box(
        modifier
            .onGloballyPositioned {
                // Use the centre of the (possibly rotated) card, so a fanned card is picked up where it lies.
                size = it.size
                topLeft = it.boundsInRoot().center - Offset(it.size.width / 2f, it.size.height / 2f)
            }
            .graphicsLayer { alpha = if (dragged || hidden) 0f else 1f }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var initial = Offset.Zero
                    val start = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        // Upward drags pick the card up; anything else is left to the scrolling hand.
                        if (over.y < 0f && abs(over.y) >= abs(over.x)) {
                            change.consume()
                            initial = over
                        }
                    } ?: return@awaitEachGesture
                    state.begin(currentItem, topLeft, size, width, currentRotation)
                    state.moveBy(initial)
                    var released = false
                    try {
                        val completed = drag(start.id) { change ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            state.moveBy(change.positionChange())
                            change.consume()
                        }
                        released = true
                        if (completed) {
                            state.release(tracker.calculateVelocity(), thresholdPx, currentOnDrop)
                        } else {
                            state.cancel()
                        }
                    } finally {
                        // The card left the composition mid-drag (e.g. the state changed): drop it quietly.
                        if (!released) state.cancel()
                    }
                }
            },
    ) {
        content()
    }
}

/** Registers the bounds of a drop target while it is on screen. */
@Composable
fun Modifier.dropTarget(state: CardDragState, target: DropTarget, inflate: Dp = 0.dp): Modifier {
    val inflatePx = with(LocalDensity.current) { inflate.toPx() }
    DisposableEffect(state, target) { onDispose { state.unregisterTarget(target) } }
    return onGloballyPositioned { state.registerTarget(target, it.boundsInRoot().inflate(inflatePx)) }
}

/** Draws the card that is being dragged. Put it last in the screen's root box. */
@Composable
fun DragOverlay(state: CardDragState) {
    val item = state.item ?: return
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot() }) {
        val position = state.origin + state.offset - overlayOrigin
        val overPile = state.hovered == DropTarget.Pile
        Box(
            Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .graphicsLayer {
                    // Start at the card's angle in the hand and straighten out while it travels up.
                    val travel = (abs(state.offset.y) / (state.cardWidth.toPx() * 2f)).coerceIn(0f, 1f)
                    rotationZ = state.restingRotation * (1f - travel) + (state.offset.x / 30f).coerceIn(-14f, 14f)
                    scaleX = 1.08f
                    scaleY = 1.08f
                },
        ) {
            when (item) {
                is DragItem.Face -> {
                    item.companions.forEachIndexed { i, card ->
                        PlayingCard(card, state.cardWidth, Modifier.offset(x = ((i + 1) * 12).dp, y = ((i + 1) * -6).dp))
                    }
                    PlayingCard(item.card, state.cardWidth, highlighted = overPile)
                }
                is DragItem.Blind -> CardBack(state.cardWidth, highlighted = overPile)
            }
        }
    }
}
