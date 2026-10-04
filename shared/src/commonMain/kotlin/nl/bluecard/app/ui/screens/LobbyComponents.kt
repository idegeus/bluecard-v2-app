package nl.bluecard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.painterResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.FeltChip
import nl.bluecard.app.ui.components.FeltChips
import nl.bluecard.app.ui.components.FeltSwitchRow
import nl.bluecard.app.ui.components.PlayerAvatar
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit
import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.engine.zweedspesten.ZpPreset
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SeatKind

/** A few fanned cards with a title, like the logo on the start screen. */
@Composable
fun FeltHeader(title: String, subtitle: String?, cards: List<Card>, cardWidth: Dp = 46.dp) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(cardWidth * 1.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val mid = (cards.size - 1) / 2f
            cards.forEachIndexed { i, card ->
                val shift = i - mid
                PlayingCard(
                    card,
                    cardWidth,
                    Modifier
                        .offset(x = cardWidth * 0.55f * shift, y = cardWidth * 0.08f * shift * shift)
                        .rotate(12f * shift),
                )
            }
        }
        Text(
            title,
            color = TableColors.OnFelt,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(subtitle, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

/** One player at the table: avatar, name, status and (for the host) a remove button. */
@Composable
fun LobbySeatRow(
    seat: SeatInfo,
    isYou: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val res = LocalResources.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isYou) TableColors.FeltLight.copy(alpha = 0.55f) else TableColors.Ink.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (seat.kind == SeatKind.BOT) {
                PlayerAvatar(seat.name, isBot = true) {
                    Icon(painterResource(R.drawable.ic_smart_toy), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            } else {
                PlayerAvatar(seat.name, avatar = seat.avatar)
            }
            when {
                seat.kind == SeatKind.HOST -> Badge(TableColors.TurnGlow) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = TableColors.CardBlack, modifier = Modifier.size(12.dp))
                }
                seat.kind == SeatKind.REMOTE && !seat.connected -> Badge(TableColors.Danger) {
                    Icon(painterResource(R.drawable.ic_link_off), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
                seat.kind == SeatKind.REMOTE -> Badge(TableColors.FeltLight) {
                    Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isYou) stringResource(R.string.you_suffix, seat.name) else seat.name,
                color = TableColors.OnFelt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when {
                seat.kind == SeatKind.HOST -> stringResource(R.string.seat_host)
                seat.kind == SeatKind.BOT -> stringResource(R.string.seat_bot, GameTexts.difficulty(res, seat.difficulty ?: BotDifficulty.NORMAL))
                !seat.connected && seat.botControlled ->
                    stringResource(R.string.seat_disconnected) + " · " + stringResource(R.string.seat_bot_takeover)
                !seat.connected -> stringResource(R.string.seat_disconnected)
                seat.botControlled -> stringResource(R.string.seat_bot_takeover)
                else -> stringResource(R.string.seat_remote)
            }
            Text(
                status,
                color = if (seat.kind == SeatKind.REMOTE && !seat.connected) TableColors.Danger else TableColors.OnFeltMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // The host sets the order of play.
        if (onMoveUp != null || onMoveDown != null) {
            Column {
                IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.lobby_move_up), tint = arrowTint(onMoveUp != null))
                }
                IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.lobby_move_down), tint = arrowTint(onMoveDown != null))
                }
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.host_remove), tint = TableColors.OnFeltMuted)
            }
        }
    }
}

@Composable
private fun Badge(color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .offset(x = 28.dp, y = 28.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.5.dp, TableColors.FeltDark, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The house rules at a glance: the special cards as real mini cards, plus the most important options. */
@Composable
fun RulesTableSummary(rules: ZpHouseRules, modifier: Modifier = Modifier) {
    val res = LocalResources.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            GameTexts.preset(res, ZpPreset.matching(rules)),
            color = TableColors.OnFelt,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        val specials = rules.specialRanks()
        if (specials.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                specials.forEachIndexed { i, (rank, effect) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                        PlayingCard(Card(rank, if (i % 2 == 0) Suit.HEARTS else Suit.SPADES), 40.dp)
                        Text(
                            GameTexts.effectShort(res, effect),
                            color = TableColors.OnFeltMuted,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        FeltChips {
            FeltChip(stringResource(R.string.hr_hand_size, rules.handSize))
            if (rules.allowMultiple) FeltChip(stringResource(R.string.lobby_chip_multiple))
            if (rules.playAfterPickUp) FeltChip(stringResource(R.string.hr_play_after_pickup))
            if (rules.drawGamble) FeltChip(stringResource(R.string.hr_gamble))
            if (rules.winnerSwap) FeltChip(stringResource(R.string.hr_winner_swap))
            if (!rules.enforceRules && rules.escalatingPenalty) FeltChip(stringResource(R.string.hr_escalating))
            FeltChip(
                stringResource(if (rules.enforceRules) R.string.rules_enforced else R.string.rules_not_enforced),
                accent = !rules.enforceRules,
            )
        }
    }
}

/** "Spelregels afdwingen" in the felt style. */
@Composable
fun FeltEnforceSwitch(enforced: Boolean, onChange: (Boolean) -> Unit) {
    FeltSwitchRow(
        stringResource(R.string.hr_enforce),
        enforced,
        onChange,
        detail = stringResource(R.string.hr_enforce_detail),
    )
}

private fun arrowTint(enabled: Boolean) = if (enabled) TableColors.OnFelt else TableColors.OnFelt.copy(alpha = 0.25f)

/** Who is watching (joined during a game or at a full table); they get a seat in the next round. */
@Composable
fun SpectatorsLine(spectators: List<nl.bluecard.multiplayer.session.SeatInfo>) {
    val (tables, watchers) = spectators.partition { it.table }
    if (tables.isNotEmpty()) {
        Text(
            stringResource(R.string.lobby_table_display, tables.joinToString(", ") { it.name }),
            color = TableColors.Highlight,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (watchers.isEmpty()) return
    Text(
        stringResource(R.string.lobby_spectators, watchers.joinToString(", ") { it.name }),
        color = TableColors.OnFeltMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}
