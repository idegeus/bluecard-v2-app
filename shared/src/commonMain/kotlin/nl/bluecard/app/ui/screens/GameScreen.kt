package nl.bluecard.app.ui.screens

import nl.bluecard.app.ui.components.ChallengePickerDialog
import nl.bluecard.app.ui.components.ChallengeOption
import nl.bluecard.app.ui.components.PlayerAvatar
import nl.bluecard.app.ui.components.GameLogSheet
import nl.bluecard.app.ui.components.CalloutOverlay
import nl.bluecard.app.ui.components.CalloutKind
import nl.bluecard.app.ui.components.Callout
import nl.bluecard.app.ui.components.feltTable
import nl.bluecard.app.ui.components.DrawStack
import nl.bluecard.app.ui.components.DiscardHeap
import nl.bluecard.app.ui.components.fannedWidth
import nl.bluecard.app.ui.components.popWhen
import nl.bluecard.app.ui.components.SlidingText
import androidx.compose.animation.animateColorAsState
import nl.bluecard.app.ui.components.rememberTableFx
import nl.bluecard.app.ui.components.fxAnchor
import nl.bluecard.app.ui.components.LocalTableFx
import nl.bluecard.app.ui.components.FxOverlay
import nl.bluecard.app.ui.components.FxAnchor
import androidx.compose.runtime.CompositionLocalProvider
import nl.bluecard.app.ui.components.platformUi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.TransformOrigin
import nl.bluecard.app.ui.components.CARD_ASPECT
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import nl.bluecard.app.ui.components.CardDragState
import nl.bluecard.app.ui.components.DragItem
import nl.bluecard.app.ui.components.DragOverlay
import nl.bluecard.app.ui.components.DraggableCard
import nl.bluecard.app.ui.components.DropHandler
import nl.bluecard.app.ui.components.DropTarget
import nl.bluecard.app.ui.components.dropTarget
import nl.bluecard.app.ui.components.rememberCardDragState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.painterResource
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.PlayerProfiles
import nl.bluecard.app.ui.text.FishTitles
import nl.bluecard.app.ui.components.SocialButton
import nl.bluecard.app.ui.components.CardBack
import nl.bluecard.app.ui.components.CardFan
import nl.bluecard.app.ui.components.KeepScreenOn
import nl.bluecard.app.ui.components.MiniFanBacks
import nl.bluecard.app.ui.components.TurnGlowBackground
import nl.bluecard.app.ui.components.BarAction
import nl.bluecard.app.ui.components.BarTone
import nl.bluecard.app.ui.components.TableActionBar
import nl.bluecard.app.ui.components.EmptyCardSlot
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.SystemBarIcons
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.text.CardLabels
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.zweedspesten.CardSource
import nl.bluecard.engine.zweedspesten.ZpEffect
import nl.bluecard.engine.zweedspesten.ZpEvent
import nl.bluecard.engine.zweedspesten.ZpPhase
import nl.bluecard.engine.zweedspesten.ZpPlayerView
import nl.bluecard.engine.zweedspesten.ZpPublicPlayer
import nl.bluecard.engine.zweedspesten.ZpRequirement
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.LostReason
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionPhase

/** Callbacks from the table to the view model. */
private class TableActions(
    val onHandCard: (Card) -> Unit,
    val onFaceUp: (Card) -> Unit,
    val onBlind: (Int) -> Unit,
    val onPlay: () -> Unit,
    val onPickUp: () -> Unit,
    val onGamble: () -> Unit,
    val onReady: () -> Unit,
    val onTakeOver: (String) -> Unit,
    val onShowLog: () -> Unit,
    val onLeave: () -> Unit,
    val onDrop: DropHandler,
    val companionsFor: (Card) -> List<Card>,
    val onChallenge: () -> Unit,
    val onCancelStaged: () -> Unit,
)

