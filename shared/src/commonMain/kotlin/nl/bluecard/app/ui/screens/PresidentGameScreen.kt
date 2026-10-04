package nl.bluecard.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.bluecard.app.R
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.Resources
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.components.BarAction
import nl.bluecard.app.ui.components.BarTone
import nl.bluecard.app.ui.components.CARD_ASPECT
import nl.bluecard.app.ui.components.CardDragState
import nl.bluecard.app.ui.components.CardFan
import nl.bluecard.app.ui.components.DragItem
import nl.bluecard.app.ui.components.DragOverlay
import nl.bluecard.app.ui.components.DropTarget
import nl.bluecard.app.ui.components.EmptyCardSlot
import nl.bluecard.app.ui.components.FxAnchor
import nl.bluecard.app.ui.components.FxOverlay
import nl.bluecard.app.ui.components.GameLogSheet
import nl.bluecard.app.ui.components.KeepScreenOn
import nl.bluecard.app.ui.components.LocalTableFx
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.SystemBarIcons
import nl.bluecard.app.ui.components.TableActionBar
import nl.bluecard.app.ui.components.TurnGlowBackground
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.components.dropTarget
import nl.bluecard.app.ui.components.feltTable
import nl.bluecard.app.ui.components.fxAnchor
import nl.bluecard.app.ui.components.platformUi
import nl.bluecard.app.ui.components.rememberCardDragState
import nl.bluecard.app.ui.components.rememberTableFx
import nl.bluecard.app.ui.text.CardLabels
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.text.PresidentTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.presidenten.PrPhase
import nl.bluecard.engine.presidenten.PrPlay
import nl.bluecard.engine.presidenten.PrPlayerView
import nl.bluecard.engine.presidenten.PrRules
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionPhase

/** The Presidenten table: sets are laid on the trick in the middle; higher sets of the same size, or pass. */
@Composable
fun PresidentGameScreen(onFinished: () -> Unit, onExitToMenu: () -> Unit, onBackToLobby: () -> Unit) {
    val container = appContainer()
    val vm: PresidentGameViewModel = viewModel { PresidentGameViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val dragState = rememberCardDragState()
    val fx = rememberTableFx()
    val haptics = LocalHapticFeedback.current

    KeepScreenOn((ui.keepScreenOn || isTableDisplay()) && ui.hasSession)
    SystemBarIcons(darkBackground = true)

    LaunchedEffect(ui.view?.phase) {
        if (ui.view?.phase == PrPhase.FINISHED) {
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
                GameMessage.NotPlayable -> GameTexts.reject(res, "TOO_LOW")
                else -> null
            }
            if (text != null) {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
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
    if (showLog && view != null) PresidentLogSheet(view, ui) { showLog = false }

    CompositionLocalProvider(LocalTableFx provides fx) {
        Box(Modifier.fillMaxSize().feltTable()) {
            view?.let { PresidentTableEffects(it, fx) }
            when {
                !ui.hasSession -> NoSession(onExitToMenu)
                view == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TableColors.OnFelt)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.game_loading), color = TableColors.OnFelt)
                }
                else -> PresidentTable(
                    ui = ui,
                    view = view,
                    vm = vm,
                    dragState = dragState,
                    onPlayed = { cards ->
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        fx.markSelfPlayed(cards)
                    },
                    onShowLog = { showLog = true },
                    onLeave = { confirmLeave = true },
                    onExitToMenu = {
                        vm.leave()
                        onExitToMenu()
                    },
                )
            }
            FxOverlay(fx)
            DragOverlay(dragState)
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 48.dp))
        }
    }
}

private fun nameResolver(res: Resources, ui: PresidentUiState): (String) -> String {
    val viewer = ui.view?.viewerId
    val you = GameTexts.selfName(res, viewer?.let(ui::nameOf).orEmpty())
    return { id -> if (id == viewer) you else ui.nameOf(id) }
}

