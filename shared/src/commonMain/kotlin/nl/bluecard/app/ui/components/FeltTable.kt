package nl.bluecard.app.ui.components

import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.app.ui.theme.TableSkin
import nl.bluecard.app.ui.theme.TableLook
import nl.bluecard.app.ui.theme.CardBackSkin
import nl.bluecard.app.ui.theme.Skins
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.composed
import nl.bluecard.engine.model.Card
import kotlin.math.abs
import kotlin.random.Random

/** A tileable grain of wool fibres, made once: light and dark specks plus short hairs. */
private object FeltGrain {
    val bitmap: ImageBitmap by lazy {
        val size = 192
        val bmp = ImageBitmap(size, size)
        val canvas = Canvas(bmp)
        val r = Random(7)
        val speck = Paint()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = r.nextInt(100)
                val color = when {
                    v < 7 -> Color(255, 255, 255, r.nextInt(10, 24))
                    v < 16 -> Color(0, 0, 0, r.nextInt(10, 30))
                    else -> continue
                }
                speck.color = color
                canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, speck)
            }
        }
        val hair = Paint().apply {
            isAntiAlias = true
            strokeWidth = 0.8f
        }
        repeat(260) {
            val x = r.nextFloat() * size
            val y = r.nextFloat() * size
            val angle = r.nextFloat() * PI * 2
            val len = 2f + r.nextFloat() * 5f
            hair.color = if (r.nextBoolean()) Color(255, 255, 255, 22) else Color(0, 0, 0, 26)
            canvas.drawLine(Offset(x, y), Offset(x + (len * cos(angle)).toFloat(), y + (len * sin(angle)).toFloat()), hair)
        }
        bmp
    }
}

/**
 * A real-looking card table in the chosen (or given) table skin: woollen felt with a soft light in the middle and
 * darker edges; pure black for OLED, light for the light table, and a moving rainbow road in space.
 */
fun Modifier.feltTable(skin: TableSkin? = null): Modifier = composed {
    val table = skin ?: Skins.table
    if (table.look == TableLook.RAINBOW) {
        val phase by rememberInfiniteTransition(label = "road").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "roadPhase",
        )
        rainbowRoad(table) { phase }
    } else {
        feltCloth(table)
    }
}

private fun Modifier.feltCloth(table: TableSkin): Modifier = drawWithCache {
    val grain = ShaderBrush(ImageShader(FeltGrain.bitmap, TileMode.Repeated, TileMode.Repeated))
    val light = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
        center = Offset(size.width / 2f, size.height * 0.45f),
        radius = size.maxDimension * 0.6f,
    )
    val vignette = Brush.radialGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
        center = Offset(size.width / 2f, size.height * 0.45f),
        radius = size.maxDimension * 0.8f,
    )
    onDrawBehind {
        drawRect(table.felt)
        if (table.look == TableLook.OLED) return@onDrawBehind
        drawRect(light)
        drawRect(grain)
        drawRect(vignette)
    }
}

/** Deep space with stars and a rainbow road running into the distance, its stripes rolling towards you. */
private fun Modifier.rainbowRoad(table: TableSkin, phase: () -> Float): Modifier = drawWithCache {
    val stars = List(90) { i ->
        val r = Random(i * 7919)
        Triple(Offset(r.nextFloat() * size.width, r.nextFloat() * size.height), 0.6f + r.nextFloat() * 1.8f, r.nextFloat())
    }
    val sky = Brush.verticalGradient(listOf(table.feltDark, table.felt, table.feltLight))
    val horizon = size.height * 0.08f
    onDrawBehind {
        drawRect(sky)
        val t = phase()
        for ((at, radius, twinkle) in stars) {
            val a = 0.35f + 0.55f * kotlin.math.abs(sin((t + twinkle) * PI)).toFloat()
            drawCircle(Color.White.copy(alpha = a), radius, at)
        }
        // The road: a trapezoid from the bottom (wide) to the horizon (narrow), cut into rainbow lanes.
        val bottomHalf = size.width * 0.62f
        val topHalf = size.width * 0.06f
        val cx = size.width / 2f
        val lanes = RAINBOW.size
        for (lane in 0 until lanes) {
            val f0 = lane.toFloat() / lanes - 0.5f
            val f1 = (lane + 1).toFloat() / lanes - 0.5f
            val path = Path().apply {
                moveTo(cx + f0 * 2 * topHalf, horizon)
                lineTo(cx + f1 * 2 * topHalf, horizon)
                lineTo(cx + f1 * 2 * bottomHalf, size.height)
                lineTo(cx + f0 * 2 * bottomHalf, size.height)
                close()
            }
            drawPath(path, RAINBOW[lane].copy(alpha = 0.42f))
        }
        // Light bands rolling down the road, bunched up in the distance.
        for (k in 0 until 7) {
            val d = ((k + t) / 7f)
            val y = horizon + (size.height - horizon) * d * d
            val half = topHalf + (bottomHalf - topHalf) * d * d
            drawLine(Color.White.copy(alpha = 0.10f + 0.25f * d), Offset(cx - half, y), Offset(cx + half, y), 2f + 10f * d * d)
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), radius = size.maxDimension * 0.8f))
    }
}

