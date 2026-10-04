package nl.bluecard.app.ui.screens

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.bluecard.app.AppContainer
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.session.AnyHost
import nl.bluecard.app.session.GameKind
import nl.bluecard.multiplayer.session.LobbySnapshot
import nl.bluecard.multiplayer.session.SessionNotice
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionPhase
import nl.bluecard.engine.core.BotDifficulty

data class HostLobbyUiState(
    val creating: Boolean = true,
    val serverError: String? = null,
    val lobby: LobbySnapshot? = null,
    val accepting: Boolean = false,
    /** False while the radio (Bluetooth) is switched off. */
    val radioReady: Boolean = true,
    val startError: String? = null,
    val starting: Boolean = false,
    /** The game this table is for; can be changed in the lobby. */
    val game: GameKind = GameKind.ZWEEDS_PESTEN,
    val switching: Boolean = false,
    /** Started as a game against bots only (no one else at the table): it runs as a local, saved game. */
    val localStarted: Boolean = false,
) {
    /** Only you and bots: starting turns this into an ordinary game against the bots (saved, no Bluetooth). */
    val botsOnly: Boolean
        get() = lobby != null && lobby.seats.none { it.kind == SeatKind.REMOTE } && lobby.spectators.isEmpty()
}

class HostLobbyViewModel(private val container: AppContainer) : ViewModel() {

    private val host = MutableStateFlow<AnyHost?>(null)
    private val _ui = MutableStateFlow(HostLobbyUiState())
    val ui: StateFlow<HostLobbyUiState> = _ui.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val notices: Flow<SessionNotice> = host.filterNotNull().flatMapLatest { it.notices }

    init {
        viewModelScope.launch {
            if (container.sessions.active.value !is ActiveSession.Hosting) {
                container.sessions.startHosting(container.settingsRepository.current().game).onFailure { e ->
                    _ui.update { it.copy(creating = false, serverError = e.message ?: e::class.simpleName) }
                    return@launch
                }
            }
            _ui.update { it.copy(creating = false) }
        }
        // Follow the table that is open now (switching the game replaces it).
        viewModelScope.launch {
            container.sessions.active.flatMapLatest { active ->
                if (active is ActiveSession.Hosting) {
                    combine(active.host.lobby, active.host.accepting, radioReady()) { lobby, accepting, ready ->
                        { state: HostLobbyUiState ->
                            state.copy(lobby = lobby, accepting = accepting, radioReady = ready, game = active.game)
                        } to active
                    }
                } else {
                    flowOf(null)
                }
            }.collect { pair ->
                if (pair != null) {
                    host.value = pair.second.host
                    _ui.update(pair.first)
                }
            }
        }
        // The lobby always uses the house rules from the settings (editable via "Regels aanpassen").
        viewModelScope.launch {
            container.sessions.active.flatMapLatest { active ->
                if (active is ActiveSession.Hosting) {
                    container.settings.map { active.game.binding.rulesFrom(it) to it }
                        .distinctUntilChanged { a, b -> a.first == b.first }
                        .map { active to it.second }
                } else {
                    emptyFlow()
                }
            }.collect { (active, settings) ->
                if (active.host.lobby.value.phase != SessionPhase.IN_GAME) active.game.binding.applyRules(active.host, settings)
            }
        }
    }

    /** Moves the table to another game; joined players reconnect for it automatically. */
    fun switchGame(game: GameKind) {
        if (_ui.value.game == game || _ui.value.switching) return
        _ui.update { it.copy(switching = true) }
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(game = game) }
            container.sessions.switchHostGame(game).onFailure { e ->
                _ui.update { it.copy(serverError = e.message ?: e::class.simpleName) }
            }
            _ui.update { it.copy(switching = false) }
        }
    }

    fun addBot() {
        val h = host.value ?: return
        viewModelScope.launch { h.addBot(container.settings.value.defaultDifficulty) }
    }

    /** Moves a player up (-1) or down (+1) in the order of play. */
    fun moveSeat(seatId: String, delta: Int) {
        val h = host.value ?: return
        viewModelScope.launch { h.moveSeat(seatId, delta) }
    }

    fun removeSeat(seatId: String) {
        val h = host.value ?: return
        viewModelScope.launch { h.removeSeat(seatId) }
    }

    fun startGame() {
        val h = host.value ?: return
        val state = _ui.value
        if (state.starting) return
        _ui.update { it.copy(starting = true, startError = null) }
        viewModelScope.launch {
            val error = if (state.botsOnly) {
                // Nobody else at the table: play it as a normal game against the bots (saved, resumable, offline).
                val bots = state.lobby?.seats.orEmpty().count { it.kind == SeatKind.BOT }
                container.sessions.startLocalGame(state.game, bots, container.settings.value.defaultDifficulty)
            } else {
                h.startGame()
            }
            _ui.update { it.copy(starting = false, startError = error, localStarted = error == null && state.botsOnly) }
        }
    }

    /** How well the bots play: for the bots at the table now and the ones added later. */
    fun setBotDifficulty(difficulty: BotDifficulty) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(defaultDifficulty = difficulty) }
            host.value?.setBotDifficulty(difficulty)
        }
    }

    /** Updates the stored house rules; the lobby picks them up and sends them to all players. */
    fun setEnforceRules(enforce: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.update { it.withEnforceRules(_ui.value.game, enforce) }
        }
    }

    fun clearStartError() = _ui.update { it.copy(startError = null) }

    private fun radioReady(): Flow<Boolean> = container.platform.transport?.ready ?: flowOf(true)

    /** Tries again to let other phones in (after Bluetooth was switched on or the permission granted). */
    fun reopenServer() {
        if (container.sessions.reopenServer()) _ui.update { it.copy(serverError = null) }
    }

    /** Ends the hosted session. Runs in the app scope so it completes even when this screen is gone. */
    fun leave() {
        container.appScope.launch { container.sessions.endSession() }
    }
}
