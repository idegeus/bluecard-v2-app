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
import nl.bluecard.app.session.hjPort
import nl.bluecard.app.ui.components.DragItem
import nl.bluecard.app.ui.components.DropTarget
import nl.bluecard.engine.hartenjagen.HjAction
import nl.bluecard.engine.hartenjagen.HjPlayerView
import nl.bluecard.engine.model.Card
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SessionPhase
import nl.bluecard.multiplayer.session.SubmitResult

data class HeartsUiState(
    val hasSession: Boolean = true,
    val kind: SessionKind = SessionKind.LOCAL,
    val view: HjPlayerView? = null,
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
}

private data class HeartsLocal(
    val selected: Set<Card> = emptySet(),
    val busy: Boolean = false,
    val hidden: Set<Card> = emptySet(),
    val hiddenAtVersion: Long = -1,
)

/** Logic of the Hartenjagen table. */
@OptIn(ExperimentalCoroutinesApi::class)
class HeartsGameViewModel(private val container: AppContainer) : ViewModel() {

    private val local = MutableStateFlow(HeartsLocal())
    private val _messages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 16)
    private val session: StateFlow<ActiveSession?> = container.sessions.active

    private val sessionState: Flow<HeartsUiState> = session.flatMapLatest { active ->
        val port = active?.hjPort ?: return@flatMapLatest flowOf(HeartsUiState(hasSession = false))
        combine(port.view, port.lobby, port.connection) { view, lobby, connection ->
            HeartsUiState(kind = active.kind, view = view, seats = lobby?.seats.orEmpty(), connection = connection, lobbyPhase = lobby?.phase)
        }
    }

    val ui: StateFlow<HeartsUiState> = combine(
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeartsUiState())

    val messages: Flow<GameMessage> = merge(
        _messages.asSharedFlow(),
        session.flatMapLatest { active -> active?.port?.notices ?: emptyFlow() }.map { GameMessage.Notice(it) },
    )

    /** Passing: tap to choose cards. Playing: tap to pick a card, tap it again (or swipe it up) to play it. */
    fun onHandCardTap(card: Card) {
        val view = ui.value.view ?: return
        val legal = view.legal
        val current = ui.value.selected
        if (legal.passCount > 0) {
            local.update {
                it.copy(
                    selected = when {
                        card in current -> current - card
                        current.size < legal.passCount -> current + card
                        else -> current - current.first() + card
                    },
                )
            }
            return
        }
        if (!view.isMyTurn) return
        if (card !in legal.playableCards) {
            _messages.tryEmit(GameMessage.NotPlayable)
            return
        }
        if (card in current) play(card) else local.update { it.copy(selected = setOf(card)) }
    }

    fun onCardDropped(item: DragItem, target: DropTarget?, swipedUp: Boolean): Boolean {
        val view = ui.value.view ?: return false
        if (local.value.busy || !view.isMyTurn || !(swipedUp || target == DropTarget.Pile)) return false
        val face = item as? DragItem.Face ?: return false
        if (face.card !in view.legal.playableCards) {
            _messages.tryEmit(GameMessage.NotPlayable)
            return false
        }
        play(face.card)
        return true
    }

    fun playSelected() {
        ui.value.selected.singleOrNull()?.let(::play)
    }

    private fun play(card: Card) {
        val view = ui.value.view ?: return
        local.update { it.copy(hidden = setOf(card), hiddenAtVersion = view.stateVersion) }
        submit(HjAction.Play(card), clearSelection = true)
    }

    fun passSelected() {
        val view = ui.value.view ?: return
        val cards = ui.value.selected.toList()
        if (cards.size != view.legal.passCount) return
        submit(HjAction.PassCards(cards), clearSelection = true)
    }

    private fun submit(action: HjAction, clearSelection: Boolean = false) {
        val port = session.value?.hjPort ?: return
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