/** Deterministic small "messiness" for a card at [index] on a pile, so the pile does not jump around. */
private fun jitter(seed: Int, range: Float): Float = (Random(seed).nextFloat() - 0.5f) * 2f * range

/**
 * The discard pile as a real, untidy heap: it gets visibly thicker the more cards lie on it (the cards underneath
 * show as edges below the top ones, which keep their place), with the last few cards on top lying crooked like they were tossed there.
 *
 * With [fanned] the cards under the top one shift to the left so their corners stay readable (to check whether the
 * last player cheated), and a small badge shows how many cards lie on the pile. Use [fannedWidth] for the room
 * that takes.
 *
 * @param top the visible top cards, bottom to top.
 */
@Composable
fun DiscardHeap(
    top: List<Card>,
    total: Int,
    width: Dp,
    modifier: Modifier = Modifier,
    highlightTop: Boolean = true,
    hovering: Boolean = false,
    contentDescription: String? = null,
    fanned: Boolean = false,
    extra: @Composable BoxScope.(lift: Dp) -> Unit = {},
) {
    val shape = RoundedCornerShape(width * 0.1f)
    val hidden = (total - top.size).coerceAtLeast(0)
    val layers = heapLayers(hidden)
    // The visible cards stay put; the thickness of the heap shows as edges below them.
    val lift = 0.dp
    val fanStep = if (fanned) width * FAN_STEP else 0.dp
    // The top card always lies on the same spot (right of the middle), whatever the number of cards: the older ones
    // fan out to its left, so the fan as a whole is centred when it is full.
    val shift = fanStep * (FAN_SLOTS - 1) / 2
    Box(
        modifier.then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // The cards underneath: only their edges and corners show.
        for (i in 0 until layers) {
            Box(
                Modifier
                    .offset(x = shift + jitter(i * 7 + 1, 3f).dp, y = HEAP_STEP * (layers - i))
                    .rotate(jitter(i * 13 + 5, 9f))
                    .size(width, width * CARD_ASPECT)
                    .shadow(1.dp, shape)
                    .clip(shape)
                    .background(TableColors.CardFace)
                    .border(1.dp, TableColors.CardBorder, shape),
            )
        }
        top.forEachIndexed { i, card ->
            val isTop = i == top.lastIndex
            val below = top.lastIndex - i
            val seed = abs(card.hashCode())
            val dx = when {
                fanned && isTop -> shift
                fanned -> shift - fanStep * below + jitter(seed, 2f).dp
                else -> jitter(seed, if (isTop) 4f else 9f).dp
            }
            val angle = when {
                fanned && isTop -> 0f
                fanned -> -4f * below + jitter(seed + 11, 3f)
                else -> jitter(seed + 11, if (isTop) 6f else 16f)
            }
            val dy = if (fanned && isTop) 0.dp else jitter(seed + 3, if (fanned) 2f else 4f).dp
            PlayingCard(
                card,
                width,
                Modifier
                    .offset(x = dx, y = -lift + dy)
                    .rotate(angle),
                highlighted = (isTop && highlightTop) || hovering,
            )
        }
        extra(lift)
        if (fanned && total > 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                modifier = Modifier.offset(x = shift + width / 2, y = -lift - width * CARD_ASPECT / 2),
            ) {
                Text(
                    total.toString(),
                    Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Extra width a fanned [DiscardHeap] of [visible] cards needs next to the card itself. */
fun fannedWidth(width: Dp, visible: Int): Dp = width * FAN_STEP * (visible - 1).coerceAtLeast(0)

/** Cards a fanned [DiscardHeap] shows at most (the top card and the two before it). */
private const val FAN_SLOTS = 3

private const val FAN_STEP = 0.3f

/** The draw pile: a block of cards that is thicker the more cards are left. */
@Composable
fun DrawStack(
    count: Int,
    width: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    skin: CardBackSkin = Skins.cardBack,
) {
    val layers = heapLayers(count - 1)
    val shape = RoundedCornerShape(width * 0.1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        for (i in 0 until layers) {
            Box(
                Modifier
                    .offset(x = (i * 0.25f).dp, y = -HEAP_STEP * i)
                    .size(width, width * CARD_ASPECT)
                    .clip(shape)
                    .background(skin.dark)
                    .border(0.8.dp, Color.White.copy(alpha = 0.55f), shape),
            )
        }
        CardBack(
            width,
            Modifier.offset(x = (layers * 0.25f).dp, y = -HEAP_STEP * layers),
            highlighted = highlighted,
            onClick = onClick,
            contentDescription = contentDescription,
            skin = skin,
        )
    }
}

/** One visible layer per two cards, up to a heap of 26 layers (52 cards). */
private fun heapLayers(cards: Int): Int = ((cards.coerceAtLeast(0) + 1) / 2).coerceAtMost(26)

private val HEAP_STEP = 1.1.dp