@Composable
fun GameScreen(onFinished: () -> Unit, onExitToMenu: () -> Unit, onBackToLobby: () -> Unit) {
    val container = appContainer()
    val vm: GameViewModel = viewModel { GameViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    // "Vals!" with several recent plays open: which one?
    var challengeChoices by remember { mutableStateOf<List<ChallengeOption>?>(null) }
    challengeChoices?.let { options ->
        ChallengePickerDialog(options, onPick = { id -> challengeChoices = null; vm.challenge(id) }, onDismiss = { challengeChoices = null })
    }
    val dragState = rememberCardDragState()
    val fx = rememberTableFx()
    val haptics = LocalHapticFeedback.current

    KeepScreenOn((ui.keepScreenOn || isTableDisplay()) && ui.hasSession)
    SystemBarIcons(darkBackground = true)

    LaunchedEffect(ui.view?.phase) {
        if (ui.view?.phase == ZpPhase.FINISHED) {
            // Let the last move play out (cards flying, a blind card turning over) before the summary.
            fx.awaitIdle()
            onFinished()
        }
    }
    LaunchedEffect(ui.kind, ui.lobbyPhase) {
        // The host went back to the lobby: clients follow.
        if (ui.kind == SessionKind.CLIENT && ui.lobbyPhase == SessionPhase.LOBBY) onBackToLobby()
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val text = when (message) {
                is GameMessage.Rejected -> GameTexts.reject(res, message.code)
                is GameMessage.Notice -> GameTexts.notice(res, message.notice)
                GameMessage.SelectHandCardFirst -> res.getString(R.string.game_swap_hint)
                GameMessage.OnlySameRank -> res.getString(R.string.game_only_same_rank)
                GameMessage.NotPlayable -> {
                    val requirement = vm.ui.value.view?.requirement
                    res.getString(R.string.err_CARD_TOO_LOW).takeIf { requirement?.maxValue == null }
                        ?: res.getString(R.string.err_CARD_TOO_HIGH)
                }
            }
            if (text != null) {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }
    }

    // A cheat caught or a false accusation is big news at the table: a stamp slams down.
    var callout by remember { mutableStateOf<Callout?>(null) }
    val lastEntry = ui.view?.log?.lastOrNull()
    // Only new events: not the last one of a game you just resumed.
    var seenSeq by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(lastEntry?.seq) {
        val entry = lastEntry ?: return@LaunchedEffect
        val first = seenSeq == null
        seenSeq = entry.seq
        if (first) return@LaunchedEffect
        val event = entry.event
        if (event is ZpEvent.CheatCaught || event is ZpEvent.FalseAccusation) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val view = ui.view ?: return@LaunchedEffect
            val you = GameTexts.selfName(res, ui.nameOf(view.viewerId))
            val text = GameTexts.event(res, event, view.viewerId) { id -> if (id == view.viewerId) you else ui.nameOf(id) }
            val shared = ui.kind != SessionKind.LOCAL
            callout = if (event is ZpEvent.CheatCaught) {
                Callout(
                    entry.seq, CalloutKind.CHEAT, res.getString(R.string.callout_cheat), text, event.cards,
                    mine = event.cheaterId == view.viewerId, sharedTable = shared,
                )
            } else {
                event as ZpEvent.FalseAccusation
                Callout(
                    entry.seq, CalloutKind.FALSE_ALARM, res.getString(R.string.callout_false_alarm), text,
                    mine = event.accuserId == view.viewerId, sharedTable = shared,
                )
            }
        }
    }

    platformUi().BackHandler(enabled = ui.hasSession) { confirmLeave = true }

    if (confirmLeave) {
        LeaveDialog(
            kind = ui.kind,
            watching = ui.view != null && ui.view?.me == null,
            onLeave = {
                confirmLeave = false
                vm.leave()
                onExitToMenu()
            },
            onResign = {
                confirmLeave = false
                vm.resign()
                if (ui.kind == SessionKind.CLIENT) onExitToMenu()
            },
            onDismiss = { confirmLeave = false },
        )
    }

    val view = ui.view
    if (showLog && view != null) {
        LogDialog(view, ui, onDismiss = { showLog = false })
    }

    CompositionLocalProvider(LocalTableFx provides fx) {
        Box(
            Modifier
                .fillMaxSize()
                .feltTable(),
        ) {
            view?.let { ZweedsTableEffects(it, fx) }
            when {
                !ui.hasSession -> NoSession(onExitToMenu)
                view == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TableColors.OnFelt)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.game_loading), color = TableColors.OnFelt)
                }
                else -> GameTable(
                    ui = ui,
                    view = view,
                    actions = TableActions(
                        onHandCard = vm::onHandCardTap,
                        onFaceUp = vm::onFaceUpTap,
                        onBlind = vm::onBlindTap,
                        onPlay = vm::play,
                        onPickUp = vm::pickUp,
                        onGamble = vm::gamble,
                        onReady = vm::ready,
                        onTakeOver = vm::takeOver,
                        onShowLog = { showLog = true },
                        onLeave = { confirmLeave = true },
                        onDrop = { item, target, swipedUp ->
                            vm.onCardDropped(item, target, swipedUp).also { played ->
                                if (played) {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    // The dragged card already flew to the pile; no second flight for it.
                                    when (item) {
                                        is DragItem.Face -> fx.markSelfPlayed(listOf(item.card) + item.companions)
                                        is DragItem.Blind -> fx.markSelfPlayedBlind()
                                    }
                                }
                            }
                        },
                        companionsFor = vm::companionsFor,
                        onChallenge = {
                            val view = ui.view
                            val open = view?.challenges.orEmpty().filter { it.playerId != view?.viewerId }
                            if (open.size > 1) {
                                challengeChoices = open.map { c -> ChallengeOption(c.id, view?.player(c.playerId)?.name ?: "?", c.cards) }
                            } else {
                                vm.challenge(open.singleOrNull()?.id)
                            }
                        },
                        onCancelStaged = vm::cancelStaged,
                    ),
                    dragState = dragState,
                    onReconnect = vm::reconnect,
                    onExitToMenu = {
                        vm.leave()
                        onExitToMenu()
                    },
                )
            }
            FxOverlay(fx)
            DragOverlay(dragState)
            CalloutOverlay(callout) { callout = null }
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 48.dp))
        }
    }
}

