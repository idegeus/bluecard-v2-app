package nl.bluecard.app.ui.screens

import nl.bluecard.app.platform.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.bluecard.app.AppContainer
import nl.bluecard.app.ui.components.DragItem
import nl.bluecard.app.ui.components.DropTarget
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.session.zpPort
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.zweedspesten.CardSource
import nl.bluecard.engine.zweedspesten.ZpAction
import nl.bluecard.engine.zweedspesten.ZpPhase
import nl.bluecard.engine.zweedspesten.ZpPlayerView
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SessionNotice
import nl.bluecard.multiplayer.session.SessionPhase
import nl.bluecard.multiplayer.session.SubmitResult

enum class SessionKind { LOCAL, HOST, CLIENT }

data class GameUiState(
    val hasSession: Boolean = true,
    val kind: SessionKind = SessionKind.LOCAL,
    val view: ZpPlayerView? = null,
    val seats: List<SeatInfo> = emptyList(),
    val connection: ConnectionStatus = ConnectionStatus.Local,
    val lobbyPhase: SessionPhase? = null,
    /** Cards selected to be played together (from hand or face-up cards). */
    val selected: Set<Card> = emptySet(),
    /** Swap phase: the hand card waiting for a face-up card to swap with. */
    val swapHandCard: Card? = null,
    /** Swap phase: or the face-up card waiting for a hand card (you can start on either side). */
    val swapFaceUpCard: Card? = null,
    val busy: Boolean = false,
    /** Cards that were swiped away and are on their way to the pile. */
    val hiddenCards: Set<Card> = emptySet(),
    /** Cards swiped onto the pile that are not played yet (more of the same rank may follow). */
    val staged: List<Card> = emptyList(),
    val stagedAtMillis: Long = 0,
    /** Bluetooth switched off during a Bluetooth game. */
    val bluetoothOff: Boolean = false,
    val showHints: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** You gave up as host: a bot plays your seat and you only watch. */
    val botPlaysForMe: Boolean = false,
) {
    fun nameOf(playerId: String): String =
        view?.player(playerId)?.name ?: seats.firstOrNull { it.id == playerId }?.name ?: playerId

    fun seat(playerId: String): SeatInfo? = seats.firstOrNull { it.id == playerId }
}

/** One-off UI messages. Codes are turned into Dutch text by the screen. */
sealed interface GameMessage {
    data class Rejected(val code: String) : GameMessage
    data class Notice(val notice: SessionNotice) : GameMessage
    data object SelectHandCardFirst : GameMessage
    data object OnlySameRank : GameMessage
    data object NotPlayable : GameMessage
}

private data class LocalUi(
    val selected: Set<Card> = emptySet(),
    val swapHandCard: Card? = null,
    val swapFaceUpCard: Card? = null,
    val busy: Boolean = false,
    /** Cards just swiped away: kept invisible until the host's new state (without them) arrives. */
    val hidden: Set<Card> = emptySet(),
    val hiddenAtVersion: Long = -1,
    /** Swiped cards lying on the pile, waiting for more of the same rank before the turn is played. */
    val staged: List<Card> = emptyList(),
    val stagedAtMillis: Long = 0,
)

