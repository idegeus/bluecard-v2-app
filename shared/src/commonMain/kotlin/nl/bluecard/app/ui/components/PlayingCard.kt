package nl.bluecard.app.ui.components

import kotlin.math.PI
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import nl.bluecard.app.ui.theme.Skins
import nl.bluecard.app.ui.theme.CardBackSkin
import nl.bluecard.app.ui.theme.BackPattern
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.bluecard.app.ui.text.CardLabels
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.model.Suit

const val CARD_ASPECT = 1.42f

/** Spoken description for accessibility, e.g. "harten vrouw" (in the app's language). */
fun cardSpeech(card: Card): String = CardLabels.speech(card)

/**
 * A face-up playing card drawn entirely in Compose. Sizes of text and symbols scale with [width],
 * independent of the system font size, so a card always looks like a card.
 */
@Composable
fun PlayingCard(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dimmed: Boolean = false,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val height = width * CARD_ASPECT
    val corner = width * 0.1f
    val color = suitColor(card.suit)
    val density = LocalDensity.current
    val rankSize = with(density) { (width * (if (card.rank == Rank.TEN) 0.24f else 0.27f)).toSp() }
    val shape = RoundedCornerShape(corner)
    val borderColor = when {
        selected -> TableColors.Selected
        highlighted -> TableColors.Highlight
        else -> TableColors.CardBorder
    }
    val borderWidth = if (selected || highlighted) (width * 0.045f).coerceAtLeast(2.dp) else 1.dp
    // A selected card slides up out of the hand.
    val lift by animateDpAsState(if (selected) -(width * 0.2f) else 0.dp, label = "lift")
    Box(
        modifier
            .offset { IntOffset(0, lift.roundToPx()) }
            .size(width, height)
            .shadow(if (selected) 6.dp else 2.dp, shape)
            .clip(shape)
            .background(TableColors.CardFace)
            .border(borderWidth, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = cardSpeech(card)
                this.selected = selected
            }
            .alpha(if (dimmed) 0.42f else 1f),
    ) {
        if (card.rank.isJoker) {
            JokerFace(color, width)
            return@Box
        }
        CornerIndex(card, color, rankSize, width, Modifier.align(Alignment.TopStart))
        CornerIndex(card, color, rankSize, width, Modifier.align(Alignment.BottomEnd).rotate(180f))
        if (card.rank.value in Rank.JACK.value..Rank.KING.value) {
            // Crown, tiara or cap above the letter, so the court cards are recognisable at a glance.
            val courtSize = with(density) { (width * 0.34f).toSp() }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CourtSymbol(card.rank, color, width * 0.44f)
                Text(
                    text = CardLabels.label(card.rank),
                    color = color,
                    fontSize = courtSize,
                    lineHeight = courtSize,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                SuitIcon(card.suit, width * 0.2f)
            }
        } else {
            SuitIcon(card.suit, width * (if (card.rank == Rank.ACE) 0.5f else 0.4f), Modifier.align(Alignment.Center))
        }
    }
}