@Composable
fun NoSession(onExitToMenu: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.game_no_session), color = TableColors.OnFelt, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onExitToMenu) { Text(stringResource(R.string.game_to_menu)) }
    }
}

// ====================================================================== table layout

@Composable
private fun GameTable(
    ui: GameUiState,
    view: ZpPlayerView,
    actions: TableActions,
    dragState: CardDragState,
    onReconnect: () -> Unit,
    onExitToMenu: () -> Unit,
) {
    val tableDisplay = isTableDisplay()
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val landscape = maxWidth > maxHeight && maxWidth > 560.dp
        val opponents = view.players.filter { it.id != view.viewerId }
        if (tableDisplay) {
            TableDisplayLayout(
                top = {
                    TopBar(view, actions)
                    ConnectionBanner(ui.bluetoothOff, ui.connection, onReconnect, onExitToMenu)
                    OpponentsRow(opponents, ui, view, actions)
                },
                center = { CenterArea(view, ui, actions, dragState, compact = false) },
                bottom = {
                    EventLine(view, ui, actions.onShowLog)
                    TurnBanner(view, ui)
                },
            )
        } else if (landscape) {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TopBar(view, actions)
                    ConnectionBanner(ui.bluetoothOff, ui.connection, onReconnect, onExitToMenu)
                    OpponentsRow(opponents, ui, view, actions)
                    Spacer(Modifier.weight(1f))
                    CenterArea(view, ui, actions, dragState, compact = true)
                    EventLine(view, ui, actions.onShowLog)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    TurnBanner(view, ui)
                    PlayerZone(view, ui, actions, dragState, compact = true)
                    ActionBar(view, ui, actions)
                }
            }
        } else {
            val compact = maxHeight < 680.dp
            // Very small phones: smaller cards and no event line, so the hand and buttons stay on screen.
            val tiny = maxHeight < 600.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(if (tiny) 4.dp else 6.dp)) {
                TopBar(view, actions)
                ConnectionBanner(ui.bluetoothOff, ui.connection, onReconnect, onExitToMenu)
                OpponentsRow(opponents, ui, view, actions)
                Spacer(Modifier.weight(1f))
                CenterArea(view, ui, actions, dragState, compact = compact, tiny = tiny)
                if (!tiny) EventLine(view, ui, actions.onShowLog)
                Spacer(Modifier.weight(1f))
                // The swap hint next to "Klaar" says it all; on tiny screens the pill would push the button off.
                if (!(tiny && view.phase == ZpPhase.SWAPPING)) TurnBanner(view, ui)
                PlayerZone(view, ui, actions, dragState, compact = compact, tiny = tiny)
                ActionBar(view, ui, actions, tiny)
            }
        }
    }
}

@Composable
private fun TopBar(view: ZpPlayerView, actions: TableActions) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(view.isMyTurn) {
        if (view.isMyTurn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onLeave) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.game_leave), tint = TableColors.OnFelt)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.game_zweeds_pesten),
                color = TableColors.OnFelt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!view.rules.enforceRules) {
                // Everyone should know that cards are not checked: watch the pile!
                Text(
                    stringResource(R.string.rules_not_enforced),
                    color = TableColors.Highlight,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (view.rules.effects.values.any { it == ZpEffect.REVERSE }) {
            Text(
                stringResource(if (view.direction > 0) R.string.game_direction_cw else R.string.game_direction_ccw),
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        SocialButton()
        IconButton(onClick = actions.onShowLog) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.game_info), tint = TableColors.OnFelt)
        }
    }
}

