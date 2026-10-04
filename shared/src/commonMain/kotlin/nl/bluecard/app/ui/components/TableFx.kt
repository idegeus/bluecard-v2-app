package nl.bluecard.app.ui.components

import nl.bluecard.app.platform.nowMillis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.bluecard.engine.model.Card
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Well-known places on the table that cards fly between. Players use [player]. */
object FxAnchor {
    const val PILE = "pile"
    const val DRAW = "draw"
    const val HAND = "hand"
    const val TABLE = "table"
    fun player(id: String) = "player:$id"
}

/** A card on its way across the table. [card] null = shown face down; [flip] turns it face up on the way. */
@Stable
class Flight(
    val id: Long,
    val card: Card?,
    val from: String,
    val to: String,
    val width: Dp,
    val delayMs: Long,
    val durationMs: Int,
    val flip: Boolean,
)

/** One of the last (blind) cards being turned over, big and slow, before it lands on [to]. */
class Reveal(val id: Long, val card: Card, val from: String, val to: String, val width: Dp, val success: Boolean, val delayMs: Long)

/** Cards burning on the pile (a 10 or four of a kind). */
class Burn(val id: Long, val cards: List<Card>, val at: String, val width: Dp, val delayMs: Long)

/**
 * Table animations shared by every game screen: anchors register where things are on screen, and
 * [FxOverlay] draws the cards that fly between them, flips and burning piles. The game screens translate
 * their game's events into calls on this class.
 */
@Stable
class TableFx {
    private val anchors = mutableStateMapOf<String, Rect>()
    internal val flights = mutableStateListOf<Flight>()
    internal val burns = mutableStateListOf<Burn>()
    internal val reveals = mutableStateListOf<Reveal>()
    private val incoming = mutableStateMapOf<String, Int>()
    private var nextId = 1L

    /** Cards of mine that were dragged onto the pile: they already flew there. */
    private val selfPlayed = mutableSetOf<Card>()
    private var selfBlind = false

    /** A blind card was dragged onto the pile: it should turn over right there. */
    fun markSelfPlayedBlind() {
        selfBlind = true
    }

    fun consumeSelfPlayedBlind(): Boolean = selfBlind.also { selfBlind = false }

    internal fun register(key: String, rect: Rect) {
        anchors[key] = rect
    }

    internal fun unregister(key: String) {
        anchors.remove(key)
    }

    internal fun anchor(key: String): Rect? = anchors[key]

    /** Cards still flying towards [key]; that place can hide them until they land. */
    fun incoming(key: String): Int = incoming[key] ?: 0

    /** The face-up cards flying towards [key] (e.g. drawn cards on their way into your hand). */
    fun incomingCards(key: String): Set<Card> = flights.filter { it.to == key }.mapNotNull { it.card }.toSet()

    fun markSelfPlayed(cards: Collection<Card>) {
        selfPlayed += cards
    }

    /** True (once) when these cards were played by dragging, so no extra flight is needed. */
    fun consumeSelfPlayed(cards: Collection<Card>): Boolean {
        if (cards.isEmpty() || !selfPlayed.containsAll(cards)) return false
        selfPlayed -= cards.toSet()
        return true
    }

    /**
     * Lets [cards] fly from [from] to [to], one after the other. Null entries fly face down. Nothing happens when
     * either place is not on screen.
     */
    fun fly(
        cards: List<Card?>,
        from: String,
        to: String,
        width: Dp = 56.dp,
        flip: Boolean = false,
        staggerMs: Long = 70,
        durationMs: Int = 360,
        startDelayMs: Long = 0,
    ) {
        if (anchors[from] == null || anchors[to] == null || cards.isEmpty()) return
        cards.forEachIndexed { i, card ->
            flights += Flight(nextId++, card, from, to, width, startDelayMs + i * staggerMs, durationMs, flip)
        }
        incoming[to] = incoming(to) + cards.size
    }

    /**
     * The dramatic version of turning over a blind card: it comes to the middle of the table, trembles, turns over
     * slowly and flashes green ([success]) or red, then lands on [to]. Takes [REVEAL_MS].
     */
    fun reveal(card: Card, from: String, success: Boolean, to: String = FxAnchor.PILE, width: Dp = 56.dp, delayMs: Long = 0) {
        if (anchors[from] == null || anchors[to] == null) return
        reveals += Reveal(nextId++, card, from, to, width, success, delayMs)
        incoming[to] = incoming(to) + 1
    }

    internal fun revealed(reveal: Reveal) {
        reveals.remove(reveal)
        incoming[reveal.to] = (incoming(reveal.to) - 1).coerceAtLeast(0)
    }

    fun burn(cards: List<Card>, at: String = FxAnchor.PILE, width: Dp = 64.dp, delayMs: Long = 0) {
        if (anchors[at] == null || cards.isEmpty()) return
        burns += Burn(nextId++, cards.takeLast(6), at, width, delayMs)
    }

