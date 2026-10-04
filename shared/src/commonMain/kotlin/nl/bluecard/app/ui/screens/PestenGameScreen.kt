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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.bluecard.app.R
import androidx.compose.ui.draw.clip
import nl.bluecard.app.ui.components.PlayerProfiles
import nl.bluecard.app.ui.components.SocialButton
import nl.bluecard.app.ui.components.CARD_ASPECT
import nl.bluecard.app.ui.components.BarAction
import nl.bluecard.app.ui.components.BarTone
import nl.bluecard.app.ui.components.CardBack
import nl.bluecard.app.ui.components.TableActionBar
import nl.bluecard.app.ui.components.CardDragState
import nl.bluecard.app.ui.components.CardFan
import nl.bluecard.app.ui.components.DragItem
import nl.bluecard.app.ui.components.DragOverlay
import nl.bluecard.app.ui.components.DropHandler
import nl.bluecard.app.ui.components.DropTarget
import nl.bluecard.app.ui.components.EmptyCardSlot
import nl.bluecard.app.ui.components.KeepScreenOn
import nl.bluecard.app.ui.components.MiniFanBacks
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.SuitIcon
import nl.bluecard.app.ui.components.SystemBarIcons
import nl.bluecard.app.ui.components.TurnGlowBackground
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.components.dropTarget
import nl.bluecard.app.ui.components.rememberCardDragState
import nl.bluecard.app.ui.text.CardLabels
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.text.PestenTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit
import nl.bluecard.engine.pesten.PsEffect
import nl.bluecard.engine.pesten.PsEvent
import nl.bluecard.engine.pesten.PsPhase
import nl.bluecard.engine.pesten.PsPlayerView
import nl.bluecard.engine.pesten.PsPublicPlayer
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionPhase

/** Callbacks from the Pesten table to the view model. */
private class PestenActions(
    val onHandCard: (Card) -> Unit,
    val onDrop: DropHandler,
    val companionsFor: (Card) -> List<Card>,
    val onPlay: () -> Unit,
    val onDraw: () -> Unit,
    val onPass: () -> Unit,
    val onLastCard: () -> Unit,
    val onCatch: () -> Unit,
    val onChallenge: () -> Unit,
    val onTakeOver: (String) -> Unit,
    val onShowLog: () -> Unit,
    val onLeave: () -> Unit,
)