/** A joker: a star in the corners and a jester's hat with "JOKER" in the middle. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.JokerFace(color: Color, width: Dp) {
    val density = LocalDensity.current
    val corner = with(density) { (width * 0.26f).toSp() }
    val label = with(density) { (width * 0.15f).toSp() }
    for ((alignment, angle) in listOf(Alignment.TopStart to 0f, Alignment.BottomEnd to 180f)) {
        Text(
            "★",
            color = color,
            fontSize = corner,
            lineHeight = corner,
            modifier = Modifier.align(alignment).rotate(angle).padding(horizontal = width * 0.06f, vertical = width * 0.03f),
        )
    }
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        CourtSymbol(Rank.JOKER, color, width * 0.62f)
        Text("JOKER", color = color, fontSize = label, lineHeight = label, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun CornerIndex(card: Card, color: Color, rankSize: androidx.compose.ui.unit.TextUnit, width: Dp, modifier: Modifier) {
    Column(
        modifier.padding(start = width * 0.05f, top = width * 0.03f, end = width * 0.05f, bottom = width * 0.03f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = CardLabels.label(card.rank),
            color = color,
            fontSize = rankSize,
            lineHeight = rankSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (card.rank == Rank.TEN) (-1).sp else 0.sp,
        )
        SuitIcon(card.suit, width * 0.18f)
    }
}

/** The back of a card in the chosen (or given) skin. Optionally shows a number (e.g. hand size or pile size). */
@Composable
fun CardBack(
    width: Dp,
    modifier: Modifier = Modifier,
    count: Int? = null,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    skin: CardBackSkin = Skins.cardBack,
) {
    val height = width * CARD_ASPECT
    val shape = RoundedCornerShape(width * 0.1f)
    val density = LocalDensity.current
    // The premium back shimmers; the others are still.
    val phase = if (skin.pattern == BackPattern.HOLO) {
        rememberInfiniteTransition(label = "holo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing), RepeatMode.Restart),
            label = "holoPhase",
        ).value
    } else {
        0f
    }
    Box(
        modifier
            .size(width, height)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(skin.base)
            .border(
                if (highlighted) (width * 0.05f).coerceAtLeast(2.dp) else 1.dp,
                if (highlighted) TableColors.Highlight else Color.White.copy(alpha = 0.7f),
                shape,
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(width * 0.08f)) { drawCardBack(skin, phase) }
        if (count != null) {
            val textSize = with(density) { (width * 0.34f).toSp() }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = width * 0.1f, vertical = width * 0.02f),
            ) {
                Text(count.toString(), color = Color.White, fontSize = textSize, lineHeight = textSize, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Dashed placeholder where a pile would be. */
@Composable
fun EmptyCardSlot(width: Dp, modifier: Modifier = Modifier, label: String? = null) {
    val shape = RoundedCornerShape(width * 0.1f)
    val density = LocalDensity.current
    Box(
        modifier
            .size(width, width * CARD_ASPECT)
            .border(1.5.dp, Color.White.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            val size = with(density) { (width * 0.17f).toSp() }
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = size, textAlign = TextAlign.Center)
        }
    }
}

private fun DrawScope.drawCardBack(skin: CardBackSkin, phase: Float) {
    val corner = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.08f)
    val line = size.minDimension * 0.02f
    val emblemSize = size.minDimension * 0.42f
    val emblemAt = Offset((size.width - emblemSize) / 2f, (size.height - emblemSize) / 2f)
    when (skin.pattern) {
        BackPattern.LATTICE -> {
            drawRoundRect(skin.dark, cornerRadius = corner)
            val step = size.minDimension / 5f
            var x = -size.height
            while (x < size.width) {
                drawLine(Color.White.copy(alpha = 0.16f), Offset(x, 0f), Offset(x + size.height, size.height), line)
                drawLine(Color.White.copy(alpha = 0.16f), Offset(x + size.height, 0f), Offset(x, size.height), line)
                x += step
            }
            drawSuit(Suit.SPADES, skin.emblem.copy(alpha = 0.85f), emblemAt, emblemSize)
        }
        BackPattern.DOTS -> {
            drawRoundRect(skin.dark, cornerRadius = corner)
            val step = size.minDimension / 6f
            var y = step / 2f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) step / 2f else step
                while (x < size.width) {
                    drawCircle(skin.emblem.copy(alpha = 0.22f), radius = step * 0.16f, center = Offset(x, y))
                    x += step
                }
                y += step * 0.86f
                row++
            }
            drawSuit(Suit.CLUBS, skin.emblem.copy(alpha = 0.9f), emblemAt, emblemSize)
        }
        BackPattern.STRIPES -> {
            drawRoundRect(skin.dark, cornerRadius = corner)
            val step = size.width / 9f
            var x = step / 2f
            while (x < size.width) {
                drawLine(skin.emblem.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), line * 0.8f)
                x += step
            }
            drawRoundRect(skin.emblem, cornerRadius = corner, style = Stroke(line * 1.4f))
            drawSuit(Suit.DIAMONDS, skin.emblem, emblemAt, emblemSize)
        }
        BackPattern.SUNBURST -> {
            val center = Offset(size.width / 2f, size.height / 2f)
            val rays = 18
            val radius = size.maxDimension
            for (i in 0 until rays) {
                if (i % 2 == 1) continue
                val a1 = ((i * 360.0 / rays) * PI / 180.0)
                val a2 = (((i + 1) * 360.0 / rays) * PI / 180.0)
                val path = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(center.x + (radius * kotlin.math.cos(a1)).toFloat(), center.y + (radius * kotlin.math.sin(a1)).toFloat())
                    lineTo(center.x + (radius * kotlin.math.cos(a2)).toFloat(), center.y + (radius * kotlin.math.sin(a2)).toFloat())
                    close()
                }
                drawPath(path, skin.dark.copy(alpha = 0.55f))
            }
            drawCircle(skin.emblem, radius = emblemSize * 0.62f, center = center)
            drawSuit(Suit.HEARTS, skin.base, emblemAt, emblemSize)
        }
        BackPattern.HOLO -> drawHolo(skin, phase, corner, emblemAt, emblemSize)
        BackPattern.RAINBOW -> {
            drawRoundRect(skin.dark, cornerRadius = corner)
            // Diagonal rainbow road with a star.
            val band = size.minDimension * 0.11f
            RAINBOW.forEachIndexed { i, color ->
                val o = (i - RAINBOW.size / 2f) * band
                drawLine(color, Offset(-size.width * 0.2f + o, size.height * 1.1f), Offset(size.width * 1.2f + o, -size.height * 0.1f), band * 1.02f)
            }
            drawCircle(skin.base, radius = emblemSize * 0.62f, center = Offset(size.width / 2f, size.height / 2f))
            drawStar(Offset(size.width / 2f, size.height / 2f), emblemSize * 0.5f, Color(0xFFFFEB3B))
        }
    }
}

