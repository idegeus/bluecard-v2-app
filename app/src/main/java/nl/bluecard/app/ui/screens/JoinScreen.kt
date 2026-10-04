package nl.bluecard.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.painterResource
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.LobbyChatPanel
import nl.bluecard.app.ui.components.BannerTone
import nl.bluecard.app.ui.components.EmptySeat
import nl.bluecard.app.ui.components.FeltBanner
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.components.CountdownBar
import nl.bluecard.app.ui.components.KeepScreenOn
import nl.bluecard.app.ui.components.FeltSecondaryButton
import nl.bluecard.app.ui.components.PlayerAvatar
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.multiplayer.session.LostReason
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.session.GameKind
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.SessionPhase

@Composable
fun JoinScreen(onLeave: () -> Unit, onGameStarted: () -> Unit) {
    val container = appContainer()
    val vm: JoinViewModel = viewModel { JoinViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.lobby?.phase, ui.joined) {
        if (ui.joined != null && ui.lobby?.phase == SessionPhase.IN_GAME) onGameStarted()
    }
    LaunchedEffect(Unit) {
        vm.notices.collect { notice -> GameTexts.notice(res, notice)?.let { snackbar.showSnackbar(it) } }
    }

    val leave = {
        vm.leave()
        onLeave()
    }
    BackHandler { leave() }

    val joined = ui.joined
    FeltScreen(
        title = stringResource(R.string.join_title),
        onBack = leave,
        snackbarHostState = snackbar,
        bottomBar = if (joined != null) {
            { FeltSecondaryButton(stringResource(R.string.join_leave), onClick = leave) }
        } else {
            null
        },
    ) {
        if (joined != null) {
            JoinedLobby(ui, onReconnect = vm::reconnect)
            return@FeltScreen
        }
        FeltHeader(
            title = stringResource(R.string.join_header),
            subtitle = stringResource(R.string.bt_join_detail),
            cards = listOf(Card.of("5C"), Card.of("KH"), Card.of("9S")),
        )

        val rejoining by container.sessions.rejoining.collectAsStateWithLifecycle()
        rejoining?.let { host ->
            FeltBanner(
                stringResource(R.string.join_rejoining, host),
                tone = BannerTone.SUCCESS,
                icon = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow) },
            )
        }
        ui.connectingTo?.let { name ->
            FeltBanner(
                stringResource(R.string.join_connecting, name),
                tone = BannerTone.SUCCESS,
                icon = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow) },
            )
        }
        ui.joinError?.let { status ->
            FeltBanner(
                when (status) {
                    is ConnectionStatus.Rejected -> GameTexts.joinRejected(res, status.reason)
                    else -> stringResource(R.string.join_failed)
                },
                tone = BannerTone.ERROR,
                action = { FeltTextAction(stringResource(R.string.ok), vm::clearError) },
            )
        }

        FeltPanel(
            title = stringResource(R.string.join_found_games),
            trailing = {
                if (ui.scanPhase == ScanPhase.SCANNING || ui.scanPhase == ScanPhase.PROBING) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow)
                }
            },
        ) {
            // What the search is doing, and how long it keeps going.
            if (ui.searchEndsAt > 0L && ui.connectingTo == null && (ui.scanPhase != ScanPhase.DONE || ui.games.isEmpty())) {
                val label = when (ui.scanPhase) {
                    ScanPhase.SCANNING -> stringResource(R.string.join_scanning)
                    ScanPhase.PROBING -> stringResource(R.string.join_probing)
                    else -> stringResource(R.string.join_watching_short)
                }
                CountdownBar(label, ui.searchStartedAt, ui.searchEndsAt)
            }
            if (ui.scanPhase == ScanPhase.FAILED) FeltBanner(stringResource(R.string.join_scan_failed), tone = BannerTone.ERROR)
            if (ui.games.isEmpty() && ui.scanPhase == ScanPhase.DONE && !ui.watching) {
                Text(
                    stringResource(R.string.join_no_games),
                    color = TableColors.OnFeltMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            for (game in ui.games) {
                val info = game.info ?: continue
                FoundGameRow(
                    hostName = info.hostName,
                    line = stringResource(R.string.join_table_of, info.hostName),
                    detail = (GameKind.fromId(info.gameId)?.let { GameTexts.gameName(res, it) } ?: info.gameName) + " · " +
                        pluralStringResource(R.plurals.join_players_line, info.playerCount, info.playerCount, info.maxPlayers) +
                        " · " + stringResource(if (info.phase == SessionPhase.LOBBY) R.string.join_phase_lobby else R.string.join_phase_watch),
                    enabled = ui.connectingTo == null,
                    onClick = { vm.connect(game) },
                )
            }
            if (ui.scanPhase == ScanPhase.IDLE || ui.scanPhase == ScanPhase.DONE || ui.scanPhase == ScanPhase.FAILED) {
                FeltSecondaryButton(stringResource(R.string.join_scan), onClick = vm::scan, icon = Icons.Filled.Refresh)
            }
        }
    }
}

/** A game found nearby: host avatar, game line and a yellow "join" pill. */
@Composable
private fun FoundGameRow(hostName: String, line: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TableColors.FeltLight.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(hostName)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(line, color = TableColors.OnFelt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            stringResource(R.string.join_connect),
            color = TableColors.CardBlack,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (enabled) TableColors.TurnGlow else TableColors.OnFeltMuted)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun JoinedLobby(ui: JoinUiState, onReconnect: () -> Unit) {
    val res = LocalResources.current
    val joined = ui.joined ?: return
    val lobby = ui.lobby
    val myId by joined.client.playerId.collectAsStateWithLifecycle()
    FeltHeader(
        title = stringResource(R.string.join_connected, lobby?.hostName ?: joined.hostLabel),
        subtitle = GameTexts.gameName(res, joined.game),
        cards = headerCards(joined.game),
    )
    val connectionText = ui.connection?.let { GameTexts.connection(res, it) }
    if (connectionText != null) {
        val status = ui.connection
        val lost = status is ConnectionStatus.Lost && status.reason == LostReason.CONNECTION_LOST
        FeltBanner(
            connectionText,
            tone = BannerTone.ERROR,
            action = if (lost) {
                { FeltTextAction(stringResource(R.string.conn_reconnect), onReconnect) }
            } else {
                null
            },
        )
    } else {
        FeltBanner(
            stringResource(R.string.join_waiting_host),
            tone = BannerTone.SUCCESS,
            icon = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow) },
        )
    }
    if (lobby != null) {
        FeltPanel(title = stringResource(R.string.lobby_table) + "  " + lobby.seats.size + "/" + lobby.maxPlayers) {
            for (seat in lobby.seats) LobbySeatRow(seat, isYou = seat.id == myId, canRemove = false, onRemove = {})
            repeat(lobby.maxPlayers - lobby.seats.size) { EmptySeat(stringResource(R.string.lobby_free_seat)) }
            SpectatorsLine(lobby.spectators)
            if (lobby.spectators.any { it.id == myId && it.table }) {
                KeepScreenOn(true)
                FeltBanner(stringResource(R.string.table_display_waiting), tone = BannerTone.SUCCESS)
            } else if (lobby.spectators.any { it.id == myId }) {
                FeltBanner(stringResource(R.string.join_watching_wait), tone = BannerTone.SUCCESS)
            }
        }
        // The host's rules, decoded for the game of this table.
        LobbyChatPanel(joined.client, myId)
        FeltPanel(title = stringResource(R.string.lobby_rules_title)) { LobbyRulesSummary(joined.game, lobby.config) }
    }
}