@Composable
private fun PresidentTable(
    ui: PresidentUiState,
    view: PrPlayerView,
    vm: PresidentGameViewModel,
    dragState: CardDragState,
    onPlayed: (List<nl.bluecard.engine.model.Card>) -> Unit,
    onShowLog: () -> Unit,
    onLeave: () -> Unit,
    onExitToMenu: () -> Unit,
) {
    val res = LocalResources.current
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val landscape = maxWidth > maxHeight && maxWidth > 560.dp
        val compact = maxHeight < 680.dp
        // Very small phones (iPhone SE size): smaller cards and no event line, so the buttons stay on screen.
        val tiny = maxHeight < 600.dp
        val top: @Composable () -> Unit = {
            TableTopBar(stringResource(R.string.game_presidenten), null, view.isMyTurn, onLeave, onShowLog)
            ConnectionBanner(ui.bluetoothOff, ui.connection, vm::reconnect, onExitToMenu)
            PresidentOpponents(view, ui, vm::takeOver)
        }
        val middle: @Composable () -> Unit = {
            PresidentTrick(view, ui, dragState, if (tiny) 44.dp else if (compact) 54.dp else 64.dp)
            if (!tiny) EventLine(view.log.lastOrNull()?.let { PresidentTexts.event(res, it.event, nameResolver(res, ui)) }, onShowLog)
        }
        val bottom: @Composable () -> Unit = {
            PresidentTurnPill(view, ui)
            PresidentHand(view, ui, vm, dragState, onPlayed, compact, tiny)
            PresidentActionBar(view, ui, vm)
        }
        if (isTableDisplay()) {
            TableDisplayLayout(
                top = top,
                center = { PresidentTrick(view, ui, dragState, 64.dp) },
                bottom = {
                    EventLine(view.log.lastOrNull()?.let { PresidentTexts.event(res, it.event, nameResolver(res, ui)) }, onShowLog)
                    PresidentTurnPill(view, ui)
                },
            )
        } else if (landscape) {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    top()
                    Spacer(Modifier.weight(1f))
                    middle()
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) { bottom() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                top()
                Spacer(Modifier.weight(1f))
                middle()
                Spacer(Modifier.weight(1f))
                bottom()
            }
        }
    }
}

@Composable
private fun PresidentOpponents(view: PrPlayerView, ui: PresidentUiState, onTakeOver: (String) -> Unit) {
    val res = LocalResources.current
    val opponents = view.players.filter { it.id != view.viewerId }
    OpponentGrid(opponents.size) { index, width ->
        val player = opponents[index]
        val position = player.finishedPosition
        val title = player.title
        val lines = buildList {
            when {
                position != null -> add("#$position " + PresidentTexts.title(res, PresidentTexts.titleFor(position, view.players.size)))
                player.passed -> add(stringResource(R.string.pr_passed))
                title != null -> add(PresidentTexts.title(res, title))
            }
            if (position == null && player.passed && title != null) add(PresidentTexts.title(res, title))
        }
        OpponentChip(
            playerId = player.id,
            name = player.name,
            seat = ui.seat(player.id),
            handCount = player.handCount,
            isCurrent = view.currentPlayerId == player.id,
            lines = lines,
            canTakeOver = ui.kind == SessionKind.HOST,
            onTakeOver = { onTakeOver(player.id) },
            dimmed = player.passed || position != null,
            width = width,
        )
    }
}

