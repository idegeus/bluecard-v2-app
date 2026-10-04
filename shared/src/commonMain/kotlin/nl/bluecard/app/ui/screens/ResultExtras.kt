package nl.bluecard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.Resources
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.core.PlayerStats
import nl.bluecard.engine.model.Card

/** One line in the summary after a game: who stood out, and how. */
data class GameFact(val emoji: String, val title: String, val detail: String)

/**
 * The fun facts of a finished game, from everybody's counters: the biggest cheater, the sharpest eye, who forgot to
 * call "Last card!", and so on. At most [max] facts, the most remarkable first.
 */
fun gameFacts(res: Resources, result: GameResult, max: Int = 6): List<GameFact> {
    val stats = result.stats
    if (stats.isEmpty()) return emptyList()
    val names = result.ranking.associate { it.playerId to it.name }
    fun top(value: (PlayerStats) -> Int): Pair<String, Int>? =
        stats.entries.map { it.key to value(it.value) }.filter { it.second > 0 && it.first in names }.maxByOrNull { it.second }
            ?.let { (id, n) -> names.getValue(id) to n }

    val facts = mutableListOf<GameFact>()
    fun add(emoji: String, title: Int, unit: Int, value: (PlayerStats) -> Int) {
        val (name, n) = top(value) ?: return
        facts += GameFact(emoji, res.getString(title), res.getString(R.string.fact_line, name, res.getQuantityString(unit, n, n)))
    }
    add("🥷", R.string.fact_ninja, R.plurals.fact_unit_unnoticed) { it.cheatsUnnoticed }
    add("🕵️", R.string.fact_cheater, R.plurals.fact_unit_caught) { it.cheatsCaught }
    add("👀", R.string.fact_sharp_eye, R.plurals.fact_unit_spotted) { it.cheatsSpotted }
    add("🙈", R.string.fact_false_alarm, R.plurals.fact_unit_false) { it.falseCalls }
    add("🤐", R.string.fact_forgetful, R.plurals.fact_unit_forgot) { it.lastCardForgotten }
    add("📣", R.string.fact_last_card, R.plurals.fact_unit_called) { it.lastCardCalls }
    add("🌙", R.string.fact_moon, R.plurals.fact_unit_moon) { it.moonShots }
    add("🔥", R.string.fact_burner, R.plurals.fact_unit_burns) { it.burns }
    add("🧺", R.string.fact_pile_eater, R.plurals.fact_unit_cards) { it.cardsFromPiles }
    add("🎲", R.string.fact_blind_luck, R.plurals.fact_unit_blind) { it.blindHits }
    add("🃏", R.string.fact_drawer, R.plurals.fact_unit_cards) { it.cardsDrawn }
    add("🏆", R.string.fact_tricks, R.plurals.fact_unit_tricks) { it.tricksWon }
    add("✋", R.string.fact_passer, R.plurals.fact_unit_passes) { it.passes }
    val played = stats.values.sumOf { it.cardsPlayed }
    if (played > 0) facts += GameFact("📊", res.getString(R.string.fact_total), res.getQuantityString(R.plurals.fact_total_cards, played, played))
    return facts.take(max)
}

/** The fun facts as a panel on the result screen. */
@Composable
fun GameFactsPanel(result: GameResult) {
    val res = LocalResources.current
    val facts = remember(result) { gameFacts(res, result) }
    if (facts.isEmpty()) return
    Text(stringResource(R.string.fact_heading), color = TableColors.OnFelt, style = MaterialTheme.typography.titleMedium)
    Surface(
        color = Color.Black.copy(alpha = 0.22f),
        contentColor = TableColors.OnFelt,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (fact in facts) FactRow(fact)
        }
    }
}

@Composable
private fun FactRow(fact: GameFact, compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fact.emoji, fontSize = if (compact) 18.sp else 22.sp, modifier = Modifier.width(if (compact) 30.dp else 36.dp))
        Column(Modifier.weight(1f)) {
            Text(fact.title, fontWeight = FontWeight.Bold, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium)
            Text(
                fact.detail,
                color = TableColors.OnFeltMuted,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ====================================================================== "I won with BlueCard"

/** The share button for the winner, with a preview of the picture that gets shared. */
@Composable
fun WinShareDialog(game: GameKind, result: GameResult, onDismiss: () -> Unit) {
    val container = appContainer()
    val res = LocalResources.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .aspectRatio(SHARE_ASPECT)
                    .drawWithContent {
                        // Record the card into a layer so it can be turned into a picture.
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) {
                WinShareCard(game, result)
            }
            Row(Modifier.widthIn(max = 340.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text(stringResource(R.string.cancel), maxLines = 1) }
                Button(
                    onClick = {
                        scope.launch {
                            val png = container.platform.encodePng(layer.toImageBitmap()) ?: return@launch
                            container.platform.shareImage(png, res.getString(R.string.share_text, GameTexts.gameName(res, game)))
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1.4f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
                ) { Text(stringResource(R.string.share_button), fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
    }
}

/** The picture itself (portrait 4:5, the Instagram feed format). */
@Composable
private fun WinShareCard(game: GameKind, result: GameResult) {
    val res = LocalResources.current
    val fact = remember(result) { gameFacts(res, result, max = 2) }
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(TableColors.FeltLight, TableColors.Felt, TableColors.FeltDark)))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("BlueCard", color = TableColors.OnFelt, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy((-22).dp)) {
            PlayingCard(Card.of("AH"), 70.dp, Modifier.rotate(-10f))
            PlayingCard(Card.of("AS"), 70.dp, Modifier.rotate(10f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.share_i_won),
                color = TableColors.Highlight,
                fontWeight = FontWeight.Black,
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
            )
            Text(GameTexts.gameName(res, game), color = TableColors.OnFelt, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (entry in result.ranking.take(4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${entry.position}.",
                        color = if (entry.position == 1) TableColors.Highlight else TableColors.OnFelt,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        entry.name + if (entry.position == 1) "  👑" else "",
                        color = TableColors.OnFelt,
                        fontWeight = if (entry.position == 1) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (fact.isNotEmpty()) Spacer(Modifier.size(4.dp))
            for (f in fact) FactRow(f, compact = true)
        }
        Text(stringResource(R.string.share_footer), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

private const val SHARE_ASPECT = 0.8f