@Composable
fun PestenGameScreen(onFinished: () -> Unit, onExitToMenu: () -> Unit, onBackToLobby: () -> Unit) {
    val container = appContainer()
    val vm: PestenGameViewModel = viewModel { PestenGameViewModel(container) }
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
        if (ui.view?.phase == PsPhase.FINISHED) {
            // Let the last move play out (cards flying, a blind card turning over) before the summary.
            fx.awaitIdle()
            onFinished()
        }
    }
    LaunchedEffect(ui.kind, ui.lobbyPhase) {
        if (ui.kind == SessionKind.CLIENT && ui.lobbyPhase == SessionPhase.LOBBY) onBackToLobby()
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val text = when (message) {
                is GameMessage.Rejected -> GameTexts.reject(res, message.code)
                is GameMessage.Notice -> GameTexts.notice(res, message.notice)
                GameMessage.NotPlayable -> {
                    val view = vm.ui.value.view
                    GameTexts.reject(
                        res,
                        when {
                            view?.drawnCard != null -> "ONLY_DRAWN_CARD"
                            (view?.pendingDraw ?: 0) > 0 -> "MUST_DRAW_OR_STACK"
                            view?.wishedSuit != null -> "WRONG_SUIT"
                            else -> "DOES_NOT_FIT"
                        },
                    )
                }
                else -> null
            }
            if (text != null) {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }
    }

    // Being caught (cheating or forgetting "Laatste kaart!") is big news at the table: a stamp slams down.
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
        val view = ui.view ?: return@LaunchedEffect
        val kind = when (event) {
            is PsEvent.CheatCaught -> CalloutKind.CHEAT
            is PsEvent.FalseAccusation -> CalloutKind.FALSE_ALARM
            is PsEvent.LastCardForgotten -> CalloutKind.FORGOT
            else -> return@LaunchedEffect
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val stamp = res.getString(
            when (kind) {
                CalloutKind.CHEAT -> R.string.callout_cheat
                CalloutKind.FALSE_ALARM -> R.string.callout_false_alarm
                CalloutKind.FORGOT -> R.string.callout_forgot
            },
        )
        val cards = (event as? PsEvent.CheatCaught)?.cards.orEmpty()
        val mine = when (event) {
            is PsEvent.CheatCaught -> event.cheaterId == view.viewerId
            is PsEvent.FalseAccusation -> event.accuserId == view.viewerId
            is PsEvent.LastCardForgotten -> event.playerId == view.viewerId
            else -> false
        }
        callout = Callout(
            entry.seq, kind, stamp, PestenTexts.event(res, event, view.viewerId, nameResolver(res, ui)), cards,
            mine = mine, sharedTable = ui.kind != SessionKind.LOCAL,
        )
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
    if (showLog && view != null) PestenLogDialog(view, ui, onDismiss = { showLog = false })
    if (ui.choosingSuitFor.isNotEmpty()) SuitDialog(onChoose = vm::chooseSuit, onDismiss = vm::cancelSuitChoice)

    CompositionLocalProvider(LocalTableFx provides fx) {
        Box(
            Modifier
                .fillMaxSize()
                .feltTable(),
        ) {
            view?.let { PestenTableEffects(it, fx) }
            when {
                !ui.hasSession -> NoSession(onExitToMenu)
                view == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TableColors.OnFelt)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.game_loading), color = TableColors.OnFelt)
                }
                else -> PestenTable(
                    ui = ui,
                    view = view,
                    actions = PestenActions(
                        onHandCard = vm::onHandCardTap,
                        onDrop = { item, target, swipedUp ->
                            vm.onCardDropped(item, target, swipedUp).also { played ->
                                if (played) {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    if (item is DragItem.Face) fx.markSelfPlayed(listOf(item.card) + item.companions)
                                }
                            }
                        },
                        companionsFor = vm::companionsFor,
                        onPlay = vm::play,
                        onDraw = vm::draw,
                        onPass = vm::pass,
                        onLastCard = vm::callLastCard,
                        onCatch = vm::catchLastCard,
                        onChallenge = {
                            val view = ui.view
                            val open = view?.challenges.orEmpty().filter { it.playerId != view?.viewerId }
                            if (open.size > 1) {
                                challengeChoices = open.map { c -> ChallengeOption(c.id, view?.player(c.playerId)?.name ?: "?", c.cards) }
                            } else {
                                vm.challenge(open.singleOrNull()?.id)
                            }
                        },
                        onTakeOver = vm::takeOver,
                        onShowLog = { showLog = true },
                        onLeave = { confirmLeave = true },
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

/** Shown instead of the buttons after giving up as host. */
@Composable
internal fun BotPlaysForYou() {
    Text(
        stringResource(R.string.game_bot_plays_for_you),
        color = TableColors.OnFeltMuted,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 14.dp),
    )
}

private fun nameResolver(res: nl.bluecard.app.res.Resources, ui: PestenUiState): (String) -> String {
    val viewer = ui.view?.viewerId
    val you = GameTexts.selfName(res, viewer?.let(ui::nameOf).orEmpty())
    return { id -> if (id == viewer) you else ui.nameOf(id) }
}

/**
 * The ✕ at the table: leave (keeps a local game saved / ends a hosted game) or give up (counts as a loss; a bot
 * takes over in a Bluetooth game). For a client both mean the same, so only "Opgeven" is offered.
 */
@Composable
internal fun LeaveDialog(kind: SessionKind, onLeave: () -> Unit, onResign: () -> Unit, onDismiss: () -> Unit, watching: Boolean = false) {
    // Watching along (or the table display): nothing to give up, you just stop watching.
    val onlyWatching = watching && kind == SessionKind.CLIENT
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TableColors.FeltDark,
        titleContentColor = TableColors.OnFelt,
        textContentColor = TableColors.OnFelt,
        title = { Text(stringResource(R.string.game_stop_title), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                when (kind) {
                    SessionKind.LOCAL -> stringResource(R.string.game_leave_local) + "\n\n" + stringResource(R.string.game_resign_local)
                    SessionKind.HOST -> stringResource(R.string.game_leave_host) + "\n\n" + stringResource(R.string.game_resign_host)
                    SessionKind.CLIENT -> stringResource(if (onlyWatching) R.string.game_leave_watching else R.string.game_resign_client)
                },
            )
        },
        confirmButton = {
            val colors = ButtonDefaults.textButtonColors(contentColor = TableColors.TurnGlow)
            Row {
                if (onlyWatching) {
                    TextButton(onClick = onResign, colors = colors) { Text(stringResource(R.string.game_stop_watching), fontWeight = FontWeight.Bold) }
                    return@Row
                }
                TextButton(onClick = onResign, colors = ButtonDefaults.textButtonColors(contentColor = TableColors.Danger)) {
                    Text(stringResource(R.string.game_resign), fontWeight = FontWeight.Bold)
                }
                if (kind != SessionKind.CLIENT) {
                    TextButton(onClick = onLeave, colors = colors) { Text(stringResource(R.string.game_leave), fontWeight = FontWeight.Bold) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TableColors.OnFeltMuted)) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** After a jack: which suit has to follow? */
@Composable
private fun SuitDialog(onChoose: (Suit) -> Unit, onDismiss: () -> Unit) {
    val res = LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TableColors.FeltDark,
        titleContentColor = TableColors.OnFelt,
        textContentColor = TableColors.OnFelt,
        title = { Text(stringResource(R.string.game_choose_suit), fontWeight = FontWeight.Bold) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (suit in Suit.entries) {
                    Column(
                        Modifier
                            .clickable { onChoose(suit) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // A small white card face, so black suits stand out on the felt.
                        Box(
                            Modifier
                                .size(width = 54.dp, height = 64.dp)
                                .background(TableColors.CardFace, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) { SuitIcon(suit, 34.dp) }
                        Text(GameTexts.suitName(res, suit), style = MaterialTheme.typography.labelMedium, color = TableColors.OnFelt)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TableColors.TurnGlow)) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

// ====================================================================== layout

@Composable
private fun PestenTable(
    ui: PestenUiState,
    view: PsPlayerView,
    actions: PestenActions,
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
                    PestenTopBar(view, actions)
                    ConnectionBanner(ui.bluetoothOff, ui.connection, onReconnect, onExitToMenu)
                    PestenOpponents(opponents, ui, view, actions)
                },
                center = { PestenCenter(view, ui, actions, dragState, compact = false) },
                bottom = {
                    PestenEventLine(view, ui, actions.onShowLog)
                    PestenTurnPill(view, ui)
                },
            )
        } else if (landscape) {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PestenTopBar(view, actions)
                    ConnectionBanner(ui.bluetoothOff, ui.connection, onReconnect, onExitToMenu)
                    PestenOpponents(opponents, ui, view, actions)
                    Spacer(Modifier.weight(1f))
                    PestenCenter(view, ui, actions, dragState, compact = true)
                    PestenEventLine(view, ui, actions.onShowLog)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
                    PestenTurnPill(view, ui)
                    PestenHand(view, ui, actions, dragState, compact = true)
                    PestenActionBar(view, ui, actions)
                }
            }
        } else {
            val compact = maxHeight < 680.dp
            // Very small phones: smaller cards and no event line, so the buttons stay on screen.
            val tiny = maxHeight < 600.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(if (tiny) 4.dp else 6.dp)) {
                PestenTopBar(view, actions)
                ConnectionBanner(ui.bluetoothOff, ui.connection, onReconnect, onExitToMenu)
                PestenOpponents(opponents, ui, view, actions)
                Spacer(Modifier.weight(1f))
                PestenCenter(view, ui, actions, dragState, compact = compact)
                if (!tiny) PestenEventLine(view, ui, actions.onShowLog)
                Spacer(Modifier.weight(1f))
                PestenTurnPill(view, ui)
                PestenHand(view, ui, actions, dragState, compact = compact, tiny = tiny)
                PestenActionBar(view, ui, actions)
            }
        }
    }
}

@Composable
private fun PestenTopBar(view: PsPlayerView, actions: PestenActions) {
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
                stringResource(R.string.game_pesten),
                color = TableColors.OnFelt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!view.rules.enforceRules) {
                Text(
                    stringResource(R.string.rules_not_enforced),
                    color = TableColors.Highlight,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (view.rules.effects.values.any { it == PsEffect.REVERSE }) {
            // A turning arrow (mirrored when reversed); the words are for screen readers.
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(if (view.direction > 0) R.string.game_direction_cw else R.string.game_direction_ccw),
                tint = TableColors.OnFeltMuted,
                modifier = Modifier.padding(end = 4.dp).size(22.dp).graphicsLayer { scaleX = if (view.direction > 0) 1f else -1f },
            )
        }
        SocialButton()
        IconButton(onClick = actions.onShowLog) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.game_info), tint = TableColors.OnFelt)
        }
    }
}

