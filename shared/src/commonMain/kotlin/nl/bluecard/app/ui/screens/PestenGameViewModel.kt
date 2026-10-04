package nl.bluecard.app.ui.screens

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
import kotlinx.coroutines.launch
import nl.bluecard.app.AppContainer
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.session.psPort
import nl.bluecard.app.ui.components.DragItem
import nl.bluecard.app.ui.components.DropTarget
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit
import nl.bluecard.engine.pesten.PsAction
import nl.bluecard.engine.pesten.PsPlayerView
import nl.bluecard.engine.pesten.PsRules
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SessionPhase
import nl.bluecard.multiplayer.session.SubmitResult

data class PestenUiState(
    val hasSession: Boolean = true,
    val kind: SessionKind = SessionKind.LOCAL,
    val view: PsPlayerView? = null,
    val seats: List<SeatInfo> = emptyList(),
    val connection: ConnectionStatus = ConnectionStatus.Local,
    val lobbyPhase: SessionPhase? = null,
    /** Cards selected to be played (several only when allowed and of the same value). */
    val selected: Set<Card> = emptySet(),
    /** A jack (or several) waiting for the player to choose the suit. */
    val choosingSuitFor: List<Card> = emptyList(),
    val busy: Boolean = false,
    /** Cards just swiped away, kept invisible until the new state arrives. */
    val hiddenCards: Set<Card> = emptySet(),
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

private data class PestenLocal(
    val selected: Set<Card> = emptySet(),
    val choosingSuitFor: List<Card> = emptyList(),
    val busy: Boolean = false,
    val hidden: Set<Card> = emptySet(),
    val hiddenAtVersion: Long = -1,
)

/**
 * Logic of the Pesten table. Like the Zweeds Pesten screen it never decides what is allowed: it offers what
 * the engine listed in [PsPlayerView.legal] and the host's engine validates every action again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PestenGameViewModel(private val container: AppContainer) : ViewModel() {

    private val local = MutableStateFlow(PestenLocal())
    private val _messages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 16)
    private val session: StateFlow<ActiveSession?> = container.sessions.active

    private val sessionState: Flow<PestenUiState> = session.flatMapLatest { active ->
        val port = active?.psPort ?: return@flatMapLatest flowOf(PestenUiState(hasSession = false))
        combine(port.view, port.lobby, port.connection) { view, lobby, connection ->
            PestenUiState(
                kind = active.kind,
                view = view,
                seats = lobby?.seats.orEmpty(),
                connection = connection,
                lobbyPhase = lobby?.phase,
            )
        }
    }

    val ui: StateFlow<PestenUiState> = combine(
        sessionState,
        local,
        container.settings,
        container.platform.transport?.ready ?: flowOf(true),
    ) { base, l, settings, radioReady ->
        val view = base.view
        val selectable = view?.legal?.playableCards?.toSet().orEmpty()
        base.copy(
            selected = l.selected.filter { it in selectable }.toSet(),
            choosingSuitFor = l.choosingSuitFor.filter { it in selectable },
            busy = l.busy,
            hiddenCards = if (view != null && view.stateVersion == l.hiddenAtVersion) l.hidden else emptySet(),
            bluetoothOff = base.kind != SessionKind.LOCAL && !radioReady,
            showHints = settings.showHints,
            keepScreenOn = settings.keepScreenOn,
            botPlaysForMe = view != null && base.seats.firstOrNull { it.id == view.viewerId }?.botControlled == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PestenUiState())

    val messages: Flow<GameMessage> = merge(
        _messages.asSharedFlow(),
        session.flatMapLatest { active -> active?.port?.notices ?: emptyFlow() }.map { GameMessage.Notice(it) },
    )

    // ================================================================== cards

    fun onHandCardTap(card: Card) {
        val state = ui.value
        val view = state.view ?: return
        // The winner of the last round gives this card to the loser.
        if (view.legal.giveCards.isNotEmpty()) {
            if (card in view.legal.giveCards) submit(PsAction.GiveCard(card))
            return
        }
        if (!view.isMyTurn) return
        if (card !in view.legal.playableCards) {
            _messages.tryEmit(GameMessage.NotPlayable)
            return
        }
        val current = state.selected
        local.update {
            it.copy(
                selected = when {
                    card in current -> current - card
                    current.isNotEmpty() && view.legal.canPlayMultiple && current.first().rank == card.rank -> current + card
                    else -> setOf(card)
                },
            )
        }
    }

    /** Swiping a card up (or dropping it on the pile) plays it, with selected cards of the same value. */
    fun onCardDropped(item: DragItem, target: DropTarget?, swipedUp: Boolean): Boolean {
        val view = ui.value.view ?: return false
        if (local.value.busy || !view.isMyTurn || !(swipedUp || target == DropTarget.Pile)) return false
        val face = item as? DragItem.Face ?: return false
        if (face.card !in view.legal.playableCards) {
            _messages.tryEmit(GameMessage.NotPlayable)
            return false
        }
        val cards = (listOf(face.card) + face.companions.filter { it.rank == face.card.rank && view.legal.canPlayMultiple })
            .filter { it in view.legal.playableCards }
            .distinct()
        return playCards(cards, view)
    }

    /** "Speel" with the selected cards. */
    fun play() {
        val view = ui.value.view ?: return
        val selected = ui.value.selected.toList()
        if (selected.isNotEmpty()) playCards(selected, view)
    }

    private fun playCards(cards: List<Card>, view: PsPlayerView): Boolean {
        if (PsRules.needsSuit(cards.first().rank, view.rules)) {
            // A jack: first ask which suit has to follow; the card waits on the pile meanwhile.
            local.update { it.copy(choosingSuitFor = cards, hidden = cards.toSet(), hiddenAtVersion = view.stateVersion) }
            return true
        }
        local.update { it.copy(hidden = cards.toSet(), hiddenAtVersion = view.stateVersion) }
        submit(PsAction.Play(cards.sorted()), clearSelection = true)
        return true
    }

    fun chooseSuit(suit: Suit) {
        val cards = local.value.choosingSuitFor
        if (cards.isEmpty()) return
        local.update { it.copy(choosingSuitFor = emptyList()) }
        submit(PsAction.Play(cards.sorted(), suit), clearSelection = true)
    }

    fun cancelSuitChoice() {
        local.update { it.copy(choosingSuitFor = emptyList(), hidden = emptySet()) }
    }

    /** Selected cards that travel along when [card] is dragged. */
    fun companionsFor(card: Card): List<Card> = ui.value.selected.filter { it != card && it.rank == card.rank }

    // ================================================================== actions

    fun draw() = submit(PsAction.Draw, clearSelection = true)

    fun pass() = submit(PsAction.Pass, clearSelection = true)

    fun callLastCard() = submit(PsAction.CallLastCard)

    fun catchLastCard() = submit(PsAction.CatchLastCard)

    /** "Vals!" — accuse the player who laid [playId] (null: the newest play by someone else). */
    fun challenge(playId: Long? = null) = submit(PsAction.Challenge(playId))

    private fun submit(action: PsAction, clearSelection: Boolean = false) {
        val port = session.value?.psPort ?: return
        if (local.value.busy || ui.value.botPlaysForMe) return // prevents double submissions from fast taps
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = port.submit(action)
            local.update {
                it.copy(
                    busy = false,
                    selected = if (clearSelection && result == SubmitResult.Accepted) emptySet() else it.selected,
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

    fun takeOver(seatId: String) {
        viewModelScope.launch { container.sessions.takeOver(seatId) }
    }

    fun reconnect() {
        viewModelScope.launch { container.sessions.reconnect() }
    }

    /** Give up (see [nl.bluecard.app.session.SessionManager.resign]). */
    fun resign() {
        container.appScope.launch { container.sessions.resign() }
    }

    fun leave() {
        container.appScope.launch { container.sessions.endSession() }
    }
}
