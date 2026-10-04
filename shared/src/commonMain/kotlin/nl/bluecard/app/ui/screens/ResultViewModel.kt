package nl.bluecard.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlinx.coroutines.launch
import nl.bluecard.app.AppContainer
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.session.ViewSummary
import nl.bluecard.multiplayer.session.SessionPhase

data class ResultUiState(
    val hasSession: Boolean = true,
    val kind: SessionKind = SessionKind.LOCAL,
    val summary: ViewSummary? = null,
    val lobbyPhase: SessionPhase? = null,
    val game: GameKind? = null,
    val seats: List<nl.bluecard.multiplayer.session.SeatInfo> = emptyList(),
)

/** The end-of-game screen works for every game: it only needs the standings and the session controls. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModel(private val container: AppContainer) : ViewModel() {

    private val session = container.sessions.active
    private val _messages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 8)

    val ui: StateFlow<ResultUiState> = session.flatMapLatest { active ->
        if (active == null) {
            flowOf(ResultUiState(hasSession = false))
        } else {
            combine(active.port.view, active.port.lobby) { view, lobby ->
                ResultUiState(
                    game = active.game,
                    kind = active.kind,
                    summary = view?.let { active.game.binding.summarizeAny(it) },
                    lobbyPhase = lobby?.phase,
                    seats = lobby?.seats.orEmpty(),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultUiState())

    val messages: Flow<GameMessage> = merge(
        _messages.asSharedFlow(),
        session.flatMapLatest { active -> active?.port?.notices ?: emptyFlow() }.map { GameMessage.Notice(it) },
    )

    /** Another round; against bots it may be another game (same bots). */
    fun playAgain(game: GameKind? = null) {
        viewModelScope.launch {
            val active = container.sessions.active.value
            val error = if (game != null && active is ActiveSession.Local && game != active.game) {
                container.settingsRepository.update { it.copy(game = game) }
                val bots = active.host.lobby.value.seats.count { it.kind == nl.bluecard.multiplayer.session.SeatKind.BOT }
                container.sessions.startLocalGame(game, bots, container.settings.value.defaultDifficulty)
            } else {
                container.sessions.playAgain()
            }
            error?.let { _messages.tryEmit(GameMessage.Rejected(it)) }
        }
    }

    /** Host: back to the lobby with another game for the table. */
    fun lobbyWith(game: GameKind, then: () -> Unit) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(game = game) }
            container.sessions.returnToLobby()
            container.sessions.switchHostGame(game)
            then()
        }
    }

    fun returnToLobby() {
        viewModelScope.launch { container.sessions.returnToLobby() }
    }

    fun leave() {
        container.appScope.launch { container.sessions.endSession() }
    }
}

val ActiveSession.kind: SessionKind
    get() = when (this) {
        is ActiveSession.Local -> SessionKind.LOCAL
        is ActiveSession.Hosting -> SessionKind.HOST
        is ActiveSession.Joined -> SessionKind.CLIENT
    }