/** The trick in the middle: the set to beat on top of the earlier ones, plus what has to be laid. */
@Composable
private fun PresidentTrick(view: PrPlayerView, ui: PresidentUiState, dragState: CardDragState, cardWidth: Dp) {
    val res = LocalResources.current
    val incoming = LocalTableFx.current?.incoming(FxAnchor.PILE) ?: 0
    // A trick that was just won stays visible (dimmed) until the next set is laid.
    val showingLast = view.plays.isEmpty() && view.lastTrick.isNotEmpty()
    val plays = if (showingLast) view.lastTrick else view.plays
    val hovering = dragState.hovered == DropTarget.Pile
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(cardWidth * CARD_ASPECT + 44.dp)
                .dropTarget(dragState, DropTarget.Pile, inflate = 24.dp)
                .fxAnchor(FxAnchor.PILE),
            contentAlignment = Alignment.Center,
        ) {
            if (plays.isEmpty() || (incoming > 0 && plays.size == 1 && !showingLast)) {
                EmptyCardSlot(cardWidth)
            } else {
                // Earlier sets peek out underneath, the latest set on top.
                val visible = plays.takeLast(3).let { if (incoming > 0 && !showingLast) it.dropLast(1) else it }
                visible.forEachIndexed { depth, play ->
                    val fromTop = visible.size - 1 - depth
                    SetOfCards(
                        play,
                        width = if (fromTop == 0) cardWidth else cardWidth * 0.82f,
                        modifier = Modifier
                            .offset(x = (fromTop * -14).dp, y = (fromTop * -22).dp)
                            .rotate(fromTop * -5f),
                        dimmed = showingLast || fromTop > 0,
                        highlight = hovering && fromTop == 0,
                    )
                }
            }
        }
        val top = view.top
        val text = when {
            view.phase != PrPhase.PLAYING -> null
            showingLast -> view.lastTrickWinnerId?.let { res.getString(R.string.pr_ev_trick, nameResolver(res, ui)(it)) }
            top == null -> stringResource(R.string.pr_requirement_lead)
            else -> {
                val rank = PrRules.setRank(top.cards)!!
                pluralStringResource(R.plurals.pr_requirement, top.cards.size, top.cards.size, CardLabels.label(rank))
            }
        }
        if (text != null) TableChip(text)
    }
}

@Composable
private fun SetOfCards(play: PrPlay, width: Dp, modifier: Modifier, dimmed: Boolean, highlight: Boolean) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-(width * 0.55f))) {
        play.cards.forEachIndexed { i, card ->
            PlayingCard(
                card,
                width,
                Modifier.rotate((i - (play.cards.size - 1) / 2f) * 5f),
                dimmed = dimmed,
                highlighted = highlight,
            )
        }
    }
}

@Composable
private fun PresidentTurnPill(view: PrPlayerView, ui: PresidentUiState) {
    val res = LocalResources.current
    val myPosition = view.me?.finishedPosition
    val myExchange = view.exchanges.firstOrNull { it.highId == view.viewerId && !it.done }
    val current = view.currentPlayerId
    val (text, active) = when {
        view.me == null && !isTableDisplay() -> stringResource(R.string.spectator_banner) to false
        myExchange != null ->
            pluralStringResource(R.plurals.pr_give_cards, myExchange.count, myExchange.count, ui.nameOf(myExchange.lowId)) to true
        view.phase == PrPhase.EXCHANGING -> stringResource(R.string.pr_waiting_exchange) to false
        myPosition != null -> stringResource(
            R.string.pr_you_finished,
            PresidentTexts.title(res, PresidentTexts.titleFor(myPosition, view.players.size)),
        ) to false
        view.isMyTurn -> stringResource(R.string.game_your_turn) to true
        current != null -> stringResource(R.string.game_waiting_for, ui.nameOf(current)) to false
        else -> "" to false
    }
    AnimatedContent(text to active, transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.9f)) togetherWith fadeOut(tween(150)) }, label = "pill") { (t, a) ->
        TurnPill(t, a)
    }
}