/**
 * Game screen logic. It never decides whether a move is valid: it only offers what the engine listed in
 * [ZpPlayerView.legal] and submits intents through the session's [nl.bluecard.multiplayer.session.PlayerPort],
 * where the (host's) engine validates them again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(private val container: AppContainer) : ViewModel() {

    companion object {
        /** How long swiped cards wait on the pile for more of the same rank before they are played. */
        const val STAGE_WINDOW_MS = 3_000L
    }

    private val localUi = MutableStateFlow(LocalUi())
    private val _messages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 16)

    private val session: StateFlow<ActiveSession?> = container.sessions.active

    private val sessionState: Flow<GameUiState> = session.flatMapLatest { active ->
        if (active == null) {
            flowOf(GameUiState(hasSession = false))
        } else {
            // This screen is for Zweeds Pesten; another game has its own screen.
            val port = active.zpPort ?: return@flatMapLatest flowOf(GameUiState(hasSession = false))
            combine(port.view, port.lobby, port.connection) { view, lobby, connection ->
                GameUiState(
                    hasSession = true,
                    kind = when (active) {
                        is ActiveSession.Local -> SessionKind.LOCAL
                        is ActiveSession.Hosting -> SessionKind.HOST
                        is ActiveSession.Joined -> SessionKind.CLIENT
                    },
                    view = view,
                    seats = lobby?.seats.orEmpty(),
                    connection = connection,
                    lobbyPhase = lobby?.phase,
                )
            }
        }
    }

    val ui: StateFlow<GameUiState> = combine(
        sessionState,
        localUi,
        container.settings,
        container.platform.transport?.ready ?: flowOf(true),
    ) { base, local, settings, radioReady ->
        val view = base.view
        // Drop selections that are no longer possible (e.g. after the state changed).
        val selectable = view?.legal?.playableCards?.toSet().orEmpty()
        base.copy(
            selected = local.selected.filter { it in selectable }.toSet(),
            swapHandCard = local.swapHandCard?.takeIf { view?.phase == ZpPhase.SWAPPING && it in view.myHand },
            swapFaceUpCard = local.swapFaceUpCard?.takeIf { view?.phase == ZpPhase.SWAPPING && view.me?.faceUp?.contains(it) == true },
            busy = local.busy,
            hiddenCards = (if (view != null && view.stateVersion == local.hiddenAtVersion) local.hidden else emptySet()) +
                local.staged,
            staged = local.staged,
            stagedAtMillis = local.stagedAtMillis,
            bluetoothOff = base.kind != SessionKind.LOCAL && !radioReady,
            showHints = settings.showHints,
            keepScreenOn = settings.keepScreenOn,
            botPlaysForMe = view != null && base.seats.firstOrNull { it.id == view.viewerId }?.botControlled == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameUiState())

    val messages: Flow<GameMessage> = merge(
        _messages.asSharedFlow(),
        session.flatMapLatest { active -> active?.port?.notices ?: emptyFlow() }.map { GameMessage.Notice(it) },
    )

    // ================================================================== card taps

    fun onHandCardTap(card: Card) {
        val state = ui.value
        val view = state.view ?: return
        // The winner of the last round gives this card to the loser.
        if (view.legal.giveCards.isNotEmpty()) {
            if (card in view.legal.giveCards) submit(ZpAction.GiveCard(card))
            return
        }
        when (view.phase) {
            ZpPhase.SWAPPING -> {
                if (!view.legal.canSwap) return
                val faceUp = state.swapFaceUpCard
                if (faceUp != null) {
                    localUi.update { it.copy(swapFaceUpCard = null, swapHandCard = null) }
                    submit(ZpAction.Swap(card, faceUp))
                } else {
                    localUi.update { it.copy(swapHandCard = if (it.swapHandCard == card) null else card) }
                }
            }
            ZpPhase.PLAYING -> if (view.legal.source == CardSource.HAND) toggleSelection(card, state)
            ZpPhase.FINISHED -> Unit
        }
    }

    fun onFaceUpTap(card: Card) {
        val state = ui.value
        val view = state.view ?: return
        when (view.phase) {
            ZpPhase.SWAPPING -> {
                if (!view.legal.canSwap) return
                val handCard = state.swapHandCard
                if (handCard == null) {
                    // Start from the table: the next tapped hand card trades places with this one.
                    localUi.update { it.copy(swapFaceUpCard = if (it.swapFaceUpCard == card) null else card) }
                } else {
                    localUi.update { it.copy(swapHandCard = null, swapFaceUpCard = null) }
                    submit(ZpAction.Swap(handCard, card))
                }
            }
            ZpPhase.PLAYING -> if (view.legal.source == CardSource.FACE_UP) toggleSelection(card, state)
            ZpPhase.FINISHED -> Unit
        }
    }

    fun onBlindTap(index: Int) {
        val view = ui.value.view ?: return
        if (view.isMyTurn && index in view.legal.blindIndices) submit(ZpAction.PlayBlind(index))
    }

    private fun toggleSelection(card: Card, state: GameUiState) {
        val view = state.view ?: return
        if (!view.isMyTurn) return
        if (card !in view.legal.playableCards) {
            _messages.tryEmit(GameMessage.NotPlayable)
            return
        }
        val current = state.selected
        when {
            // Tapping a selected card again puts it back; playing is done by swiping or the "Speel" button.
            card in current -> localUi.update { it.copy(selected = current - card) }
            current.isNotEmpty() && view.legal.canPlayMultiple && current.first().rank == card.rank ->
                localUi.update { it.copy(selected = current + card) }
            else -> localUi.update { it.copy(selected = setOf(card)) }
        }
    }

    // ================================================================== swipes

    /**
     * A card was dragged and released. Swiping up (or dropping on the pile) plays it, together with selected
     * cards of the same rank; in the swap phase, dropping a hand card on a face-up card swaps them.
     * Whether a card may be played comes from the engine's legal moves; the host validates again.
     * Returns true when a move was submitted.
     */
    fun onCardDropped(item: DragItem, target: DropTarget?, swipedUp: Boolean): Boolean {
        val view = ui.value.view ?: return false
        if (localUi.value.busy) return false
        return when (view.phase) {
            ZpPhase.SWAPPING -> {
                val face = item as? DragItem.Face ?: return false
                val faceUp = target as? DropTarget.FaceUp ?: return false
                if (!view.legal.canSwap || face.card !in view.myHand) return false
                localUi.update { it.copy(swapHandCard = null) }
                hide(setOf(face.card), view)
                submit(ZpAction.Swap(face.card, faceUp.card))
                true
            }
            ZpPhase.PLAYING -> {
                if (!view.isMyTurn || !(swipedUp || target == DropTarget.Pile)) return false
                when (item) {
                    is DragItem.Face -> {
                        val playable = view.legal.playableCards
                        if (item.card !in playable) {
                            _messages.tryEmit(GameMessage.NotPlayable)
                            return false
                        }
                        if (!view.legal.canPlayMultiple) {
                            hide(setOf(item.card), view)
                            submit(ZpAction.Play(listOf(item.card)), clearSelection = true)
                            return true
                        }
                        val staged = localUi.value.staged
                        if (staged.isNotEmpty() && staged.first().rank != item.card.rank) {
                            _messages.tryEmit(GameMessage.OnlySameRank)
                            return false
                        }
                        val cards = (staged + item.card + item.companions.filter { it.rank == item.card.rank && it in playable })
                            .distinct()
                        // More cards of this rank in reach? Keep the turn open a moment so they can follow.
                        val more = playable.any { it.rank == item.card.rank && it !in cards }
                        if (more) stage(cards) else commit(cards, view)
                        true
                    }
                    is DragItem.Blind -> {
                        if (item.index !in view.legal.blindIndices) return false
                        submit(ZpAction.PlayBlind(item.index))
                        true
                    }
                }
            }
            ZpPhase.FINISHED -> false
        }
    }

    private var stageJob: Job? = null

    private fun stage(cards: List<Card>) {
        localUi.update { it.copy(staged = cards, stagedAtMillis = nowMillis(), selected = emptySet()) }
        stageJob?.cancel()
        stageJob = viewModelScope.launch {
            delay(STAGE_WINDOW_MS)
            commitStaged()
        }
    }

    private fun commit(cards: List<Card>, view: ZpPlayerView) {
        stageJob?.cancel()
        localUi.update { it.copy(staged = emptyList()) }
        hide(cards.toSet(), view)
        submit(ZpAction.Play(cards.sorted()), clearSelection = true)
    }

    /** Plays the cards that are lying on the pile now ("Speel" or after the waiting time). */
    fun commitStaged() {
        val local = localUi.value
        val view = ui.value.view
        if (local.staged.isEmpty() || view == null) return
        // Cards of the same rank that were tapped meanwhile go along.
        val rank = local.staged.first().rank
        val tapped = local.selected.filter { it.rank == rank && it in view.legal.playableCards }
        commit((local.staged + tapped).distinct(), view)
    }

    /** Takes the swiped cards back into the hand. */
    fun cancelStaged() {
        stageJob?.cancel()
        localUi.update { it.copy(staged = emptyList()) }
    }

    /** "Vals!" — accuse the player who laid [playId] (null: the newest play by someone else). */
    fun challenge(playId: Long? = null) {
        cancelStaged()
        submit(ZpAction.Challenge(playId))
    }

    /** Selected cards that would travel along when [card] is dragged. */
    fun companionsFor(card: Card): List<Card> {
        val state = ui.value
        return state.selected.filter { it != card && it.rank == card.rank }
    }

    private fun hide(cards: Set<Card>, view: ZpPlayerView) {
        localUi.update { it.copy(hidden = cards, hiddenAtVersion = view.stateVersion) }
    }

    // ================================================================== actions

    fun play() {
        if (localUi.value.staged.isNotEmpty()) {
            commitStaged()
            return
        }
        val selected = ui.value.selected
        if (selected.isEmpty()) return
        submit(ZpAction.Play(selected.sorted()), clearSelection = true)
    }

    fun pickUp() {
        cancelStaged()
        submit(ZpAction.PickUp, clearSelection = true)
    }

    fun gamble() {
        cancelStaged()
        submit(ZpAction.Gamble, clearSelection = true)
    }

    fun ready() = submit(ZpAction.Ready)

    private fun submit(action: ZpAction, clearSelection: Boolean = false) {
        val port = session.value?.zpPort ?: return
        if (localUi.value.busy || ui.value.botPlaysForMe) return // prevents double submissions from fast taps
        localUi.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = port.submit(action)
            localUi.update {
                it.copy(
                    busy = false,
                    selected = if (clearSelection && result == SubmitResult.Accepted) emptySet() else it.selected,
                    // A refused move: show the swiped cards again.
                    hidden = if (result == SubmitResult.Accepted) it.hidden else emptySet(),
                )
            }
            when (result) {
                SubmitResult.Accepted -> Unit
                is SubmitResult.Rejected -> _messages.tryEmit(GameMessage.Rejected(result.reason))
                is SubmitResult.Failed -> _messages.tryEmit(GameMessage.Rejected(result.reason))
            }
        }
    }

    // ================================================================== session control

    /** Host only: let a bot play for a disconnected player. */
    fun takeOver(seatId: String) {
        val hosting = session.value as? ActiveSession.Hosting ?: return
        viewModelScope.launch { hosting.host.setBotControl(seatId, BotDifficulty.NORMAL) }
    }

    fun reconnect() {
        val joined = session.value as? ActiveSession.Joined ?: return
        viewModelScope.launch { joined.client.reconnect() }
    }

    /** "Opnieuw spelen": local and host only. */
    fun playAgain() {
        viewModelScope.launch {
            val error = when (val active = session.value) {
                is ActiveSession.Local -> container.sessions.restartLocal()
                is ActiveSession.Hosting -> active.host.startGame()
                else -> null
            }
            if (error != null) _messages.tryEmit(GameMessage.Rejected(error))
        }
    }

    /** Host only: back to the lobby to change players or rules. */
    fun returnToLobby() {
        val hosting = session.value as? ActiveSession.Hosting ?: return
        viewModelScope.launch { hosting.host.returnToLobby() }
    }

    /** Give up (see [nl.bluecard.app.session.SessionManager.resign]). */
    fun resign() {
        container.appScope.launch { container.sessions.resign() }
    }

    fun leave() {
        container.appScope.launch { container.sessions.endSession() }
    }
}
