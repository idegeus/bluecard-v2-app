package nl.bluecard.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.bluecard.app.AppContainer
import nl.bluecard.app.R
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.stats.Leaderboard
import nl.bluecard.app.stats.PlayerStats
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.multiplayer.session.MatchRecord
import nl.bluecard.multiplayer.session.SeatKind

/** Which player's card is open (tapped at the table); shown by the session overlay. */
object PlayerProfiles {
    var openId: String? by mutableStateOf(null)

    fun open(playerId: String) {
        openId = playerId
    }

    fun close() {
        openId = null
    }
}

/** How often [a] finished ahead of [b] and the other way round, in games they played together. */
fun headToHead(records: List<MatchRecord>, a: String, b: String): Pair<Int, Int> {
    var aAhead = 0
    var bAhead = 0
    for (record in records) {
        val pa = record.players.firstOrNull { it.deviceId == a && it.human } ?: continue
        val pb = record.players.firstOrNull { it.deviceId == b && it.human } ?: continue
        when {
            pa.position < pb.position -> aAhead++
            pb.position < pa.position -> bAhead++
        }
    }
    return aAhead to bAhead
}

/** A player's card at the table: their leaderboard stats overall and for this game, and how you do against them. */
@Composable
internal fun PlayerProfileDialog(container: AppContainer, session: ActiveSession, playerId: String, onDismiss: () -> Unit) {
    val res = LocalResources.current
    val lobby by session.port.lobby.collectAsStateWithLifecycle()
    val settings by container.settings.collectAsStateWithLifecycle()
    val records by container.matches.records.collectAsStateWithLifecycle()
    val seat = lobby?.seats?.firstOrNull { it.id == playerId } ?: lobby?.spectators?.firstOrNull { it.id == playerId } ?: return
    val myDevice = settings.deviceId
    val deviceId = seat.deviceId?.takeIf { seat.kind != SeatKind.BOT }
    val isMe = deviceId != null && deviceId == myDevice
    val gameName = GameTexts.gameName(res, session.game)

    fun statsFor(gameId: String?): PlayerStats? = when {
        deviceId == null -> null
        isMe -> Leaderboard.mine(records, deviceId, gameId)
        else -> Leaderboard.ranking(records, gameId).firstOrNull { it.deviceId == deviceId }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = TableColors.FeltDark, contentColor = TableColors.OnFelt, shape = RoundedCornerShape(20.dp)) {
            Column(
                Modifier.padding(18.dp).widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlayerAvatar(seat.name, size = 64.dp, isBot = seat.kind == SeatKind.BOT, avatar = seat.avatar)
                Text(
                    if (isMe) stringResource(R.string.you_suffix, seat.name) else seat.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (deviceId == null) {
                    Text(stringResource(R.string.profile_bot), color = TableColors.OnFeltMuted, textAlign = TextAlign.Center)
                } else {
                    val overall = statsFor(null)
                    val rank = Leaderboard.ranking(records).indexOfFirst { it.deviceId == deviceId }
                    if (rank >= 0) {
                        Text(stringResource(R.string.profile_rank, rank + 1), color = TableColors.Highlight, fontWeight = FontWeight.Bold)
                    }
                    if (overall == null || overall.played == 0) {
                        Text(stringResource(R.string.profile_none), color = TableColors.OnFeltMuted, textAlign = TextAlign.Center)
                    } else {
                        StatsBlock(stringResource(R.string.lb_all_games), overall)
                        statsFor(session.game.id)?.takeIf { it.played > 0 }?.let { StatsBlock(gameName, it) }
                        if (overall.hasCheatStats) {
                            Text(
                                stringResource(R.string.lb_calls_line, overall.rightCalls, overall.wrongCalls, overall.caught),
                                color = TableColors.OnFeltMuted,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (!isMe) {
                        val (theirs, mine) = headToHead(records, deviceId, myDevice)
                        if (theirs + mine > 0) {
                            Text(stringResource(R.string.profile_vs_you, mine, theirs), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TableColors.OnFelt),
                ) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}

@Composable
private fun StatsBlock(title: String, stats: PlayerStats) {
    Surface(color = Color.Black.copy(alpha = 0.25f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Number(stats.played.toString(), stringResource(R.string.lb_played))
                Number(stats.wins.toString(), stringResource(R.string.lb_wins), TableColors.Highlight)
                Number(stats.losses.toString(), stringResource(R.string.lb_losses))
                Number(stringResource(R.string.lb_rate_value, stats.winRate), stringResource(R.string.lb_rate))
            }
        }
    }
}

@Composable
private fun Number(value: String, label: String, color: Color = TableColors.OnFelt) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.labelSmall)
    }
}