/** Bluetooth off, reconnecting or connection lost — shared by all game screens. */
@Composable
internal fun ConnectionBanner(bluetoothOff: Boolean, connection: ConnectionStatus, onReconnect: () -> Unit, onExitToMenu: () -> Unit) {
    val res = LocalResources.current
    if (bluetoothOff) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.conn_bluetooth_off), Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
    // While the table moves to a new host, say so instead of "connection lost".
    val takingOver by appContainer().sessions.rejoining.collectAsStateWithLifecycle()
    val text = takingOver?.let { stringResource(R.string.join_taking_over, it) } ?: GameTexts.connection(res, connection) ?: return
    val lost = takingOver == null && (connection is ConnectionStatus.Lost || connection is ConnectionStatus.Rejected)
    Surface(
        color = if (lost) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (connection is ConnectionStatus.Reconnecting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            if (lost) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val status = connection
                    if (status is ConnectionStatus.Lost && status.reason == LostReason.CONNECTION_LOST) {
                        TextButton(onClick = onReconnect) { Text(stringResource(R.string.conn_reconnect)) }
                    }
                    TextButton(onClick = onExitToMenu) { Text(stringResource(R.string.game_to_menu)) }
                }
            }
        }
    }
}

// ====================================================================== opponents

@Composable
private fun OpponentsRow(opponents: List<ZpPublicPlayer>, ui: GameUiState, view: ZpPlayerView, actions: TableActions) {
    // Panels share the width; at big tables they continue on a second row.
    OpponentGrid(opponents.size, minWidth = 96.dp, maxWidth = 172.dp) { index, width ->
        val player = opponents[index]
        OpponentPanel(
            player = player,
            seat = ui.seat(player.id),
            isCurrent = view.currentPlayerId == player.id,
            swapping = view.phase == ZpPhase.SWAPPING,
            canTakeOver = ui.kind == SessionKind.HOST,
            onTakeOver = { actions.onTakeOver(player.id) },
            width = width,
            playerCount = view.players.size,
        )
    }
}

