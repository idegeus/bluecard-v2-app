package nl.bluecard.app.ui.screens

import nl.bluecard.app.platform.nowMillis
import nl.bluecard.app.ui.components.platformUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.painterResource
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.LobbyChatPanel
import nl.bluecard.app.ui.components.BannerTone
import nl.bluecard.app.ui.components.EmptySeat
import nl.bluecard.app.ui.components.FeltBanner
import nl.bluecard.app.ui.components.FeltDivider
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltPrimaryButton
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.components.FeltSegmented
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.app.ui.components.FeltSecondaryButton
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import androidx.compose.material3.ButtonDefaults
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.session.GameKind
import nl.bluecard.engine.hartenjagen.HjHouseRules
import nl.bluecard.engine.pesten.PsHouseRules
import nl.bluecard.engine.presidenten.PrHouseRules
import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.multiplayer.protocol.ProtocolJson
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionPhase

@Composable
fun HostLobbyScreen(onLeave: () -> Unit, onEditRules: (GameKind) -> Unit, onGameStarted: () -> Unit, onLocalStarted: () -> Unit) {
    val container = appContainer()
    val vm: HostLobbyViewModel = viewModel { HostLobbyViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(ui.lobby?.phase) {
        if (ui.lobby?.phase == SessionPhase.IN_GAME) onGameStarted()
    }
    LaunchedEffect(ui.localStarted) {
        if (ui.localStarted) onLocalStarted()
    }
    LaunchedEffect(Unit) {
        vm.notices.collect { notice -> GameTexts.notice(res, notice)?.let { snackbar.showSnackbar(it) } }
    }
    LaunchedEffect(ui.startError) {
        ui.startError?.let {
            snackbar.showSnackbar(GameTexts.reject(res, it))
            vm.clearStartError()
        }
    }

    platformUi().BackHandler { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.host_leave_title)) },
            text = { Text(stringResource(R.string.host_leave_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.leave()
                    onLeave()
                }) { Text(stringResource(R.string.yes_leave)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    val hasSaved by container.sessions.hasSavedGame.collectAsStateWithLifecycle()
    val allowRadio = platformUi().rememberHostPreparer { vm.reopenServer() }
    val lobby = ui.lobby
    val enough = lobby != null && lobby.seats.size >= lobby.minPlayers
    FeltScreen(
        title = stringResource(R.string.host_title),
        onBack = { confirmLeave = true },
        snackbarHostState = snackbar,
        bottomBar = if (lobby == null) null else {
            {
                if (!enough) {
                    Text(
                        pluralStringResource(R.plurals.host_need_players, lobby.minPlayers, lobby.minPlayers),
                        color = TableColors.OnFeltMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (ui.botsOnly && hasSaved) {
                    Text(stringResource(R.string.bots_overwrite_warning), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
                }
                FeltPrimaryButton(stringResource(R.string.host_start), onClick = vm::startGame, enabled = enough && !ui.starting)
            }
        },
    ) {
        FeltHeader(
            title = GameTexts.gameName(res, ui.game),
            subtitle = stringResource(R.string.host_subtitle),
            cards = headerCards(ui.game),
        )
        if (ui.creating) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = TableColors.TurnGlow, trackColor = TableColors.FeltDark)
            return@FeltScreen
        }
        ui.serverError?.let { error ->
            FeltBanner(
                stringResource(R.string.host_server_failed, error),
                tone = BannerTone.ERROR,
                action = { FeltTextAction(stringResource(R.string.retry), vm::reopenServer) },
            )
        }
        if (lobby != null) {
            // The game of this table can still change; joined players follow automatically.
            GamePicker(ui.game) { game -> vm.switchGame(game) }
            if (ui.switching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TableColors.TurnGlow, trackColor = TableColors.FeltDark)
            FeltPanel(
                title = stringResource(R.string.lobby_table) + "  " + lobby.seats.size + "/" + lobby.maxPlayers,
                trailing = {
                    if (ui.accepting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.host_waiting), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.labelMedium)
                    }
                },
            ) {
                lobby.seats.forEachIndexed { index, seat ->
                    LobbySeatRow(
                        seat,
                        isYou = seat.kind == SeatKind.HOST,
                        canRemove = seat.kind != SeatKind.HOST,
                        onRemove = { vm.removeSeat(seat.id) },
                        onMoveUp = if (index > 0) ({ vm.moveSeat(seat.id, -1) }) else null,
                        onMoveDown = if (index < lobby.seats.lastIndex) ({ vm.moveSeat(seat.id, 1) }) else null,
                    )
                }
                val free = lobby.maxPlayers - lobby.seats.size
                if (free > 0) {
                    EmptySeat(
                        stringResource(R.string.host_add_bot),
                        onClick = vm::addBot,
                        leading = { Icon(Icons.Filled.Add, contentDescription = null, tint = TableColors.OnFelt) },
                    )
                    repeat(free - 1) { EmptySeat(stringResource(R.string.lobby_free_seat)) }
                }
                SpectatorsLine(lobby.spectators)
            }
            if (lobby.seats.any { it.kind == SeatKind.BOT }) {
                val settings by container.settings.collectAsStateWithLifecycle()
                FeltPanel(title = stringResource(R.string.bots_difficulty)) {
                    FeltSegmented(
                        options = BotDifficulty.entries.toList(),
                        selected = settings.defaultDifficulty,
                        label = { GameTexts.difficulty(res, it) },
                        onSelect = vm::setBotDifficulty,
                    )
                }
            }

            if (ui.accepting) {
                // Visibility for other phones (platform specific: Bluetooth discoverability on Android).
                platformUi().HostVisibilityPanel()
            } else if (ui.serverError == null) {
                // Offline table (Bluetooth off or not allowed): bots only, until the radio is back.
                FeltBanner(
                    stringResource(R.string.host_offline),
                    action = if (ui.radioReady) {
                        { FeltTextAction(stringResource(R.string.host_allow), allowRadio) }
                    } else {
                        null
                    },
                )
            }

            val session by container.sessions.active.collectAsStateWithLifecycle()
            session?.port?.let { port -> LobbyChatPanel(port, port.playerId.value) }

            FeltPanel(title = stringResource(R.string.lobby_rules_title) + " · " + GameTexts.gameName(res, ui.game)) {
                val settings by container.settings.collectAsStateWithLifecycle()
                if (ui.game.hasCheating) {
                    FeltEnforceSwitch(settings.enforceRulesFor(ui.game)) { enforce -> vm.setEnforceRules(enforce) }
                    FeltDivider()
                }
                // What the players received, decoded for the game of this table.
                LobbyRulesSummary(ui.game, lobby.config)
                FeltSecondaryButton(stringResource(R.string.bots_change_rules), onClick = { onEditRules(ui.game) })
            }
        }
    }
}

/** Small text button in the felt colours (inside banners). */
@Composable
fun FeltTextAction(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = TableColors.TurnGlow)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/** Current time, refreshed every few seconds (for countdowns). */
@Composable
fun produceNow(): androidx.compose.runtime.State<Long> {
    val state = remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.longValue = nowMillis()
            delay(5_000)
        }
    }
    return state
}