@Composable
private fun PresidentHand(
    view: PrPlayerView,
    ui: PresidentUiState,
    vm: PresidentGameViewModel,
    dragState: CardDragState,
    onPlayed: (List<nl.bluecard.engine.model.Card>) -> Unit,
    compact: Boolean,
    tiny: Boolean = false,
) {
    val hand = view.myHand
    if (hand.isEmpty()) return
    val playing = view.isMyTurn
    val giving = view.legal.giveCount > 0
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val zoneWidth = maxWidth
        val cardWidth = (zoneWidth * (if (hand.size > 9) 0.19f else 0.22f))
            .coerceIn(if (tiny) 48.dp else if (compact) 56.dp else 62.dp, if (tiny) 62.dp else 86.dp)
        Box(Modifier.fillMaxWidth().height(cardWidth * CARD_ASPECT + cardWidth * 0.6f).fxAnchor(FxAnchor.HAND)) {
            if (playing || giving) TurnGlowBackground(Modifier.matchParentSize())
            CardFan(
                cards = hand,
                hidden = ui.hiddenCards + (LocalTableFx.current?.incomingCards(FxAnchor.HAND) ?: emptySet()),
                isMyTurn = playing || giving,
                anySelected = ui.selected.isNotEmpty(),
                dragState = dragState,
                onDrop = { item, target, swipedUp ->
                    vm.onCardDropped(item, target, swipedUp).also { played ->
                        if (played && item is DragItem.Face) onPlayed(listOf(item.card) + item.companions)
                    }
                },
                dragEnabled = playing && !ui.busy && !ui.botPlaysForMe,
                dragItem = { card -> DragItem.Face(card, if (playing) vm.companionsFor(card) else emptyList()) },
                cardWidth = cardWidth,
                availableWidth = zoneWidth,
                modifier = Modifier.fillMaxSize(),
            ) { card ->
                PlayingCard(
                    card,
                    cardWidth,
                    selected = card in ui.selected,
                    dimmed = playing && ui.showHints && card !in view.legal.playableCards && !card.rank.isJoker,
                    onClick = if (playing || giving) ({ vm.onHandCardTap(card) }) else null,
                )
            }
        }
    }
}

@Composable
private fun PresidentActionBar(view: PrPlayerView, ui: PresidentUiState, vm: PresidentGameViewModel) {
    val connected = ui.connection == ConnectionStatus.Local || ui.connection == ConnectionStatus.Connected
    if (!connected) {
        Spacer(Modifier.height(52.dp))
        return
    }
    if (ui.botPlaysForMe) {
        BotPlaysForYou()
        return
    }
    val enabled = !ui.busy
    val legal = view.legal
    val buttons = buildList {
        if (legal.giveCount > 0) {
            add(
                BarAction(
                    stringResource(R.string.pr_give_button, ui.selected.size, legal.giveCount),
                    BarTone.PRIMARY,
                    enabled && ui.selected.size == legal.giveCount,
                    vm::giveSelected,
                ),
            )
        } else if (view.isMyTurn) {
            if (legal.canPass) add(BarAction(stringResource(R.string.game_pass), BarTone.OUTLINE, enabled, vm::pass))
            if (ui.selected.isNotEmpty()) {
                add(BarAction(stringResource(R.string.game_play_count, ui.selected.size), BarTone.PRIMARY, enabled && ui.selectionPlayable, vm::playSelected))
            }
        }
    }
    if (buttons.isEmpty()) Spacer(Modifier.height(52.dp)) else TableActionBar(buttons)
}

@Composable
private fun PresidentLogSheet(view: PrPlayerView, ui: PresidentUiState, onDismiss: () -> Unit) {
    val res = LocalResources.current
    val nameOf = nameResolver(res, ui)
    val isBot = { id: String -> ui.seat(id)?.let { it.kind == SeatKind.BOT || it.botControlled } == true }
    GameLogSheet(
        title = stringResource(R.string.game_log),
        historyTab = stringResource(R.string.log_tab_history),
        specialsTab = stringResource(R.string.rules_title),
        items = view.log.map { presidentLogItem(res, it, nameOf, isBot) { id -> ui.seat(id)?.avatar } },
        onDismiss = onDismiss,
        rules = { PresidentRules(view.rules) },
    ) {
        Text(stringResource(R.string.rules_pr_titles), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(if (view.rules.twoHigh) R.string.rules_pr_order_two_high else R.string.rules_pr_order_two_low),
            color = TableColors.OnFeltMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