// ====================================================================== opponents

@Composable
private fun PestenOpponents(opponents: List<PsPublicPlayer>, ui: PestenUiState, view: PsPlayerView, actions: PestenActions) {
    OpponentGrid(opponents.size, minWidth = 92.dp, maxWidth = 150.dp) { index, width ->
        val player = opponents[index]
        PestenOpponentPanel(
            player = player,
            seat = ui.seat(player.id),
            isCurrent = view.currentPlayerId == player.id,
            forgot = view.forgottenId == player.id,
            canTakeOver = ui.kind == SessionKind.HOST,
            onTakeOver = { actions.onTakeOver(player.id) },
            width = width,
        )
    }
}

@Composable
private fun PestenOpponentPanel(
    player: PsPublicPlayer,
    seat: SeatInfo?,
    isCurrent: Boolean,
    forgot: Boolean,
    canTakeOver: Boolean,
    onTakeOver: () -> Unit,
    width: Dp,
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
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
            val position = player.finishedPosition
            if (position != null) {
                Text(
                    stringResource(R.string.game_finished_position, position),
                    color = TableColors.Highlight,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                )
            } else {
                MiniFanBacks(player.handCount, 24.dp)
            }
            if (player.announced && player.handCount <= 2) {
                Text(stringResource(R.string.game_announced), color = TableColors.Highlight, style = MaterialTheme.typography.labelSmall)
            } else if (forgot) {
                // Everyone can see it: one card and nothing said.
                Text("1!", color = TableColors.Danger, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
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

// ====================================================================== centre

@Composable
private fun PestenCenter(view: PsPlayerView, ui: PestenUiState, actions: PestenActions, dragState: CardDragState, compact: Boolean) {
    val pileWidth = if (compact) 56.dp else 66.dp
    val canDraw = view.isMyTurn && view.legal.canDraw && !ui.busy
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        if (view.drawPileCount > 0) {
            // Tapping the draw pile takes a card, like at a real table. Its thickness shows what is left.
            DrawStack(
                view.drawPileCount,
                pileWidth,
                Modifier.fxAnchor(FxAnchor.DRAW),
                highlighted = canDraw,
                onClick = if (canDraw) actions.onDraw else null,
                contentDescription = stringResource(R.string.game_draw_pile) + ": " + view.drawPileCount,
            )
        } else {
            EmptyCardSlot(pileWidth, Modifier.fxAnchor(FxAnchor.DRAW))
        }
        PestenDiscardPile(view, pileWidth * 1.15f, dragState)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PestenRequirement(view)
        }
    }
}


/** The discard pile: the last cards slightly fanned, and the drop target for swiped cards. */
@Composable
private fun PestenDiscardPile(view: PsPlayerView, width: Dp, dragState: CardDragState) {
    // Cards still flying to the pile appear when they land.
    val incoming = LocalTableFx.current?.incoming(FxAnchor.PILE) ?: 0
    val visible = view.discardTop.dropLast(incoming).takeLast(3)
    val hovering = dragState.hovered == DropTarget.Pile
    Box(
        Modifier
            .width(width + 28.dp + fannedWidth(width, 3))
            .height(width * CARD_ASPECT + 6.dp)
            .dropTarget(dragState, DropTarget.Pile, inflate = 28.dp)
            .fxAnchor(FxAnchor.PILE)
            .graphicsLayer {
                val grow = if (hovering) 1.08f else 1f
                scaleX = grow
                scaleY = grow
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
                hovering = hovering,
                contentDescription = pluralStringResource(R.plurals.game_pile_count, view.discardCount, view.discardCount),
                fanned = true,
            )
        }
    }
}

