package nl.bluecard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.KeepScreenOn
import nl.bluecard.app.ui.components.LobbyChatPanel
import nl.bluecard.app.platform.NearbyTable
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.ui.components.BannerTone
import nl.bluecard.app.ui.components.EmptySeat
import nl.bluecard.app.ui.components.FeltBanner
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.components.FeltSecondaryButton
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.components.platformUi
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.LostReason
import nl.bluecard.multiplayer.session.SessionPhase

/*
 * Playing together with phones nearby on platforms whose transport announces tables itself (iOS: Multipeer
 * Connectivity, over Bluetooth and Wi-Fi). Android has its own Bluetooth screens with permissions and pairing.
 */

/** Join a table nearby, as a player or as the table display (opening one starts from "Spel starten"). */
@Composable
fun NearbyPlayScreen(onBack: () -> Unit, onJoin: () -> Unit) {
    val container = appContainer()
    val settings by container.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    FeltScreen(title = stringResource(R.string.bt_title), onBack = onBack) {
        FeltHeader(
            title = stringResource(R.string.bt_header),
            subtitle = stringResource(R.string.nearby_intro),
            cards = listOf(Card.of("QS"), Card.of("AH"), Card.of("JD")),
        )
        if (container.platform.transport == null) {
            FeltBanner(stringResource(R.string.nearby_unavailable), tone = BannerTone.ERROR)
            return@FeltScreen
        }
        // Joining first, without scrolling; a new table (game + create) below.
        NearbyRoleCard(stringResource(R.string.bt_join), stringResource(R.string.nearby_join_detail), Icons.Filled.Search, primary = true) {
            container.sessions.joinAsTable.value = false
            onJoin()
        }
        TableDisplayCard {
            container.sessions.joinAsTable.value = true
            onJoin()
        }
        FeltPanel(title = stringResource(R.string.settings_player)) {
            PlayerNameField(settings.playerName, label = stringResource(R.string.bt_your_name), onFelt = true) { name ->
                scope.launch { container.settingsRepository.update { it.copy(playerName = name) } }
            }
        }
    }
}

@Composable
private fun NearbyRoleCard(title: String, detail: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) TableColors.FeltLight.copy(alpha = 0.75f) else TableColors.FeltDark.copy(alpha = 0.55f))
            .border(1.dp, if (primary) TableColors.TurnGlow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (primary) TableColors.TurnGlow else Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (primary) TableColors.CardBlack else TableColors.OnFelt, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TableColors.OnFelt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(detail, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Text("›", color = TableColors.OnFelt, fontSize = 30.sp, fontWeight = FontWeight.Light)
    }
}