/** The colours of the rainbow road, outside in. */
internal val RAINBOW = listOf(
    Color(0xFFFF4D4D), Color(0xFFFF9F1C), Color(0xFFFFE14D), Color(0xFF4DFF88), Color(0xFF4DB8FF), Color(0xFFA64DFF),
)

internal fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val a = -PI / 2 + i * PI / 5
        val p = Offset(center.x + (r * kotlin.math.cos(a)).toFloat(), center.y + (r * kotlin.math.sin(a)).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

/** The premium back: a rainbow foil that slowly turns, a moving shine and a glowing diamond. */
private fun DrawScope.drawHolo(skin: CardBackSkin, phase: Float, corner: androidx.compose.ui.geometry.CornerRadius, emblemAt: Offset, emblemSize: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val foil = listOf(
        Color(0xFFFF4FD8), Color(0xFF7C4DFF), Color(0xFF00E5FF), Color(0xFF69F0AE), Color(0xFFFFEB3B), Color(0xFFFF4FD8),
    )
    rotate(phase * 360f, center) {
        drawCircle(Brush.sweepGradient(foil, center), radius = size.maxDimension, center = center)
    }
    drawRoundRect(skin.dark.copy(alpha = 0.55f), cornerRadius = corner)
    // A band of light sweeping across, like tilting a foil card.
    val bandX = -size.width + phase * size.width * 3f
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
            start = Offset(bandX, 0f),
            end = Offset(bandX + size.width * 0.6f, size.height),
        ),
    )
    // Glowing diamond.
    val d = Path().apply {
        moveTo(center.x, emblemAt.y - emblemSize * 0.1f)
        lineTo(emblemAt.x + emblemSize * 1.05f, center.y)
        lineTo(center.x, emblemAt.y + emblemSize * 1.1f)
        lineTo(emblemAt.x - emblemSize * 0.05f, center.y)
        close()
    }
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center, emblemSize), radius = emblemSize, center = center)
    drawPath(d, Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFB3E5FC), Color(0xFFF8BBD0)), emblemAt, emblemAt + Offset(emblemSize, emblemSize)))
    drawPath(d, Color.White, style = Stroke(size.minDimension * 0.02f))
    // Sparkles that twinkle with the phase.
    val sparkles = listOf(0.18f to 0.16f, 0.82f to 0.22f, 0.24f to 0.82f, 0.78f to 0.86f, 0.5f to 0.08f)
    sparkles.forEachIndexed { i, (fx, fy) ->
        val alpha = ((kotlin.math.sin((phase * 2 * PI) + i * 1.3) + 1) / 2).toFloat()
        drawCircle(Color.White.copy(alpha = 0.3f + 0.7f * alpha), radius = size.minDimension * 0.025f * (0.6f + alpha), center = Offset(size.width * fx, size.height * fy))
    }
}