    internal fun landed(flight: Flight) {
        flights.remove(flight)
        incoming[flight.to] = (incoming(flight.to) - 1).coerceAtLeast(0)
    }

    /**
     * Waits until every running animation (flying cards, burning pile, blind reveal) is done, plus [lingerMs] to
     * look at the final table — at most [maxMs]. Used before showing the summary, so the last move is seen.
     */
    suspend fun awaitIdle(lingerMs: Long = 900, maxMs: Long = 7_000) {
        val start = nowMillis()
        // The last view's effects are scheduled right after it arrives.
        delay(150)
        while ((flights.isNotEmpty() || burns.isNotEmpty() || reveals.isNotEmpty()) && nowMillis() - start < maxMs) delay(100)
        delay(lingerMs)
    }

    internal fun burnt(burn: Burn) {
        burns.remove(burn)
    }
}

@Composable
fun rememberTableFx(): TableFx = remember { TableFx() }

/** The animations of the table on screen (provided by the game screens). */
val LocalTableFx = staticCompositionLocalOf<TableFx?> { null }

/** Registers where this element is, so cards can fly to and from it. No-op outside a game table. */
@Composable
fun Modifier.fxAnchor(key: String): Modifier {
    val fx = LocalTableFx.current ?: return this
    DisposableEffect(fx, key) { onDispose { fx.unregister(key) } }
    return onGloballyPositioned { fx.register(key, it.boundsInRoot()) }
}

/** Draws all running table animations. Put it near the end of the screen's root box (above the table). */
@Composable
fun FxOverlay(fx: TableFx) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().onGloballyPositioned {
            origin = it.positionInRoot()
            size = Offset(it.size.width.toFloat(), it.size.height.toFloat())
        },
    ) {
        for (flight in fx.flights.toList()) key(flight.id) { FlyingCard(fx, flight, origin) }
        for (burn in fx.burns.toList()) key(burn.id) { BurningPile(fx, burn, origin) }
        for (reveal in fx.reveals.toList()) key(reveal.id) { DramaticReveal(fx, reveal, origin, size) }
    }
}

@Composable
private fun FlyingCard(fx: TableFx, flight: Flight, origin: Offset) {
    val from = fx.anchor(flight.from)?.center
    val to = fx.anchor(flight.to)?.center
    val progress = remember { Animatable(0f) }
    var started by remember { mutableStateOf(flight.delayMs == 0L) }
    LaunchedEffect(flight.id) {
        if (flight.delayMs > 0) delay(flight.delayMs)
        started = true
        progress.animateTo(1f, tween(flight.durationMs, easing = FastOutSlowInEasing))
        fx.landed(flight)
    }
    if (from == null || to == null || !started) return
    val density = LocalDensity.current
    val widthPx = with(density) { flight.width.toPx() }
    val heightPx = widthPx * CARD_ASPECT
    val p = progress.value
    // A gentle arc, like a card tossed onto the table.
    val lift = sin(p * PI).toFloat() * heightPx * 0.35f
    val x = from.x + (to.x - from.x) * p - widthPx / 2f - origin.x
    val y = from.y + (to.y - from.y) * p - heightPx / 2f - lift - origin.y
    val showFace = flight.card != null && (!flight.flip || p > 0.5f)
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .graphicsLayer {
                rotationZ = (1f - p) * -12f + p * 4f
                if (flight.flip) {
                    // Turn over halfway: the back rotates away, the face rotates in.
                    rotationY = if (p < 0.5f) p * 180f else (p - 1f) * 180f
                    cameraDistance = 12f * density.density
                }
                val grow = 1f + sin(p * PI).toFloat() * 0.12f
                scaleX = grow
                scaleY = grow
            },
    ) {
        if (showFace) PlayingCard(flight.card!!, flight.width) else CardBack(flight.width)
    }
}