@Composable
private fun OpponentPanel(
    player: ZpPublicPlayer,
    seat: SeatInfo?,
    isCurrent: Boolean,
    swapping: Boolean,
    canTakeOver: Boolean,
    onTakeOver: () -> Unit,
    width: Dp,
    playerCount: Int,
) {
    val border = if (isCurrent) BorderStroke(2.dp, TableColors.TurnGlow) else BorderStroke(1.dp, TableColors.Ink.copy(alpha = 0.25f))
    Surface(
        color = animateColorAsState(if (isCurrent) TableColors.FeltLight else TableColors.FeltDark.copy(alpha = 0.6f), label = "panel").value,
        contentColor = TableColors.OnFelt,
        shape = RoundedCornerShape(12.dp),
        border = border,
        modifier = Modifier
            .width(width)
            .fxAnchor(FxAnchor.player(player.id))
            // Tap a player for their stats from the leaderboard.
            .clip(RoundedCornerShape(12.dp))
            .clickable { PlayerProfiles.open(player.id) },
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bot = seat != null && (seat.kind == SeatKind.BOT || seat.botControlled)
                if (bot) {
                    PlayerAvatar(player.name, size = 22.dp, isBot = true) {
                        Icon(painterResource(R.drawable.ic_smart_toy), null, Modifier.size(14.dp), tint = Color.White)
                    }
                } else {
                    PlayerAvatar(player.name, size = 22.dp, avatar = seat?.avatar)
                }
                if (seat != null && seat.kind == SeatKind.REMOTE && !seat.connected) {
                    Icon(painterResource(R.drawable.ic_link_off), null, Modifier.padding(start = 2.dp).size(14.dp), tint = TableColors.Danger)
                }
                Text(
                    player.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 2.dp).weight(1f, fill = false),
                )
                if (swapping && player.ready) {
                    Text("✓", color = TableColors.Highlight, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
                }
            }
            val position = player.finishedPosition
            if (position != null) {
                Text(
                    stringResource(R.string.game_finished_position, position),
                    color = TableColors.Highlight,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                )
                Text(
                    FishTitles.emoji(position, playerCount) + " " + FishTitles.title(LocalResources.current, position, playerCount),
                    color = TableColors.Highlight,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (width >= 160.dp) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (player.handCount > 0) MiniFanBacks(player.handCount, 24.dp)
                    MiniTable(player.faceUp, player.faceDownCount, 27.dp)
                }
            } else {
                // Narrow panel: hand above the table cards.
                if (player.handCount > 0) MiniFanBacks(player.handCount, 22.dp)
                MiniTable(player.faceUp, player.faceDownCount, ((width - 16.dp) / 3).coerceIn(18.dp, 27.dp))
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

/** Face-up cards lying on top of face-down cards, small. */
@Composable
private fun MiniTable(faceUp: List<Card>, faceDownCount: Int, width: Dp) {
    val slots = maxOf(faceUp.size, faceDownCount)
    if (slots == 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until slots) {
            Box {
                if (i < faceDownCount) CardBack(width)
                if (i < faceUp.size) {
                    PlayingCard(faceUp[i], width, Modifier.offset(y = if (i < faceDownCount) (-4).dp else 0.dp))
                }
            }
        }
    }
}

// ====================================================================== centre: piles

@Composable
private fun CenterArea(view: ZpPlayerView, ui: GameUiState, actions: TableActions, dragState: CardDragState, compact: Boolean, tiny: Boolean = false) {
    val pileWidth = if (tiny) 46.dp else if (compact) 56.dp else 66.dp
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Draw pile: its thickness shows how many cards are left (no labels: it is just a table).
        if (view.drawPileCount > 0) {
            DrawStack(
                view.drawPileCount,
                pileWidth,
                Modifier.fxAnchor(FxAnchor.DRAW),
                contentDescription = stringResource(R.string.game_draw_pile) + ": " + view.drawPileCount,
            )
        } else {
            EmptyCardSlot(pileWidth, Modifier.fxAnchor(FxAnchor.DRAW))
        }
        // Discard pile: a heap that grows with the number of cards on it.
        val canPull = view.isMyTurn && view.legal.canPickUp && !ui.busy
        DiscardPileView(view, pileWidth * 1.15f, dragState, canPull, actions.onPickUp, staged = ui.staged)
        // Requirement + burned
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RequirementChip(view)
            Text(
                stringResource(R.string.game_burned) + ": " + view.burnedCount,
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}


/**
 * The discard pile. It is the drop target for swiped cards, and can itself be pulled down towards the player
 * to pick it up — like taking the stack from the middle of the table.
 */
@Composable
private fun DiscardPileView(
    view: ZpPlayerView,
    width: Dp,
    dragState: CardDragState,
    canPull: Boolean,
    onPickUp: () -> Unit,
    staged: List<Card> = emptyList(),
) {
    // Cards still flying to the pile appear when they land.
    val incoming = LocalTableFx.current?.incoming(FxAnchor.PILE) ?: 0
    val visible = view.discardTop.dropLast(incoming).takeLast(3)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pullThreshold = with(density) { (width * 0.9f).toPx() }
    val maxPull = with(density) { (width * 1.6f).toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val hovering = dragState.hovered == DropTarget.Pile
    Box(
        Modifier
            .width(width + 28.dp + fannedWidth(width, 3))
            .height(width * 1.42f + 6.dp)
            .dropTarget(dragState, DropTarget.Pile, inflate = 28.dp)
            .fxAnchor(FxAnchor.PILE)
            .graphicsLayer {
                translationY = pull
                val grow = if (hovering) 1.08f else 1f
                scaleX = grow
                scaleY = grow
            }
            .pointerInput(canPull) {
                if (!canPull) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (pull > pullThreshold) onPickUp()
                        scope.launch { animate(pull, 0f, animationSpec = tween(200)) { value, _ -> pull = value } }
                    },
                    onDragCancel = { scope.launch { animate(pull, 0f) { value, _ -> pull = value } } },
                ) { change, dragAmount ->
                    change.consume()
                    pull = (pull + dragAmount).coerceIn(0f, maxPull)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (visible.isEmpty()) {
            EmptyCardSlot(width)
        } else {
            DiscardHeap(
                top = visible,
                total = (view.discardCount - incoming).coerceAtLeast(visible.size),
                width = width,
                highlightTop = staged.isEmpty(),
                hovering = hovering,
                contentDescription = pluralStringResource(R.plurals.game_pile_count, view.discardCount, view.discardCount),
                fanned = true,
            ) { lift ->
                // Swiped cards waiting for more of the same rank lie on top, glowing until they are played.
                staged.forEachIndexed { index, card ->
                    PlayingCard(
                        card,
                        width,
                        Modifier
                            .offset(x = (8 + index * 10).dp, y = -lift - (6 * (index + 1)).dp)
                            .rotate((index * 5 - 3).toFloat()),
                        highlighted = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun RequirementChip(view: ZpPlayerView) {
    val text = requirementText(view.requirement)
    Surface(color = Color.Black.copy(alpha = 0.3f), contentColor = TableColors.OnFelt, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun requirementText(requirement: ZpRequirement): String {
    val max = requirement.maxValue
    val min = requirement.minValue
    return when {
        max != null -> stringResource(R.string.game_requirement_max, rankLabel(max))
        min != null && min > Rank.TWO.value -> stringResource(R.string.game_requirement_min, rankLabel(min))
        else -> stringResource(R.string.game_requirement_any)
    }
}

private fun rankLabel(value: Int): String = Rank.fromValue(value)?.let { CardLabels.label(it) } ?: value.toString()

// ====================================================================== event line

@Composable
private fun EventLine(view: ZpPlayerView, ui: GameUiState, onShowLog: () -> Unit) {
    val res = LocalResources.current
    val last = view.log.lastOrNull() ?: return
    val youLabel = GameTexts.selfName(res, ui.nameOf(view.viewerId))
    val text = GameTexts.event(res, last.event, view.viewerId) { id -> if (id == view.viewerId) youLabel else ui.nameOf(id) }
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

@Composable
private fun LogDialog(view: ZpPlayerView, ui: GameUiState, onDismiss: () -> Unit) {
    val res = LocalResources.current
    val youLabel = GameTexts.selfName(res, ui.nameOf(view.viewerId))
    val nameOf = { id: String -> if (id == view.viewerId) youLabel else ui.nameOf(id) }
    val isBot = { id: String -> ui.seat(id)?.let { it.kind == SeatKind.BOT || it.botControlled } == true }
    GameLogSheet(
        title = stringResource(R.string.game_log),
        historyTab = stringResource(R.string.log_tab_history),
        specialsTab = stringResource(R.string.rules_title),
        items = view.log.map { zweedsLogItem(res, it, view.viewerId, nameOf, isBot) { id -> ui.seat(id)?.avatar } },
        onDismiss = onDismiss,
        rules = { ZweedsRules(view.rules) },
    ) {
        for ((rank, effect) in view.rules.specialRanks()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayingCard(Card(rank, nl.bluecard.engine.model.Suit.HEARTS), 40.dp)
                Text(GameTexts.effectLong(res, effect, view.rules), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ====================================================================== my side

@Composable
private fun TurnBanner(view: ZpPlayerView, ui: GameUiState) {
    val myPosition = view.me?.finishedPosition
    val current = view.currentPlayerId
    val exchange = view.exchange
    val (text, active) = when {
        view.me == null && !isTableDisplay() -> stringResource(R.string.spectator_banner) to false
        exchange != null && exchange.winnerId == view.viewerId -> stringResource(R.string.game_give_card, ui.nameOf(exchange.loserId)) to true
        exchange != null -> stringResource(R.string.game_exchanging, ui.nameOf(exchange.winnerId), ui.nameOf(exchange.loserId)) to false
        myPosition != null -> stringResource(
            R.string.game_you_finished_fish,
            FishTitles.emoji(myPosition, view.players.size) + " " + FishTitles.title(LocalResources.current, myPosition, view.players.size),
        ) to false
        view.phase == ZpPhase.SWAPPING -> stringResource(R.string.game_swap_phase) to (view.legal.canSwap || view.legal.canReady)
        view.isMyTurn -> stringResource(R.string.game_your_turn) to true
        current != null -> stringResource(R.string.game_waiting_for, ui.nameOf(current)) to false
        else -> "" to false
    }
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
        )
    }
}

/**
 * The player's side of the table, built like a real card player holds cards: the hand as a curved, overlapping
 * fan at the bottom, and the table cards (face-up on face-down) lying in front of the player, half hidden
 * behind the hand. They only come forward when they matter: in the swap phase and once the hand is empty.
 */
@Composable
private fun PlayerZone(view: ZpPlayerView, ui: GameUiState, actions: TableActions, dragState: CardDragState, compact: Boolean, tiny: Boolean = false) {
    val me = view.me ?: return
    val hand = view.myHand
    val handActive = hand.isNotEmpty()
    val swapping = view.phase == ZpPhase.SWAPPING
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val zoneWidth = maxWidth
        val bigHand = hand.size > 9
        val handWidth = (zoneWidth * (if (bigHand) 0.19f else 0.22f))
            .coerceIn(if (tiny) 48.dp else if (compact) 58.dp else 64.dp, if (tiny) 62.dp else 88.dp)
        val handHeight = handWidth * CARD_ASPECT
        // Table cards: small and tucked behind the hand while it matters, large and in front at the end.
        val tableWidth by animateDpAsState(if (handActive) handWidth * 0.72f else handWidth * 0.95f, label = "tableWidth")
        val tableHeight = tableWidth * CARD_ASPECT
        val peek = when {
            !handActive -> 0.dp
            swapping -> tableHeight + 10.dp // fully visible above the hand to swap
            else -> tableHeight * 0.42f // only the index corners peek out
        }
        val fanDrop = handWidth * 0.3f
        val lift = handWidth * 0.3f
        val zoneHeight by animateDpAsState(
            if (handActive) peek + handHeight + fanDrop + lift else tableHeight + lift + 12.dp,
            label = "zoneHeight",
        )
        val tableTop by animateDpAsState(if (handActive) lift * 0.5f else lift, label = "tableTop")

        Box(Modifier.fillMaxWidth().height(zoneHeight)) {
            if (view.isMyTurn) TurnGlowBackground(Modifier.matchParentSize())
            TableCards(
                me = me,
                view = view,
                ui = ui,
                actions = actions,
                dragState = dragState,
                cardWidth = tableWidth,
                modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, tableTop.roundToPx()) }.fxAnchor(FxAnchor.TABLE),
            )
            if (handActive) {
                FanHand(
                    hand = hand,
                    view = view,
                    ui = ui,
                    actions = actions,
                    dragState = dragState,
                    cardWidth = handWidth,
                    availableWidth = zoneWidth,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(handHeight + fanDrop + lift).fxAnchor(FxAnchor.HAND),
                )
            }
        }
    }
}

/** Face-up cards lying on the face-down cards in front of the player. */
@Composable
private fun TableCards(
    me: ZpPublicPlayer,
    view: ZpPlayerView,
    ui: GameUiState,
    actions: TableActions,
    dragState: CardDragState,
    cardWidth: Dp,
    modifier: Modifier,
) {
    val faceUp = me.faceUp
    val downCount = me.faceDownCount
    val slots = maxOf(faceUp.size, downCount)
    if (slots == 0) return
    val legal = view.legal
    val swapping = view.phase == ZpPhase.SWAPPING && legal.canSwap
    val playingFaceUp = view.isMyTurn && legal.source == CardSource.FACE_UP
    val playingBlind = view.isMyTurn && legal.source == CardSource.FACE_DOWN
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(cardWidth * 0.18f)) {
        for (i in 0 until slots) {
            Box(Modifier.height(cardWidth * CARD_ASPECT + 8.dp), contentAlignment = Alignment.BottomCenter) {
                if (i < downCount) {
                    val blindIndex = i
                    val canPlayBlind = playingBlind && faceUp.size <= i && !ui.busy
                    DraggableCard(dragState, DragItem.Blind(blindIndex), canPlayBlind, cardWidth, actions.onDrop) {
                        CardBack(
                            cardWidth,
                            highlighted = playingBlind,
                            onClick = if (canPlayBlind) ({ actions.onBlind(blindIndex) }) else null,
                            contentDescription = stringResource(R.string.game_blind_card, i + 1),
                        )
                    }
                }
                if (i < faceUp.size && faceUp[i] !in ui.hiddenCards) {
                    val card = faceUp[i]
                    val playable = playingFaceUp && card in legal.playableCards
                    val fits = playingFaceUp && card in legal.fittingCards
                    val swapTarget = swapping && dragState.item != null
                    DraggableCard(
                        dragState,
                        DragItem.Face(card, if (playingFaceUp) actions.companionsFor(card) else emptyList()),
                        enabled = playingFaceUp && !ui.busy,
                        width = cardWidth,
                        onDrop = actions.onDrop,
                        modifier = Modifier
                            .offset(y = if (i < downCount) (-8).dp else 0.dp)
                            .then(if (swapping) Modifier.dropTarget(dragState, DropTarget.FaceUp(card)) else Modifier),
                        hidden = card in ui.hiddenCards,
                    ) {
                        PlayingCard(
                            card,
                            cardWidth,
                            selected = card in ui.selected || card == ui.swapFaceUpCard || (swapTarget && dragState.hovered == DropTarget.FaceUp(card)),
                            highlighted = (swapping && (ui.swapHandCard != null || swapTarget)) || (fits && ui.showHints),
                            dimmed = playingFaceUp && !playable,
                            onClick = if (swapping || playingFaceUp) ({ actions.onFaceUp(card) }) else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FanHand(
    hand: List<Card>,
    view: ZpPlayerView,
    ui: GameUiState,
    actions: TableActions,
    dragState: CardDragState,
    cardWidth: Dp,
    availableWidth: Dp,
    modifier: Modifier,
) {
    val legal = view.legal
    val swapping = view.phase == ZpPhase.SWAPPING && legal.canSwap
    val playingHand = view.isMyTurn && legal.source == CardSource.HAND
    val giving = legal.giveCards.isNotEmpty()
    CardFan(
        cards = hand,
        hidden = ui.hiddenCards + (LocalTableFx.current?.incomingCards(FxAnchor.HAND) ?: emptySet()),
        isMyTurn = view.isMyTurn,
        anySelected = hand.any { it in ui.selected || it == ui.swapHandCard },
        dragState = dragState,
        onDrop = actions.onDrop,
        dragEnabled = (swapping || playingHand) && !ui.busy && !ui.botPlaysForMe,
        dragItem = { card -> DragItem.Face(card, if (playingHand) actions.companionsFor(card) else emptyList()) },
        cardWidth = cardWidth,
        availableWidth = availableWidth,
        modifier = modifier,
    ) { card ->
        PlayingCard(
            card,
            cardWidth,
            selected = card in ui.selected || card == ui.swapHandCard,
            highlighted = (playingHand && card in legal.fittingCards && ui.showHints) || (swapping && ui.swapFaceUpCard != null),
            dimmed = playingHand && card !in legal.playableCards && ui.showHints,
            onClick = if (swapping || playingHand || giving) ({ actions.onHandCard(card) }) else null,
        )
    }
}

/** Cards lie on the pile; more of the same rank may follow before the turn is played automatically. */
@Composable
private fun StagedBar(ui: GameUiState, actions: TableActions) {
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(ui.stagedAtMillis) {
        progress.snapTo(1f)
        progress.animateTo(0f, tween(GameViewModel.STAGE_WINDOW_MS.toInt(), easing = androidx.compose.animation.core.LinearEasing))
    }
    val rank = CardLabels.label(ui.staged.first().rank)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = TableColors.TurnGlow,
            trackColor = TableColors.Ink.copy(alpha = 0.15f),
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = actions.onCancelStaged,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TableColors.OnFelt),
            ) { Text(stringResource(R.string.game_staged_back)) }
            Text(
                stringResource(R.string.game_staged_hint, rank),
                color = TableColors.OnFelt,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = actions.onPlay,
                enabled = !ui.busy,
                colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
            ) { Text(stringResource(R.string.game_play_count, ui.staged.size), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ActionBar(view: ZpPlayerView, ui: GameUiState, actions: TableActions, tiny: Boolean = false) {
    val legal = view.legal
    // Without a connection to the host nothing can be played; the connection banner explains why.
    val connected = ui.connection == ConnectionStatus.Local || ui.connection == ConnectionStatus.Connected
    if (!connected) {
        Spacer(Modifier.height(52.dp))
        return
    }
    if (ui.botPlaysForMe) {
        BotPlaysForYou()
        return
    }
    if (ui.staged.isNotEmpty()) {
        StagedBar(ui, actions)
        return
    }
    if (view.phase == ZpPhase.SWAPPING) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (legal.canReady) {
                Text(
                    stringResource(if (tiny) R.string.game_swap_hint_short else R.string.game_swap_hint),
                    color = TableColors.OnFelt,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = actions.onReady,
                    enabled = !ui.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
                ) {
                    Text(stringResource(R.string.game_ready), fontWeight = FontWeight.Black)
                }
            } else {
                Text(stringResource(R.string.game_swap_waiting), color = TableColors.OnFelt)
            }
        }
        return
    }
    // All buttons you need during play sit here, at the bottom, on one line; the table above only shows cards.
    val enabled = !ui.busy
    val buttons = buildList {
        // "Vals!" can be called by anyone, also outside their turn.
        if (legal.canChallenge && view.challenge != null) {
            add(BarAction(stringResource(R.string.game_challenge), BarTone.DANGER, enabled, actions.onChallenge))
        }
        if (view.isMyTurn) {
            if (legal.canPickUp) {
                add(BarAction(stringResource(R.string.game_pick_up, view.discardCount), BarTone.OUTLINE, enabled, actions.onPickUp))
            }
            if (legal.canGamble) add(BarAction(stringResource(R.string.game_gamble), BarTone.TONAL, enabled, actions.onGamble))
            if (ui.selected.isNotEmpty() && legal.source != CardSource.FACE_DOWN) {
                add(BarAction(stringResource(R.string.game_play_count, ui.selected.size), BarTone.PRIMARY, enabled, actions.onPlay))
            }
        }
    }
    val hint = when {
        !view.isMyTurn -> null
        legal.source == CardSource.FACE_DOWN -> stringResource(R.string.game_select_blind)
        legal.playableCards.isEmpty() -> stringResource(R.string.game_must_pick_up)
        else -> null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (hint != null) {
            Text(hint, color = TableColors.OnFelt, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        TableActionBar(buttons)
    }
}

