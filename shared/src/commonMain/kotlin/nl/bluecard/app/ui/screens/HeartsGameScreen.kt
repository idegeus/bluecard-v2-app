package nl.bluecard.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.text.HeartsTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.hartenjagen.HjEvent
import nl.bluecard.engine.hartenjagen.HjPhase
import nl.bluecard.engine.hartenjagen.HjPlay
import nl.bluecard.engine.hartenjagen.HjPlayerView
import nl.bluecard.engine.hartenjagen.HjRules
import nl.bluecard.engine.model.Card
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionPhase

/** The Hartenjagen table: one card per player per trick; avoid hearts and the queen of spades. */
@Composable
fun HeartsGameScreen(onFinished: () -> Unit, onExitToMenu: () -> Unit, onBackToLobby: () -> Unit) {
    val container = appContainer()
    val vm: HeartsGameViewModel = viewModel { HeartsGameViewModel(container) }
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
        if (ui.view?.phase == HjPhase.FINISHED) {
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
                    val v = vm.ui.value.view
                    GameTexts.reject(res, if (v?.leadSuit != null) "MUST_FOLLOW_SUIT" else "HEARTS_NOT_BROKEN")
                }
                else -> null
            }
            if (text != null) {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }
    }

    // After every deal: who took how many points (not after the last one: the result screen follows).
    var scored by remember { mutableStateOf<HjEvent.DealScored?>(null) }
    var seenSeq by remember { mutableStateOf<Long?>(null) }
    val lastSeq = ui.view?.log?.lastOrNull()?.seq
    LaunchedEffect(lastSeq) {
        val view = ui.view ?: return@LaunchedEffect
        val previous = seenSeq
        seenSeq = lastSeq
        if (previous == null || view.phase == HjPhase.FINISHED) return@LaunchedEffect
        view.log.filter { it.seq > previous }.map { it.event }.filterIsInstance<HjEvent.DealScored>().lastOrNull()?.let {
            scored = it
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
    if (showLog && view != null) HeartsLogSheet(view, ui) { showLog = false }
    scored?.let { deal -> if (view != null) DealScoreDialog(deal, view, ui) { scored = null } }

    CompositionLocalProvider(LocalTableFx provides fx) {
        Box(Modifier.fillMaxSize().feltTable()) {
            view?.let { HeartsTableEffects(it, fx) }
            when {
                !ui.hasSession -> NoSession(onExitToMenu)
                view == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TableColors.OnFelt)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.game_loading), color = TableColors.OnFelt)
                }
                else -> HeartsTable(
                    ui = ui,
                    view = view,
                    vm = vm,
                    dragState = dragState,
                    onPlayed = { card ->
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        fx.markSelfPlayed(listOf(card))
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

private fun nameResolver(res: Resources, ui: HeartsUiState): (String) -> String {
    val viewer = ui.view?.viewerId
    val you = GameTexts.selfName(res, viewer?.let(ui::nameOf).orEmpty())
    return { id -> if (id == viewer) you else ui.nameOf(id) }
}

@Composable
private fun HeartsTable(
    ui: HeartsUiState,
    view: HjPlayerView,
    vm: HeartsGameViewModel,
    dragState: CardDragState,
    onPlayed: (Card) -> Unit,
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
        val trickWidthBase = ((maxWidth - 32.dp) / view.players.size.coerceAtLeast(4)).coerceIn(40.dp, if (compact) 58.dp else 66.dp)
        val trickWidth = if (tiny) trickWidthBase.coerceAtMost(46.dp) else trickWidthBase
        val subtitle = stringResource(R.string.hj_round, view.deal + 1) + " · " + HeartsTexts.target(res, view.rules.targetScore)
        val top: @Composable () -> Unit = {
            TableTopBar(stringResource(R.string.game_hartenjagen), subtitle, view.isMyTurn, onLeave, onShowLog)
            ConnectionBanner(ui.bluetoothOff, ui.connection, vm::reconnect, onExitToMenu)
            HeartsOpponents(view, ui, vm::takeOver)
        }
        val middle: @Composable () -> Unit = {
            HeartsTrick(view, ui, dragState, trickWidth)
            if (!tiny) EventLine(view.log.lastOrNull()?.let { HeartsTexts.event(res, it.event, nameResolver(res, ui)) }, onShowLog)
        }
        val bottom: @Composable () -> Unit = {
            HeartsTurnPill(view, ui)
            HeartsHand(view, ui, vm, dragState, onPlayed, compact, tiny)
            HeartsActionBar(view, ui, vm)
        }
        if (isTableDisplay()) {
            TableDisplayLayout(
                top = top,
                center = { HeartsTrick(view, ui, dragState, 66.dp) },
                bottom = {
                    EventLine(view.log.lastOrNull()?.let { HeartsTexts.event(res, it.event, nameResolver(res, ui)) }, onShowLog)
                    HeartsTurnPill(view, ui)
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
private fun HeartsOpponents(view: HjPlayerView, ui: HeartsUiState, onTakeOver: (String) -> Unit) {
    val res = LocalResources.current
    val opponents = view.players.filter { it.id != view.viewerId }
    OpponentGrid(opponents.size) { index, width ->
        val player = opponents[index]
        val lines = buildList {
            add(scoreLine(res, player.score, player.dealPoints))
            if (view.phase == HjPhase.PASSING && player.hasPassed) add(stringResource(R.string.hj_passed_badge))
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
            width = width,
        )
    }
}

/** "12 pt (+3)": the total and what this deal adds so far. */
private fun scoreLine(res: Resources, total: Int, deal: Int): String =
    HeartsTexts.points(res, total) + if (deal > 0) " (+$deal)" else ""

/** The trick: the cards in the order they were played, each with its player; the card that wins so far stands out. */
@Composable
private fun HeartsTrick(view: HjPlayerView, ui: HeartsUiState, dragState: CardDragState, cardWidth: Dp) {
    val res = LocalResources.current
    val nameOf = nameResolver(res, ui)
    val incomingCards = LocalTableFx.current?.incomingCards(FxAnchor.PILE).orEmpty()
    val showingLast = view.trick.isEmpty() && view.lastTrick.isNotEmpty()
    val plays: List<HjPlay> = if (showingLast) view.lastTrick else view.trick.filter { it.card !in incomingCards }
    val lead = plays.firstOrNull()?.card?.suit
    val winning = plays.filter { it.card.suit == lead }.maxByOrNull { it.card.rank.value }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(cardWidth * CARD_ASPECT + 22.dp)
                .dropTarget(dragState, DropTarget.Pile, inflate = 24.dp)
                .fxAnchor(FxAnchor.PILE),
            contentAlignment = Alignment.Center,
        ) {
            if (plays.isEmpty()) {
                EmptyCardSlot(cardWidth)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
                    for (play in plays) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cardWidth + 4.dp)) {
                            PlayingCard(
                                play.card,
                                cardWidth,
                                highlighted = play == winning,
                                dimmed = showingLast && play != winning,
                            )
                            Text(
                                nameOf(play.playerId),
                                color = if (play == winning) TableColors.Highlight else TableColors.OnFeltMuted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        val me = view.me
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val requirement = when {
                view.phase != HjPhase.PLAYING -> null
                showingLast -> view.lastTrickWinnerId?.let { res.getString(R.string.pr_ev_trick, nameOf(it)) }
                lead != null -> stringResource(R.string.hj_follow, GameTexts.suitName(res, lead) + " " + GameTexts.suitSymbolText(lead))
                else -> stringResource(R.string.hj_lead)
            }
            requirement?.let { TableChip(it) }
            if (view.heartsBroken && view.phase == HjPhase.PLAYING) TableChip(stringResource(R.string.hj_hearts_broken_badge))
            if (me != null) TableChip(stringResource(R.string.hj_you_score, scoreLine(res, me.score, me.dealPoints)))
        }
    }
}

@Composable
private fun HeartsTurnPill(view: HjPlayerView, ui: HeartsUiState) {
    val legal = view.legal
    val current = view.currentPlayerId
    val (text, active) = when {
        view.me == null && !isTableDisplay() -> stringResource(R.string.spectator_banner) to false
        legal.passCount > 0 -> pluralStringResource(
            R.plurals.hj_pass_to,
            legal.passCount,
            legal.passCount,
            view.passTargetId?.let(ui::nameOf).orEmpty(),
        ) to true
        view.phase == HjPhase.PASSING -> stringResource(R.string.hj_pass_waiting) to false
        view.isMyTurn -> stringResource(R.string.game_your_turn) to true
        current != null -> stringResource(R.string.game_waiting_for, ui.nameOf(current)) to false
        else -> "" to false
    }
    TurnPill(text, active)
}

@Composable
private fun HeartsHand(
    view: HjPlayerView,
    ui: HeartsUiState,
    vm: HeartsGameViewModel,
    dragState: CardDragState,
    onPlayed: (Card) -> Unit,
    compact: Boolean,
    tiny: Boolean = false,
) {
    val hand = view.myHand
    if (hand.isEmpty()) return
    val playing = view.isMyTurn
    val passing = view.legal.passCount > 0
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val zoneWidth = maxWidth
        val cardWidth = (zoneWidth * (if (hand.size > 9) 0.19f else 0.22f))
            .coerceIn(if (tiny) 48.dp else if (compact) 56.dp else 62.dp, if (tiny) 62.dp else 86.dp)
        Box(Modifier.fillMaxWidth().height(cardWidth * CARD_ASPECT + cardWidth * 0.6f).fxAnchor(FxAnchor.HAND)) {
            if (playing || passing) TurnGlowBackground(Modifier.matchParentSize())
            CardFan(
                cards = hand,
                hidden = ui.hiddenCards + (LocalTableFx.current?.incomingCards(FxAnchor.HAND) ?: emptySet()),
                isMyTurn = playing || passing,
                anySelected = ui.selected.isNotEmpty(),
                dragState = dragState,
                onDrop = { item, target, swipedUp ->
                    vm.onCardDropped(item, target, swipedUp).also { played ->
                        if (played && item is DragItem.Face) onPlayed(item.card)
                    }
                },
                dragEnabled = playing && !ui.busy && !ui.botPlaysForMe,
                dragItem = { card -> DragItem.Face(card, emptyList()) },
                cardWidth = cardWidth,
                availableWidth = zoneWidth,
                modifier = Modifier.fillMaxSize(),
            ) { card ->
                val penalty = HjRules.isPenalty(card, view.rules)
                PlayingCard(
                    card,
                    cardWidth,
                    selected = card in ui.selected,
                    highlighted = passing && penalty && ui.showHints && card !in ui.selected,
                    dimmed = playing && ui.showHints && card !in view.legal.playableCards,
                    onClick = if (playing || passing) ({ vm.onHandCardTap(card) }) else null,
                )
            }
        }
    }
}

@Composable
private fun HeartsActionBar(view: HjPlayerView, ui: HeartsUiState, vm: HeartsGameViewModel) {
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
        if (legal.passCount > 0) {
            add(
                BarAction(
                    stringResource(R.string.hj_pass_button, ui.selected.size, legal.passCount),
                    BarTone.PRIMARY,
                    enabled && ui.selected.size == legal.passCount,
                    vm::passSelected,
                ),
            )
        } else if (view.isMyTurn && ui.selected.size == 1) {
            add(BarAction(stringResource(R.string.game_play_count, 1), BarTone.PRIMARY, enabled, vm::playSelected))
        }
    }
    if (buttons.isEmpty()) Spacer(Modifier.height(52.dp)) else TableActionBar(buttons)
}

/** The points of the deal that just ended and the totals. */
@Composable
private fun DealScoreDialog(deal: HjEvent.DealScored, view: HjPlayerView, ui: HeartsUiState, onDismiss: () -> Unit) {
    val res = LocalResources.current
    val nameOf = nameResolver(res, ui)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TableColors.FeltDark,
        titleContentColor = TableColors.OnFelt,
        textContentColor = TableColors.OnFelt,
        title = { Text(stringResource(R.string.hj_ev_scored, deal.deal + 1), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Text("+", Modifier.width(48.dp), color = TableColors.OnFeltMuted, textAlign = TextAlign.End)
                    Text(stringResource(R.string.hj_total), Modifier.width(72.dp), color = TableColors.OnFeltMuted, textAlign = TextAlign.End)
                }
                for (player in view.players.sortedBy { deal.totals[it.id] ?: 0 }) {
                    val points = deal.points[player.id] ?: 0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(nameOf(player.id), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (points > 0) "+$points" else "0",
                            Modifier.width(48.dp),
                            color = if (points > 0) TableColors.Danger else TableColors.Highlight,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Bold,
                        )
                        Text((deal.totals[player.id] ?: 0).toString(), Modifier.width(72.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Black)
                    }
                }
                Text(HeartsTexts.target(res, view.rules.targetScore), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TableColors.TurnGlow)) {
                Text(stringResource(R.string.hj_continue), fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun HeartsLogSheet(view: HjPlayerView, ui: HeartsUiState, onDismiss: () -> Unit) {
    val res = LocalResources.current
    val nameOf = nameResolver(res, ui)
    val isBot = { id: String -> ui.seat(id)?.let { it.kind == SeatKind.BOT || it.botControlled } == true }
    GameLogSheet(
        title = stringResource(R.string.game_log),
        historyTab = stringResource(R.string.log_tab_history),
        specialsTab = stringResource(R.string.rules_title),
        items = view.log.map { heartsLogItem(res, it, nameOf, isBot) { id -> ui.seat(id)?.avatar } },
        onDismiss = onDismiss,
        rules = { HeartsRules(view.rules) },
    ) {
        HeartsRulesSummary(view.rules)
        Text(stringResource(R.string.rules_hj_play), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