@Composable
private fun DramaticReveal(fx: TableFx, reveal: Reveal, origin: Offset, area: Offset) {
    val from = fx.anchor(reveal.from)?.center
    val to = fx.anchor(reveal.to)?.center
    val progress = remember { Animatable(0f) }
    var started by remember { mutableStateOf(reveal.delayMs == 0L) }
    LaunchedEffect(reveal.id) {
        if (reveal.delayMs > 0) delay(reveal.delayMs)
        started = true
        progress.animateTo(1f, tween(REVEAL_MS.toInt(), easing = LinearEasing))
        fx.revealed(reveal)
    }
    if (from == null || to == null || !started) return
    val density = LocalDensity.current
    val widthPx = with(density) { reveal.width.toPx() }
    val heightPx = widthPx * CARD_ASPECT
    val p = progress.value
    // The middle of the table, a little above the centre.
    val stage = Offset(area.x / 2f + origin.x, area.y * 0.42f + origin.y)
    fun ease(t: Float) = (1f - cos(t.coerceIn(0f, 1f) * PI).toFloat()) / 2f
    val (pos, scale) = when {
        p < 0.18f -> lerp(from, stage, ease(p / 0.18f)) to 1f + 1.2f * ease(p / 0.18f)
        p < 0.86f -> stage to 2.2f
        else -> lerp(stage, to, ease((p - 0.86f) / 0.14f)) to 2.2f - 1.2f * ease((p - 0.86f) / 0.14f)
    }
    // Trembling while the drum rolls, faster and faster.
    val tremble = if (p in 0.18f..0.46f) {
        val t = (p - 0.18f) / 0.28f
        sin(t * t * 60f) * 7f * (0.4f + t)
    } else {
        0f
    }
    val flip = ease((p - 0.46f) / 0.22f)
    val showFace = flip > 0.5f
    val flash = if (p in 0.68f..0.86f) sin((p - 0.68f) / 0.18f * PI).toFloat() else 0f
    val flashColor = if (reveal.success) Color(0xFF66BB6A) else Color(0xFFE53935)
    // Darken the table a little while all eyes are on the card.
    val dim = when {
        p < 0.15f -> p / 0.15f
        p > 0.88f -> (1f - p) / 0.12f
        else -> 1f
    }.coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color.Black.copy(alpha = 0.35f * dim))
        if (flash > 0f) {
            val c = stage - origin
            drawCircle(flashColor.copy(alpha = 0.55f * flash), radius = heightPx * 1.6f * (0.6f + flash), center = c)
        }
    }
    Box(
        Modifier
            .offset { IntOffset((pos.x - origin.x - widthPx / 2f).roundToInt(), (pos.y - origin.y - heightPx / 2f).roundToInt()) }
            .graphicsLayer {
                scaleX = scale * (1f + 0.08f * flash)
                scaleY = scale * (1f + 0.08f * flash)
                rotationZ = tremble
                rotationY = if (flip < 0.5f) flip * 180f else (flip - 1f) * 180f
                cameraDistance = 14f * density.density
            },
    ) {
        if (showFace) PlayingCard(reveal.card, reveal.width, highlighted = flash > 0f) else CardBack(reveal.width)
    }
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** Duration of [TableFx.reveal]: well over two seconds, it is the moment of the game. */
const val REVEAL_MS = 2_400L

/** Flames lick up from the pile while the burnt cards curl, glow and fade away. */
@Composable
private fun BurningPile(fx: TableFx, burn: Burn, origin: Offset) {
    val rect = fx.anchor(burn.at) ?: return
    val progress = remember { Animatable(0f) }
    var started by remember { mutableStateOf(burn.delayMs == 0L) }
    LaunchedEffect(burn.id) {
        if (burn.delayMs > 0) delay(burn.delayMs)
        started = true
        progress.animateTo(1f, tween(BURN_MS, easing = LinearEasing))
        fx.burnt(burn)
    }
    if (!started) return
    val density = LocalDensity.current
    val widthPx = with(density) { burn.width.toPx() }
    val heightPx = widthPx * CARD_ASPECT
    val p = progress.value
    val center = rect.center - origin
    burn.cards.forEachIndexed { i, card ->
        val spread = (i - (burn.cards.size - 1) / 2f) * widthPx * 0.18f
        Box(
            Modifier
                .offset { IntOffset((center.x - widthPx / 2f + spread).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
                .graphicsLayer {
                    alpha = (1f - p * 1.4f).coerceIn(0f, 1f)
                    scaleX = 1f + p * 0.25f
                    scaleY = 1f - p * 0.3f
                    rotationZ = spread / widthPx * 30f * p
                    translationY = -p * heightPx * 0.4f
                },
        ) { PlayingCard(card, burn.width) }
    }
    val particles = remember(burn.id) { List(46) { FlameParticle.random(Random(burn.id * 31 + it)) } }
    Canvas(Modifier.fillMaxSize()) {
        for (f in particles) {
            val t = ((p - f.start) / f.life).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) continue
            val x = center.x + f.dx * widthPx * 0.9f + sin(t * 6f + f.dx * 10f) * widthPx * 0.06f
            val y = center.y + heightPx * 0.45f - t * heightPx * f.rise
            val color = when {
                t < 0.3f -> Color(0xFFFFF59D)
                t < 0.6f -> Color(0xFFFFA726)
                else -> Color(0xFFE53935)
            }
            drawCircle(color.copy(alpha = (1f - t) * 0.85f), radius = widthPx * f.size * (1f - t * 0.5f), center = Offset(x, y))
        }
    }
}

private class FlameParticle(val dx: Float, val rise: Float, val size: Float, val start: Float, val life: Float) {
    companion object {
        fun random(r: Random) = FlameParticle(
            dx = r.nextFloat() - 0.5f,
            rise = 0.8f + r.nextFloat() * 1.2f,
            size = 0.06f + r.nextFloat() * 0.1f,
            start = r.nextFloat() * 0.55f,
            life = 0.3f + r.nextFloat() * 0.35f,
        )
    }
}

private const val BURN_MS = 1_100
