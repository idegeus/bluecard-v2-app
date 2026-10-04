package nl.bluecard.app.ui.screens

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.FeltDivider
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.stats.Leaderboard
import nl.bluecard.app.stats.PlayerStats
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.components.PlayerAvatar
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card

/**
 * The ranking of everybody you played with over Bluetooth (phones exchange their results when they connect),
 * plus your own totals including games against bots.
 */
@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val container = appContainer()
    val records by container.matches.records.collectAsStateWithLifecycle()
    val settings by container.settings.collectAsStateWithLifecycle()
    val res = LocalResources.current
    // null = all games.
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val gameId = filter

    FeltScreen(title = stringResource(R.string.lb_title), onBack = onBack) {
        FeltHeader(
            title = stringResource(R.string.lb_header),
            subtitle = stringResource(R.string.lb_subtitle),
            cards = listOf(Card.of("AH"), Card.of("KS"), Card.of("QD")),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (id in listOf<String?>(null) + GameKind.entries.map { it.id }) {
                PresetPill(
                    GameKind.fromId(id)?.let { GameTexts.gameName(res, it) } ?: stringResource(R.string.lb_all_games),
                    selected = id == gameId,
                ) { filter = id }
            }
        }

        val mine = Leaderboard.mine(records, settings.deviceId, gameId)
        FeltPanel(title = stringResource(R.string.lb_you)) {
            Text(stringResource(R.string.lb_you_detail), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
            StatsRow(mine)
            FeltDivider()
            Text(stringResource(R.string.lb_cheat_calls), color = TableColors.OnFelt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(mine.rightCalls.toString(), stringResource(R.string.lb_calls_right), TableColors.Highlight)
                BigNumber(mine.wrongCalls.toString(), stringResource(R.string.lb_calls_wrong))
                BigNumber(mine.caught.toString(), stringResource(R.string.lb_caught), TableColors.Danger)
            }
        }

        val ranking = Leaderboard.ranking(records, gameId)
        FeltPanel(title = stringResource(R.string.lb_together)) {
            if (ranking.isEmpty()) {
                Text(stringResource(R.string.lb_empty), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                HeaderRow()
                ranking.forEachIndexed { index, stats ->
                    RankingRow(index + 1, stats, isMe = stats.deviceId == settings.deviceId)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(stats: PlayerStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BigNumber(stats.played.toString(), stringResource(R.string.lb_played))
        BigNumber(stats.wins.toString(), stringResource(R.string.lb_wins), TableColors.Highlight)
        BigNumber(stats.losses.toString(), stringResource(R.string.lb_losses))
        BigNumber(stringResource(R.string.lb_rate_value, stats.winRate), stringResource(R.string.lb_rate))
    }
}

@Composable
private fun BigNumber(value: String, label: String, color: Color = TableColors.OnFelt) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(label, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(76.dp))
        Spacer(Modifier.weight(1f))
        for (label in listOf(R.string.lb_wins, R.string.lb_played, R.string.lb_rate)) {
            Text(
                stringResource(label),
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.width(58.dp),
            )
        }
    }
}

@Composable
private fun RankingRow(place: Int, stats: PlayerStats, isMe: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isMe) TableColors.FeltLight.copy(alpha = 0.6f) else TableColors.Ink.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$place.",
            color = if (place == 1) TableColors.Highlight else TableColors.OnFelt,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            modifier = Modifier.width(30.dp),
        )
        PlayerAvatar(stats.name, size = 34.dp)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isMe) stringResource(R.string.you_suffix, stats.name) else stats.name,
                color = TableColors.OnFelt,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stats.hasCheatStats) {
                // Right and wrong "Vals!" calls, and how often caught.
                Text(
                    stringResource(R.string.lb_calls_line, stats.rightCalls, stats.wrongCalls, stats.caught),
                    color = TableColors.OnFeltMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        for (value in listOf(stats.wins.toString(), stats.played.toString(), stringResource(R.string.lb_rate_value, stats.winRate))) {
            Text(value, color = TableColors.OnFelt, textAlign = TextAlign.End, modifier = Modifier.width(58.dp))
        }
    }
}