/** Find a table nearby, join it and wait in its lobby until the host starts. */
@Composable
fun NearbyJoinScreen(onBack: () -> Unit, onGameStarted: () -> Unit) {
    val container = appContainer()
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val active by container.sessions.active.collectAsStateWithLifecycle()
    val joined = active as? ActiveSession.Joined
    val discovery = container.platform.transport?.discovery
    val tables by remember(discovery, joined == null) {
        if (discovery != null && joined == null) discovery.tables() else flowOf(emptyList())
    }.collectAsState(emptyList())
    var connectingTo by remember { mutableStateOf<String?>(null) }
    var joinError by remember { mutableStateOf<ConnectionStatus?>(null) }
    val rejoining by container.sessions.rejoining.collectAsStateWithLifecycle()

    val lobby by remember(joined) { joined?.client?.lobby ?: flowOf(null) }.collectAsState(null)
    val connection by remember(joined) { joined?.client?.connection ?: flowOf(null) }.collectAsState(null)
    LaunchedEffect(joined, lobby?.phase) {
        if (joined != null && lobby?.phase == SessionPhase.IN_GAME) onGameStarted()
    }
    LaunchedEffect(joined) {
        joined?.client?.notices?.collect { notice -> GameTexts.notice(res, notice)?.let { snackbar.showSnackbar(it) } }
    }

    val leave = {
        container.appScope.launch { if (container.sessions.active.value is ActiveSession.Joined) container.sessions.endSession() }
        onBack()
    }
    platformUi().BackHandler { leave() }

    fun join(table: NearbyTable) {
        if (connectingTo != null) return
        connectingTo = table.hostName
        joinError = null
        scope.launch {
            val game = GameKind.fromId(table.gameId) ?: GameKind.ZWEEDS_PESTEN
            var status = container.sessions.join(table.address, table.hostName, game)
            if (status is ConnectionStatus.Lost) status = container.sessions.join(table.address, table.hostName, game)
            connectingTo = null
            if (status != ConnectionStatus.Connected) joinError = status
        }
    }

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
            val myId by joined.client.playerId.collectAsStateWithLifecycle()
            FeltHeader(
                title = stringResource(R.string.join_connected, lobby?.hostName ?: joined.hostLabel),
                subtitle = GameTexts.gameName(res, joined.game),
                cards = headerCards(joined.game),
            )
            val status = connection
            val problem = status?.let { GameTexts.connection(res, it) }
            if (problem != null) {
                val lost = status is ConnectionStatus.Lost && status.reason == LostReason.CONNECTION_LOST
                FeltBanner(
                    problem,
                    tone = BannerTone.ERROR,
                    action = if (lost) {
                        { FeltTextAction(stringResource(R.string.conn_reconnect)) { scope.launch { container.sessions.reconnect() } } }
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
            lobby?.let { l ->
                FeltPanel(title = stringResource(R.string.lobby_table) + "  " + l.seats.size + "/" + l.maxPlayers) {
                    for (seat in l.seats) LobbySeatRow(seat, isYou = seat.id == myId, canRemove = false, onRemove = {})
                    repeat(l.maxPlayers - l.seats.size) { EmptySeat(stringResource(R.string.lobby_free_seat)) }
                    SpectatorsLine(l.spectators)
                    if (l.spectators.any { it.id == myId && it.table }) {
                        KeepScreenOn(true)
                        FeltBanner(stringResource(R.string.table_display_waiting), tone = BannerTone.SUCCESS)
                    } else if (l.spectators.any { it.id == myId }) {
                        FeltBanner(stringResource(R.string.join_watching_wait), tone = BannerTone.SUCCESS)
                    }
                }
                LobbyChatPanel(joined.client, myId)
                FeltPanel(title = stringResource(R.string.lobby_rules_title)) { LobbyRulesSummary(joined.game, l.config) }
            }
            return@FeltScreen
        }

        FeltHeader(
            title = stringResource(R.string.join_header),
            subtitle = stringResource(R.string.nearby_join_detail),
            cards = listOf(Card.of("5C"), Card.of("KH"), Card.of("9S")),
        )
        rejoining?.let { host ->
            FeltBanner(
                stringResource(R.string.join_rejoining, host),
                tone = BannerTone.SUCCESS,
                icon = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow) },
            )
        }
        connectingTo?.let { name ->
            FeltBanner(
                stringResource(R.string.join_connecting, name),
                tone = BannerTone.SUCCESS,
                icon = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow) },
            )
        }
        joinError?.let { status ->
            FeltBanner(
                when (status) {
                    is ConnectionStatus.Rejected -> GameTexts.joinRejected(res, status.reason)
                    else -> stringResource(R.string.join_failed)
                },
                tone = BannerTone.ERROR,
                action = { FeltTextAction(stringResource(R.string.ok)) { joinError = null } },
            )
        }
        FeltPanel(
            title = stringResource(R.string.join_found_games),
            trailing = { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow) },
        ) {
            if (tables.isEmpty()) {
                Text(stringResource(R.string.nearby_searching), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
            }
            for (table in tables) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable(enabled = connectingTo == null) { join(table) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.join_table_of, table.hostName),
                            color = TableColors.OnFelt,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        GameKind.fromId(table.gameId)?.let {
                            Text(GameTexts.gameName(res, it), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        stringResource(R.string.join_connect),
                        color = TableColors.CardBlack,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(TableColors.TurnGlow)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Host lobby panel on iOS: nothing to switch on, the table is announced while the lobby is open. */
@Composable
fun NearbyVisibilityPanel() {
    FeltPanel(title = stringResource(R.string.nearby_title)) {
        Text(stringResource(R.string.nearby_visible), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