/** What has to be played now: draw cards, a wished suit, or suit/value of the top card. */
@Composable
private fun PestenRequirement(view: PsPlayerView) {
    val res = LocalResources.current
    val top = view.discardTop.lastOrNull()
    val wished = view.wishedSuit
    val (text, urgent) = when {
        view.pendingDraw > 0 -> stringResource(
            if (view.rules.stackDraws) R.string.ps_must_draw_or_stack else R.string.ps_must_draw,
            view.pendingDraw,
        ) to true
        wished != null -> stringResource(R.string.ps_requirement_suit, GameTexts.suitName(res, wished) + " " + GameTexts.suitSymbolText(wished)) to false
        top == null || top.rank.isJoker -> stringResource(R.string.game_requirement_any) to false
        else -> stringResource(R.string.ps_requirement_match, GameTexts.suitSymbolText(top.suit), CardLabels.label(top.rank)) to false
    }
    Surface(
        color = animateColorAsState(if (urgent) TableColors.Danger else Color.Black.copy(alpha = 0.3f), label = "requirement").value,
        contentColor = if (urgent) Color.White else TableColors.OnFelt,
        shape = RoundedCornerShape(50),
        modifier = Modifier.widthIn(max = 150.dp).popWhen(urgent),
    ) {
        SlidingText(text) { shown ->
            Text(
                shown,
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PestenEventLine(view: PsPlayerView, ui: PestenUiState, onShowLog: () -> Unit) {
    val res = LocalResources.current
    // "X moet N pakken" is already shown next to the pile; show the play that caused it instead.
    val last = view.log.lastOrNull { it.event !is PsEvent.MustDraw } ?: return
    Surface(
        color = Color.Black.copy(alpha = 0.25f),
        contentColor = TableColors.OnFelt,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onShowLog),
    ) {
        SlidingText(PestenTexts.event(res, last.event, view.viewerId, nameResolver(res, ui))) { shown ->
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
private fun PestenLogDialog(view: PsPlayerView, ui: PestenUiState, onDismiss: () -> Unit) {
    val res = LocalResources.current
    val nameOf = nameResolver(res, ui)
    val isBot = { id: String -> ui.seat(id)?.let { it.kind == SeatKind.BOT || it.botControlled } == true }
    GameLogSheet(
        title = stringResource(R.string.game_log),
        historyTab = stringResource(R.string.log_tab_history),
        specialsTab = stringResource(R.string.rules_title),
        items = view.log.map { pestenLogItem(res, it, view.viewerId, nameOf, isBot) { id -> ui.seat(id)?.avatar } },
        onDismiss = onDismiss,
        rules = { PestenRules(view.rules) },
    ) {
        for ((rank, effect) in PestenTexts.specials(view.rules)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayingCard(Card(rank, Suit.HEARTS), 40.dp)
                Text(
                    if (effect == null) PestenTexts.jokerLong(res, view.rules) else PestenTexts.effectLong(res, effect),
                    color = TableColors.OnFelt,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ====================================================================== my side

@Composable
private fun PestenTurnPill(view: PsPlayerView, ui: PestenUiState) {
    val myPosition = view.me?.finishedPosition
    val current = view.currentPlayerId
    val exchange = view.exchange
    val (text, active) = when {
        view.me == null && !isTableDisplay() -> stringResource(R.string.spectator_banner) to false
        exchange != null && exchange.winnerId == view.viewerId -> stringResource(R.string.game_give_card, ui.nameOf(exchange.loserId)) to true
        exchange != null -> stringResource(R.string.game_exchanging, ui.nameOf(exchange.winnerId), ui.nameOf(exchange.loserId)) to false
        myPosition != null -> stringResource(R.string.game_you_finished, myPosition) to false
        view.isMyTurn && view.drawnCard != null -> stringResource(R.string.game_drawn_hint) to true
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

@Composable
private fun PestenHand(view: PsPlayerView, ui: PestenUiState, actions: PestenActions, dragState: CardDragState, compact: Boolean, tiny: Boolean = false) {
    val hand = view.myHand
    if (hand.isEmpty()) return
    val legal = view.legal
    val playing = view.isMyTurn
    val giving = legal.giveCards.isNotEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val zoneWidth = maxWidth
        val handWidth = (zoneWidth * (if (hand.size > 9) 0.19f else 0.22f))
            .coerceIn(if (tiny) 48.dp else if (compact) 58.dp else 64.dp, if (tiny) 62.dp else 88.dp)
        val fanHeight = handWidth * CARD_ASPECT + handWidth * 0.6f
        Box(Modifier.fillMaxWidth().height(fanHeight).fxAnchor(FxAnchor.HAND)) {
            if (playing) TurnGlowBackground(Modifier.matchParentSize())
            CardFan(
                cards = hand,
                hidden = ui.hiddenCards + (LocalTableFx.current?.incomingCards(FxAnchor.HAND) ?: emptySet()),
                isMyTurn = playing,
                anySelected = hand.any { it in ui.selected },
                dragState = dragState,
                onDrop = actions.onDrop,
                dragEnabled = playing && !ui.busy && !ui.botPlaysForMe,
                dragItem = { card -> DragItem.Face(card, if (playing) actions.companionsFor(card) else emptyList()) },
                cardWidth = handWidth,
                availableWidth = zoneWidth,
                modifier = Modifier.fillMaxSize(),
            ) { card ->
                PlayingCard(
                    card,
                    handWidth,
                    selected = card in ui.selected,
                    highlighted = playing && card in legal.fittingCards && ui.showHints,
                    dimmed = playing && card !in legal.playableCards && ui.showHints,
                    onClick = if (playing || giving) ({ actions.onHandCard(card) }) else null,
                )
            }
        }
    }
}

/** Every button needed during play, together at the bottom. */
@Composable
private fun PestenActionBar(view: PsPlayerView, ui: PestenUiState, actions: PestenActions) {
    val legal = view.legal
    val connected = ui.connection == nl.bluecard.multiplayer.session.ConnectionStatus.Local ||
        ui.connection == nl.bluecard.multiplayer.session.ConnectionStatus.Connected
    if (!connected) {
        Spacer(Modifier.height(52.dp))
        return
    }
    if (ui.botPlaysForMe) {
        BotPlaysForYou()
        return
    }
    val enabled = !ui.busy
    val showLastCard = legal.canCallLastCard && (view.myHand.size == 1 || view.isMyTurn)
    val buttons = buildList {
        if (legal.canChallenge) add(BarAction(stringResource(R.string.game_challenge), BarTone.DANGER, enabled, actions.onChallenge))
        if (legal.canCatch) add(BarAction(stringResource(R.string.game_catch), BarTone.ALERT, enabled, actions.onCatch))
        if (showLastCard) add(BarAction(stringResource(R.string.game_last_card), BarTone.HIGHLIGHT_OUTLINE, enabled, actions.onLastCard))
        if (view.isMyTurn) {
            if (legal.canPass) add(BarAction(stringResource(R.string.game_pass), BarTone.OUTLINE, enabled, actions.onPass))
            if (legal.canDraw) {
                val label = if (legal.drawCount > 1) stringResource(R.string.game_draw_n, legal.drawCount) else stringResource(R.string.game_draw)
                add(BarAction(label, BarTone.OUTLINE, enabled, actions.onDraw))
            }
            if (ui.selected.isNotEmpty()) {
                add(BarAction(stringResource(R.string.game_play_count, ui.selected.size), BarTone.PRIMARY, enabled, actions.onPlay))
            }
        }
    }
    TableActionBar(buttons)
}

