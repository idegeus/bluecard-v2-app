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
import nl.bluecard.app.session.prPort
import nl.bluecard.app.ui.components.DragItem
import nl.bluecard.app.ui.components.DropTarget
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.presidenten.PrAction
import nl.bluecard.engine.presidenten.PrPlayerView
import nl.bluecard.engine.presidenten.PrRules
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SessionPhase
import nl.bluecard.multiplayer.session.SubmitResult

data class PresidentUiState(
    val hasSession: Boolean = true,
    val kind: SessionKind = SessionKind.LOCAL,
    val view: PrPlayerView? = null,
    val seats: List<SeatInfo> = emptyList(),
    val connection: ConnectionStatus = ConnectionStatus.Local,
    val lobbyPhase: SessionPhase? = null,
    val selected: Set<Card> = emptySet(),
    val busy: Boolean = false,
    val hiddenCards: Set<Card> = emptySet(),
    val bluetoothOff: Boolean = false,
    val showHints: Boolean = true,
    val keepScreenOn: Boolean = true,
    val botPlaysForMe: Boolean = false,
) {
    fun nameOf(playerId: String): String =
        view?.player(playerId)?.name ?: seats.firstOrNull { it.id == playerId }?.name ?: playerId

    fun seat(playerId: String): SeatInfo? = seats.firstOrNull { it.id == playerId }

    /** The selection forms a set that may be laid now. */
    val selectionPlayable: Boolean
        get() {
            val v = view ?: return false
            return v.isMyTurn && selected.isNotEmpty() && PrRules.whyNot(selected.toList(), v.top, v.rules) == null
        }
}

private data class PresidentLocal(
    val selected: Set<Card> = emptySet(),
    val busy: Boolean = false,
    val hidden: Set<Card> = emptySet(),
    val hiddenAtVersion: Long = -1,
)

/** Logic of the Presidenten table. Only offers what the engine listed; the host validates every action again. */
@OptIn(ExperimentalCoroutinesApi::class)
class PresidentGameViewModel(private val container: AppContainer) : ViewModel() {

    private val local = MutableStateFlow(PresidentLocal())
    private val _messages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 16)
    private val session: StateFlow<ActiveSession?> = container.sessions.active

    private val sessionState: Flow<PresidentUiState> = session.flatMapLatest { active ->
        val port = active?.prPort ?: return@flatMapLatest flowOf(PresidentUiState(hasSession = false))
        combine(port.view, port.lobby, port.connection) { view, lobby, connection ->
            PresidentUiState(kind = active.kind, view = view, seats = lobby?.seats.orEmpty(), connection = connection, lobbyPhase = lobby?.phase)
        }
    }

    val ui: StateFlow<PresidentUiState> = combine(
        sessionState,
        local,
        container.settings,
        container.platform.transport?.ready ?: flowOf(true),
    ) { base, l, settings, radioReady ->
        val view = base.view
        val hand = view?.myHand?.toSet().orEmpty()
        base.copy(
            selected = l.selected.filter { it in hand }.toSet(),
            busy = l.busy,
            hiddenCards = if (view != null && view.stateVersion == l.hiddenAtVersion) l.hidden else emptySet(),
            bluetoothOff = base.kind != SessionKind.LOCAL && !radioReady,
            showHints = settings.showHints,
            keepScreenOn = settings.keepScreenOn,
            botPlaysForMe = view != null && base.seats.firstOrNull { it.id == view.viewerId }?.botControlled == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresidentUiState())

    val messages: Flow<GameMessage> = merge(
        _messages.asSharedFlow(),
        session.flatMapLatest { active -> active?.port?.notices ?: emptyFlow() }.map { GameMessage.Notice(it) },
    )

    /**
     * Tapping a card selects a whole set at once: the cards of that value needed now (when following) or all of
     * them (when leading). Jokers join the set that is selected. Tapping a selected card takes it out again.
     */
    fun onHandCardTap(card: Card) {
        val view = ui.value.view ?: return
        val legal = view.legal
        val current = ui.value.selected
        if (legal.giveCount > 0) {
            local.update {
                it.copy(
                    selected = when {
                        card in current -> current - card
                        current.size < legal.giveCount -> current + card
                        else -> current - current.first() + card
                    },
                )
            }
            return
        }
        if (!view.isMyTurn) return
        if (card in current) {
            local.update { it.copy(selected = current - card) }
            return
        }
        if (card !in legal.playableCards && !card.rank.isJoker) {
            // Higher but not enough of them, or simply too low.
            val top = view.top
            val higher = top != null && PrRules.strength(card, view.rules) > PrRules.strength(PrRules.setRank(top.cards)!!, view.rules)
            _messages.tryEmit(if (higher) GameMessage.Rejected("WRONG_COUNT") else GameMessage.NotPlayable)
            return
        }
        val hand = view.myHand
        val need = legal.requiredCount
        val selection: Set<Card> = if (card.rank.isJoker && current.isNotEmpty()) {
            current + card
        } else if (card.rank.isJoker) {
            val jokers = hand.filter { it.rank.isJoker }
            (if (need != null) jokers.take(need) else listOf(card)).toSet()
        } else {
            val same = listOf(card) + hand.filter { it.rank == card.rank && it != card }
            if (need == null) {
                same.toSet()
            } else {
                val natural = same.take(need)
                val jokers = hand.filter { it.rank.isJoker }.take(need - natural.size)
                (natural + jokers).toSet()
            }
        }
        local.update { it.copy(selected = selection) }
    }

    /** Swiping a card up plays it together with the rest of the selection. */
    fun onCardDropped(item: DragItem, target: DropTarget?, swipedUp: Boolean): Boolean {
        val view = ui.value.view ?: return false
        if (local.value.busy || !view.isMyTurn || !(swipedUp || target == DropTarget.Pile)) return false
        val face = item as? DragItem.Face ?: return false
        if (face.card !in ui.value.selected) onHandCardTap(face.card)
        val cards = ui.value.selected.toList()
        if (cards.isEmpty()) return false
        play(cards)
        return true
    }

    fun companionsFor(card: Card): List<Card> = ui.value.selected.filter { it != card }

    fun playSelected() {
        val cards = ui.value.selected.toList()
        if (cards.isNotEmpty()) play(cards)
    }

    private fun play(cards: List<Card>) {
        val view = ui.value.view ?: return
        local.update { it.copy(hidden = cards.toSet(), hiddenAtVersion = view.stateVersion) }
        submit(PrAction.Play(cards), clearSelection = true)
    }

    fun pass() = submit(PrAction.Pass, clearSelection = true)

    fun giveSelected() {
        val view = ui.value.view ?: return
        val cards = ui.value.selected.toList()
        if (cards.size != view.legal.giveCount) return
        submit(PrAction.GiveCards(cards), clearSelection = true)
    }

    private fun submit(action: PrAction, clearSelection: Boolean = false) {
        val port = session.value?.prPort ?: return
        if (local.value.busy || ui.value.botPlaysForMe) return
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

    fun takeOver(seatId: String) {
        viewModelScope.launch { container.sessions.takeOver(seatId) }
    }

    fun reconnect() {
        viewModelScope.launch { container.sessions.reconnect() }
    }

    fun resign() {
        container.appScope.launch { container.sessions.resign() }
    }

    fun leave() {
        container.appScope.launch { container.sessions.endSession() }
    }
}
