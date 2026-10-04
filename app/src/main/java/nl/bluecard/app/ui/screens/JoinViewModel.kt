package nl.bluecard.app.ui.screens

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import nl.bluecard.app.bluetooth.announcedDevice
import nl.bluecard.app.bluetooth.searchUntilNewPhone
import nl.bluecard.app.bluetooth.SearchEnd
import nl.bluecard.app.platform.nowMillis

import nl.bluecard.app.bluetooth
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.bluecard.app.AppContainer
import nl.bluecard.app.bluetooth.DiscoveryEvent
import nl.bluecard.app.bluetooth.NearbyDevice
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.multiplayer.protocol.NetMessage
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.app.session.GameKind
import nl.bluecard.multiplayer.session.LobbyProbe
import nl.bluecard.multiplayer.session.LobbySnapshot
import nl.bluecard.multiplayer.session.ProbeResult
import nl.bluecard.multiplayer.session.SessionNotice
import java.io.IOException

enum class ProbeState { UNKNOWN, PROBING, GAME, NO_GAME, INCOMPATIBLE }

data class FoundDevice(
    val device: NearbyDevice,
    val probe: ProbeState = ProbeState.UNKNOWN,
    val info: NetMessage.LobbyInfo? = null,
) {
    val label: String get() = info?.hostName ?: device.name ?: device.address
}

enum class ScanPhase { IDLE, SCANNING, PROBING, DONE, FAILED }

data class JoinUiState(
    val scanPhase: ScanPhase = ScanPhase.IDLE,
    val devices: List<FoundDevice> = emptyList(),
    val connectingTo: String? = null,
    val joinError: ConnectionStatus? = null,
    val joined: ActiveSession.Joined? = null,
    val lobby: LobbySnapshot? = null,
    val connection: ConnectionStatus? = null,
    /** After the search, phones without a game are checked again for a while (a table may open any moment). */
    val watching: Boolean = false,
    /** The current search runs from [searchStartedAt] to [searchEndsAt] (wall clock; 0 when not searching). */
    val searchStartedAt: Long = 0L,
    val searchEndsAt: Long = 0L,
) {
    val games: List<FoundDevice> get() = devices.filter { it.probe == ProbeState.GAME }

    /** Phones/computers without a (confirmed) game. BLE gadgets like watches or earbuds are not shown. */
    val others: List<FoundDevice> get() = devices.filter { it.probe != ProbeState.GAME && it.device.canHostGame }
}

/**
 * Finds hosts: classic discovery plus already-paired phones, then asks every candidate for its lobby
 * information (HELLO with intent QUERY). This works even when all phones carry the same Bluetooth name.
 */
class JoinViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(JoinUiState())
    val ui: StateFlow<JoinUiState> = _ui.asStateFlow()
    private var scanJob: Job? = null

    @Volatile
    private var probeInFlight = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val notices: Flow<SessionNotice> = container.sessions.active.flatMapLatest { session ->
        if (session is ActiveSession.Joined) session.client.notices else emptyFlow()
    }

    init {
        viewModelScope.launch {
            container.sessions.active.flatMapLatest { session ->
                if (session is ActiveSession.Joined) {
                    kotlinx.coroutines.flow.combine(session.client.lobby, session.client.connection) { lobby, conn ->
                        Triple(session, lobby, conn)
                    }
                } else {
                    flowOf(Triple(null, null, null))
                }
            }.collect { (session, lobby, connection) ->
                _ui.update { it.copy(joined = session, lobby = lobby, connection = connection) }
            }
        }
        if (container.sessions.active.value !is ActiveSession.Joined) scan()
    }

    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val bonded = container.bluetooth.bondedDevices().map { FoundDevice(it) }
            val started = nowMillis()
            val endsAt = started + SEARCH_WINDOW_MS
            _ui.update {
                it.copy(scanPhase = ScanPhase.SCANNING, devices = bonded, joinError = null, searchStartedAt = started, searchEndsAt = endsAt)
            }
            // Tables that announce themselves with their address are checked the moment they are seen (also while
            // searching: the check stops the search for a moment).
            val announced = launch {
                container.bluetooth.announcedTables().collect { beacons ->
                    val fresh = beacons.mapNotNull { beacon ->
                        val address = beacon.address ?: return@mapNotNull null
                        val known = _ui.value.devices.firstOrNull { it.device.address == address }
                        if (known != null && known.probe != ProbeState.UNKNOWN) return@mapNotNull null
                        known ?: FoundDevice(announcedDevice(address, beacon.hostName))
                    }
                    if (fresh.isEmpty()) return@collect
                    _ui.update { state -> state.copy(devices = state.devices.filter { d -> fresh.none { it.device.address == d.device.address } } + fresh) }
                    container.bluetooth.cancelDiscovery()
                    probe(fresh)
                }
            }
            try {
                // A Bluetooth search drowns out the announcements, so give those a moment first: a host that
                // announces its address is usually seen (and checked) within a second or two.
                withTimeoutOrNull(ANNOUNCE_HEAD_START_MS) { _ui.first { state -> state.games.isNotEmpty() } }
                if (_ui.value.games.isNotEmpty()) {
                    _ui.update { it.copy(scanPhase = ScanPhase.DONE) }
                    return@launch
                }
                // Search and check in turns: the search stops at each new phone, which is checked at once.
                var end: SearchEnd
                do {
                    end = discover()
                    if (end == SearchEnd.FAILED && _ui.value.devices.isEmpty()) {
                        _ui.update { it.copy(scanPhase = ScanPhase.FAILED) }
                        return@launch
                    }
                    _ui.update { it.copy(scanPhase = ScanPhase.PROBING) }
                    probe(_ui.value.devices.filter { it.device.canHostGame && it.probe == ProbeState.UNKNOWN })
                    if (_ui.value.games.isNotEmpty()) break
                    _ui.update { it.copy(scanPhase = ScanPhase.SCANNING) }
                } while (end == SearchEnd.STOPPED_AT_NEW_PHONE && nowMillis() < endsAt)
                // Someone may open a table just after we looked (or a first attempt failed): keep looking until the
                // search window is over, quietly, until a table shows up or we connect.
                _ui.update { it.copy(scanPhase = ScanPhase.DONE, watching = true) }
                var round = 0
                while (nowMillis() + RECHECK_INTERVAL_MS < endsAt) {
                    if (_ui.value.games.isNotEmpty()) return@launch
                    delay(RECHECK_INTERVAL_MS)
                    // A phone that just opened a table becomes visible: search again now and then.
                    if (round++ % REDISCOVER_EVERY == REDISCOVER_EVERY - 1) {
                        discover()
                        probe(_ui.value.devices.filter { it.device.canHostGame && it.probe == ProbeState.UNKNOWN })
                    }
                    probe(_ui.value.devices.filter { it.device.canHostGame && it.device.isPhone && it.probe == ProbeState.NO_GAME })
                }
            } finally {
                announced.cancel()
                _ui.update { it.copy(watching = false, searchStartedAt = 0L, searchEndsAt = 0L) }
            }
        }
    }

    /**
     * Runs a Bluetooth search and merges what it finds into the device list; it stops at the first new phone so that
     * one is checked straight away (see [searchUntilNewPhone]).
     */
    private suspend fun discover(): SearchEnd = container.bluetooth.searchUntilNewPhone(
        onFound = { device ->
            _ui.update { state ->
                if (state.devices.any { it.device.address == device.address }) {
                    state.copy(
                        devices = state.devices.map {
                            if (it.device.address == device.address) {
                                it.copy(device = device.copy(name = device.name ?: it.device.name, bonded = it.device.bonded || device.bonded))
                            } else {
                                it
                            }
                        },
                    )
                } else {
                    state.copy(devices = state.devices + FoundDevice(device))
                }
            }
        },
        isNew = { device -> _ui.value.devices.none { it.device.address == device.address && it.probe != ProbeState.UNKNOWN } },
    )

    /** One check at a time: two Bluetooth connections being set up at once make both fail. */
    private val probeLock = Mutex()

    private suspend fun probe(devices: List<FoundDevice>) = probeLock.withLock {
        val settings = container.settingsRepository.ensureToken()
        // Phones in range first; paired-only devices may be far away and take a page timeout each.
        val order = devices
            .sortedWith(compareBy({ !it.device.isPhone }, { it.device.bonded }))
            .map { it.device.address }
        for (address in order) {
            // Found meanwhile by the other check (announcement or search): no need to ask again.
            if (_ui.value.devices.any { it.device.address == address && it.probe == ProbeState.GAME }) continue
            setProbe(address, ProbeState.PROBING, null)
            probeInFlight = true
            val result = try {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    try {
                        val link = container.bluetooth.connect(address)
                        LobbyProbe.query(link, settings.displayName, settings.playerToken, hostAddress = address)
                    } catch (e: IOException) {
                        ProbeResult.NoGame(e.message ?: "io")
                    }
                } ?: ProbeResult.NoGame("timeout")
            } finally {
                probeInFlight = false
            }
            Log.d(TAG, "probe $address -> $result")
            // An aborted connection attempt keeps the Bluetooth controller busy for a moment; without a pause
            // the next attempts fail instantly ("read failed, socket might closed").
            if (result !is ProbeResult.Found) delay(PROBE_PAUSE_MS)
            when (result) {
                is ProbeResult.Found -> setProbe(address, ProbeState.GAME, result.info)
                ProbeResult.Incompatible -> setProbe(address, ProbeState.INCOMPATIBLE, null)
                is ProbeResult.NoGame -> setProbe(address, ProbeState.NO_GAME, null)
            }
        }
    }

    private fun setProbe(address: String, state: ProbeState, info: NetMessage.LobbyInfo?) {
        _ui.update { ui ->
            ui.copy(devices = ui.devices.map { if (it.device.address == address) it.copy(probe = state, info = info) else it })
        }
    }

    fun connect(device: FoundDevice) {
        if (_ui.value.connectingTo != null) return
        val interruptedProbe = probeInFlight
        scanJob?.cancel()
        container.bluetooth.cancelDiscovery()
        _ui.update { it.copy(connectingTo = device.label, joinError = null, scanPhase = if (it.scanPhase == ScanPhase.DONE) it.scanPhase else ScanPhase.IDLE) }
        viewModelScope.launch {
            // An aborted probe keeps the Bluetooth controller busy for a moment (see probe()).
            if (interruptedProbe) delay(PROBE_PAUSE_MS)
            // The client must speak the host's game; ask for it first when we don't know it yet.
            val game = GameKind.fromId(device.info?.gameId) ?: probeGame(device.device.address) ?: GameKind.ZWEEDS_PESTEN
            var status = container.sessions.join(device.device.address, device.label, game)
            // A first connect right after scanning or probing sometimes fails while the radio settles; try once more.
            if (status is ConnectionStatus.Lost) {
                delay(PROBE_PAUSE_MS)
                status = container.sessions.join(device.device.address, device.label, game)
            }
            _ui.update {
                it.copy(connectingTo = null, joinError = if (status == ConnectionStatus.Connected) null else status)
            }
        }
    }

    private suspend fun probeGame(address: String): GameKind? {
        val settings = container.settingsRepository.ensureToken()
        val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            try {
                LobbyProbe.query(container.bluetooth.connect(address), settings.displayName, settings.playerToken, hostAddress = address)
            } catch (e: IOException) {
                null
            }
        }
        delay(PROBE_PAUSE_MS)
        return (result as? ProbeResult.Found)?.info?.gameId?.let(GameKind::fromId)
    }

    fun reconnect() {
        val joined = _ui.value.joined ?: return
        viewModelScope.launch { joined.client.reconnect() }
    }

    fun clearError() = _ui.update { it.copy(joinError = null) }

    /** Leaves the joined game (if any). Runs in the app scope so it completes after the screen is gone. */
    fun leave() {
        scanJob?.cancel()
        container.bluetooth.cancelDiscovery()
        container.appScope.launch {
            if (container.sessions.active.value is ActiveSession.Joined) container.sessions.endSession()
        }
    }

    override fun onCleared() {
        container.bluetooth.cancelDiscovery()
    }

    private companion object {
        const val TAG = "JoinViewModel"
        const val PROBE_TIMEOUT_MS = 9_000L
        const val PROBE_PAUSE_MS = 1_200L
        const val RECHECK_INTERVAL_MS = 5_000L
        /** How long one search lasts: discovery, checking the phones found, then watching for new tables. */
        const val SEARCH_WINDOW_MS = 90_000L

        /** How long the BLE announcements get before the (radio-hogging) Bluetooth search starts. */
        const val ANNOUNCE_HEAD_START_MS = 3_000L
        const val REDISCOVER_EVERY = 3
    }
}
