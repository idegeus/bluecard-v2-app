package nl.bluecard.app.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.bluecard.app.R
import androidx.compose.ui.draw.clip
import nl.bluecard.app.ui.components.PlayerProfiles
import nl.bluecard.app.ui.components.SocialButton
import nl.bluecard.app.res.painterResource
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.components.FxAnchor
import nl.bluecard.app.ui.components.MiniFanBacks
import nl.bluecard.app.ui.components.PlayerAvatar
import nl.bluecard.app.ui.components.SlidingText
import nl.bluecard.app.ui.components.fxAnchor
import nl.bluecard.app.ui.components.popWhen
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SeatKind

/* Table pieces shared by the Presidenten and Hartenjagen screens. */

/** Leave (✕), the game name with an optional small line under it, and the history (ⓘ). */
@Composable
internal fun TableTopBar(
    title: String,
    subtitle: String?,
    isMyTurn: Boolean,
    onLeave: () -> Unit,
    onShowLog: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(isMyTurn) {
        if (isMyTurn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onLeave) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.game_leave), tint = TableColors.OnFelt)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TableColors.OnFelt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, color = TableColors.Highlight, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        trailing?.invoke()
        SocialButton()
        IconButton(onClick = onShowLog) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.game_info), tint = TableColors.OnFelt)
        }
    }
}

/**
 * The other players: as many per row as fit (each at least [minWidth] wide), continuing on a second row at big tables,
 * so everybody is always in view. [item] gets the index and the width each panel should take.
 */
@Composable
internal fun OpponentGrid(count: Int, minWidth: Dp = 92.dp, maxWidth: Dp = 150.dp, item: @Composable (Int, Dp) -> Unit) {
    if (count == 0) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 6.dp
        val perRow = ((this.maxWidth + gap) / (minWidth + gap)).toInt().coerceIn(1, count)
        val rows = (count + perRow - 1) / perRow
        // Spread evenly over the rows (5 players: 3 + 2, not 4 + 1).
        val columns = (count + rows - 1) / rows
        val width = ((this.maxWidth - gap * (columns - 1)) / columns).coerceAtMost(maxWidth)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    for (i in row * columns until minOf(count, (row + 1) * columns)) item(i, width)
                }
            }
        }
    }
}

/**
 * One opponent: avatar and name, their cards as a small fan, and a few short status lines ([lines], the first in
 * the highlight colour).
 */
@Composable
internal fun OpponentChip(
    playerId: String,
    name: String,
    seat: SeatInfo?,
    handCount: Int,
    isCurrent: Boolean,
    lines: List<String>,
    canTakeOver: Boolean,
    onTakeOver: () -> Unit,
    dimmed: Boolean = false,
    width: Dp = 120.dp,
) {
    val border = if (isCurrent) BorderStroke(2.dp, TableColors.TurnGlow) else BorderStroke(1.dp, TableColors.Ink.copy(alpha = 0.25f))
    Surface(
        color = animateColorAsState(if (isCurrent) TableColors.FeltLight else TableColors.FeltDark.copy(alpha = if (dimmed) 0.3f else 0.6f), label = "chip").value,
        contentColor = TableColors.OnFelt.copy(alpha = if (dimmed) 0.6f else 1f),
        shape = RoundedCornerShape(12.dp),
        border = border,
        modifier = Modifier
            .width(width)
            .fxAnchor(FxAnchor.player(playerId))
            // Tap a player for their stats from the leaderboard.
            .clip(RoundedCornerShape(12.dp))
            .clickable { PlayerProfiles.open(playerId) },
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bot = seat != null && (seat.kind == SeatKind.BOT || seat.botControlled)
                PlayerAvatar(name, size = 22.dp, isBot = bot, avatar = seat?.avatar)
                if (seat != null && seat.kind == SeatKind.REMOTE && !seat.connected) {
                    Icon(painterResource(R.drawable.ic_link_off), null, Modifier.padding(start = 2.dp).size(14.dp), tint = TableColors.Danger)
                }
                Text(
                    name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
            if (handCount > 0) MiniFanBacks(handCount, 22.dp)
            lines.forEachIndexed { i, line ->
                Text(
                    line,
                    color = if (i == 0) TableColors.Highlight else TableColors.OnFeltMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canTakeOver && seat != null && seat.kind == SeatKind.REMOTE && !seat.connected && !seat.botControlled) {
                Text(
                    stringResource(R.string.game_take_over),
                    color = TableColors.Highlight,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable(onClick = onTakeOver).padding(2.dp),
                )
            }
        }
    }
}

/** "Your turn" / "Waiting for …" / what to do now, as a pill above the hand. */
@Composable
internal fun TurnPill(text: String, active: Boolean) {
    if (text.isEmpty()) return
    Surface(
        color = if (active) TableColors.TurnGlow else Color.Black.copy(alpha = 0.25f),
        contentColor = if (active) TableColors.CardBlack else TableColors.OnFelt,
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).popWhen(active),
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 5.dp, horizontal = 16.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
        )
    }
}

/** The latest event, tappable for the full history. */
@Composable
internal fun EventLine(text: String?, onShowLog: () -> Unit) {
    if (text == null) return
    Surface(
        color = Color.Black.copy(alpha = 0.25f),
        contentColor = TableColors.OnFelt,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onShowLog),
    ) {
        SlidingText(text) { shown ->
            Text(
                shown,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A small rounded label (requirement, round, …). */
@Composable
internal fun TableChip(text: String, urgent: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        color = animateColorAsState(if (urgent) TableColors.Danger else Color.Black.copy(alpha = 0.3f), label = "chip").value,
        contentColor = if (urgent) Color.White else TableColors.OnFelt,
        shape = RoundedCornerShape(50),
        modifier = modifier.widthIn(max = 170.dp).popWhen(urgent),
    ) {
        SlidingText(text) { shown ->
            Text(
                shown,
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
