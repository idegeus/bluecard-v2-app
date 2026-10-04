package nl.bluecard.app.session

import nl.bluecard.app.platform.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import nl.bluecard.app.data.SavedGame
import nl.bluecard.app.data.SavedGameRepository
import nl.bluecard.app.data.SettingsRepository
import nl.bluecard.app.settings.AppSettings
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.multiplayer.session.ClientOptions
import nl.bluecard.multiplayer.session.ClientSession
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.HostOptions
import nl.bluecard.multiplayer.session.HostSession
import nl.bluecard.multiplayer.session.MatchLog
import nl.bluecard.multiplayer.session.PlayerPort
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionNotice
import nl.bluecard.multiplayer.session.TableStyle
import nl.bluecard.multiplayer.session.HostSnapshot
import nl.bluecard.multiplayer.session.LostReason
import nl.bluecard.multiplayer.session.SuccessorInfo
import kotlinx.io.IOException
import nl.bluecard.app.platform.GameTransport
import nl.bluecard.app.platform.Platform

/** The game session that is currently running, if any. */
sealed interface ActiveSession {
    val game: GameKind
    val port: PlayerPort<*, *>

    /** Against bots on this phone only. */
    data class Local(val host: AnyHost, override val game: GameKind) : ActiveSession {
        override val port: PlayerPort<*, *> get() = host.hostPort
    }

    /** This phone hosts a Bluetooth game. */
    data class Hosting(val host: AnyHost, override val game: GameKind) : ActiveSession {
        override val port: PlayerPort<*, *> get() = host.hostPort
    }

    /** This phone joined someone else's Bluetooth game. */
    data class Joined(
        val client: AnyClient,
        override val game: GameKind,
        val hostAddress: String,
        val hostLabel: String,
    ) : ActiveSession {
        override val port: PlayerPort<*, *> get() = client
    }
}

/** The port of a Zweeds Pesten session, or null for another game. */
/** This phone/tablet joined as a table display: it shows the table big and never plays. */
val ActiveSession?.isTableDisplay: Boolean get() = this is ActiveSession.Joined && client.asTable

@Suppress("UNCHECKED_CAST")
val ActiveSession.zpPort: ZpPort? get() = if (game == GameKind.ZWEEDS_PESTEN) port as ZpPort else null

/** The port of a Pesten session, or null for another game. */
@Suppress("UNCHECKED_CAST")
val ActiveSession.psPort: PsPort? get() = if (game == GameKind.PESTEN) port as PsPort else null

@Suppress("UNCHECKED_CAST")
val ActiveSession.prPort: PrPort? get() = if (game == GameKind.PRESIDENTEN) port as PrPort else null

@Suppress("UNCHECKED_CAST")
val ActiveSession.hjPort: HjPort? get() = if (game == GameKind.HARTENJAGEN) port as HjPort else null

/**
 * App-wide owner of the active game session. Lives in the Application, so the session survives screen
 * rotation and navigation. Bluetooth sessions additionally run a foreground service so they keep going
 * while the app is in the background.
 */
class SessionManager(
    private val platform: Platform,
    private val appScope: CoroutineScope,
    private val settings: SettingsRepository,
    private val savedGames: SavedGameRepository,
    private val matchLog: MatchLog,
) {
    private val transport: GameTransport? get() = platform.transport

    /** The name this phone announces its table under (kept for reopening the server). */
    private var advertisedName = ""

    private val mutex = Mutex()
    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    private val _hasSavedGame = MutableStateFlow(false)
    val hasSavedGame: StateFlow<Boolean> = _hasSavedGame.asStateFlow()

    private var autosaveJob: Job? = null

    private val _rejoining = MutableStateFlow<String?>(null)

    /** The next join from the join screen is as a table display (chosen on the multiplayer screen). */
    val joinAsTable = MutableStateFlow(false)

    /** The host's name while this phone reconnects because the host switched to another game. */
    val rejoining: StateFlow<String?> = _rejoining.asStateFlow()

    init {
        appScope.launch { _hasSavedGame.value = savedGames.exists() }
        // The host switched the table to another game: reconnect with that game.
        appScope.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            _active.flatMapLatest { session ->
                if (session is ActiveSession.Joined) session.client.notices.map { session to it } else emptyFlow()
            }.collect { (session, notice) ->
                if (notice is SessionNotice.GameSwitched) rejoin(session, notice.gameId)
            }
        }
        // The host went away: the successor takes the table over, the others move to the successor.
        appScope.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            _active.flatMapLatest { session ->
                if (session is ActiveSession.Joined) session.client.connection.map { session to it } else emptyFlow()
            }.collect { (session, status) ->
                val reason = (status as? ConnectionStatus.Lost)?.reason
                if (reason == LostReason.HOST_MOVED || reason == LostReason.CONNECTION_LOST) hostGone(session)
            }
        }
        // The host's table: shuffle ritual and look follow this phone's settings.
        appScope.launch {
            kotlinx.coroutines.flow.combine(_active, settings.settings) { session, prefs -> session to prefs }.collect { (session, prefs) ->
                val host = when (session) {
                    is ActiveSession.Local -> session.host
                    is ActiveSession.Hosting -> session.host
                    else -> null
                } ?: return@collect
                host.shuffleRitual = prefs.shuffleRitual
                if (session is ActiveSession.Hosting) host.setStyle(TableStyle(prefs.cardBackSkin, prefs.tableSkin))
            }
        }
        // When Bluetooth comes back on while hosting (also mid-game), accept (re)connections again.
        transport?.let { radio ->
            appScope.launch {
                radio.ready.collect { ready ->
                    val hosting = _active.value as? ActiveSession.Hosting
                    if (ready && hosting != null && !hosting.host.accepting.value) {
                        platform.log(TAG, "radio back on: reopening server")
                        reopenServer()
                    }
                }
            }
        }
    }

    // ================================================================== local games

    /** Starts a new game against bots. Returns null on success or an engine reason code. */
    suspend fun startLocalGame(game: GameKind, botCount: Int, difficulty: BotDifficulty): String? = mutex.withLock {
        endLocked()
        startLocal(game.binding, botCount, difficulty)
    }

    private suspend fun <C : Any, S : Any, A : Any, V : Any> startLocal(
        binding: GameBinding<C, S, A, V>,
        botCount: Int,
        difficulty: BotDifficulty,
    ): String? {
        val prefs = settings.ensureToken()
        val host = newHostFromSettings(binding, prefs)
        repeat(botCount) { host.addBot(difficulty) }
        // Active before starting: the shuffle (if any) is shown for the active session.
        val session = ActiveSession.Local(host, binding.kind)
        _active.value = session
        val error = host.startGame()
        if (error != null) {
            host.close()
            if (_active.value === session) _active.value = null
            return error
        }
        savedGames.delete()
        startAutosave(binding, host)
        return null
    }

    /** Resumes the saved local game. Returns false when there is none or it cannot be read. */
    suspend fun resumeLocalGame(): Boolean = mutex.withLock {
        val saved = savedGames.load()
        val game = GameKind.fromId(saved?.gameId)
        if (saved == null || game == null) {
            _hasSavedGame.value = false
            return@withLock false
        }
        endLocked()
        resume(game.binding, saved)
    }

    private suspend fun <C : Any, S : Any, A : Any, V : Any> resume(binding: GameBinding<C, S, A, V>, saved: SavedGame): Boolean {
        val state = try {
            binding.decodeState(saved.state)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException as well.
            platform.log(TAG, "saved game unreadable", e)
            savedGames.delete()
            _hasSavedGame.value = false
            return false
        }
        val prefs = settings.ensureToken()
        val host = newHost(binding, prefs, binding.module.defaultConfig())
        host.restore(state, saved.seats)
        _active.value = ActiveSession.Local(host, binding.kind)
        startAutosave(binding, host)
        return true
    }

    /** Local game finished and the player wants another round with the same bots and rules. */
    suspend fun restartLocal(): String? {
        val session = _active.value as? ActiveSession.Local ?: return "NO_GAME"
        return session.host.startGame()
    }

    @OptIn(FlowPreview::class)
    private fun <C : Any, S : Any, A : Any, V : Any> startAutosave(binding: GameBinding<C, S, A, V>, host: HostSession<C, S, A, V>) {
        autosaveJob?.cancel()
        autosaveJob = appScope.launch {
            host.gameHost.state.filterNotNull().debounce(AUTOSAVE_DEBOUNCE_MS).collect { state ->
                if (binding.module.result(state) != null) {
                    savedGames.delete()
                    _hasSavedGame.value = false
                } else {
                    save(binding, state, host.lobby.value.seats)
                }
            }
        }
    }

    private suspend fun <S : Any> save(binding: GameBinding<*, S, *, *>, state: S, seats: List<SeatInfo>) {
        savedGames.save(
            SavedGame(gameId = binding.kind.id, state = binding.encodeState(state), seats = seats, savedAtMillis = nowMillis()),
        )
        _hasSavedGame.value = true
    }

    // ================================================================== controls shared by all game screens

    /** "Opnieuw spelen" (local game or host). Returns an engine reason code when it cannot start. */
    suspend fun playAgain(): String? = when (val active = _active.value) {
        is ActiveSession.Local -> restartLocal()
        is ActiveSession.Hosting -> active.host.startGame()
        else -> null
    }

    /**
     * Giving up. Against bots the game ends with you last; as host a bot takes over your seat and the others play
     * on; as client you leave and the host lets a bot take over.
     */
    suspend fun resign() {
        when (val active = _active.value) {
            is ActiveSession.Local -> active.host.resignHost(endGame = true)
            is ActiveSession.Hosting -> active.host.resignHost(endGame = false)
            is ActiveSession.Joined -> endSession()
            null -> Unit
        }
    }

    /** Host only: back to the lobby to change players or rules. */
    suspend fun returnToLobby() {
        (_active.value as? ActiveSession.Hosting)?.host?.returnToLobby()
    }

    /** Host only: let a bot play for a disconnected player. */
    suspend fun takeOver(seatId: String) {
        (_active.value as? ActiveSession.Hosting)?.host?.setBotControl(seatId, BotDifficulty.NORMAL)
    }

    suspend fun reconnect() {
        (_active.value as? ActiveSession.Joined)?.client?.reconnect()
    }

    // ================================================================== Bluetooth hosting

    /**
     * Opens a table: a hosted game that accepts connections from phones nearby. When the radio cannot accept them
     * (Bluetooth off, no permission, no transport) the table still opens, offline: bots can always join, and the
     * server opens as soon as Bluetooth is back (or on [reopenServer]).
     */
    suspend fun startHosting(game: GameKind): Result<AnyHost> = mutex.withLock {
        endLocked()
        val prefs = settings.ensureToken()
        val host = newHostFromSettings(game.binding, prefs)
        advertisedName = prefs.displayName
        _active.value = ActiveSession.Hosting(host, game)
        openServer(host, game)
        platform.keepSessionAlive(active = true, hosting = true)
        Result.success(host)
    }

    /** Re-opens the server socket after it failed (e.g. Bluetooth was toggled or a permission granted). */
    fun reopenServer(): Boolean {
        val hosting = _active.value as? ActiveSession.Hosting ?: return false
        return openServer(hosting.host, hosting.game)
    }

    private fun openServer(host: AnyHost, game: GameKind): Boolean = try {
        host.startAccepting(requireTransport().openServer(advertisedName, game.id))
        true
    } catch (e: IOException) {
        // Bluetooth off, no permission (the transport reports both as IOException) or no transport at all.
        platform.log(TAG, "cannot open server", e)
        false
    }

    private fun requireTransport(): GameTransport = transport ?: throw IOException("no multiplayer transport on this device")

    // ================================================================== Bluetooth joining

    /**
     * Connects to a host running [game] (known from probing the host; Zweeds Pesten when unknown). On success
     * the session becomes active; otherwise the failure status is returned.
     */
    suspend fun join(address: String, hostLabel: String, game: GameKind, asTable: Boolean = joinAsTable.value): ConnectionStatus = mutex.withLock {
        endLocked()
        val prefs = settings.ensureToken()
        val client = newClient(game, address, prefs, asTable)
        val status = client.connect()
        if (status == ConnectionStatus.Connected) {
            _active.value = ActiveSession.Joined(client, game, address, hostLabel)
            platform.keepSessionAlive(active = true, hosting = false)
        } else {
            client.leave()
        }
        status
    }

    /**
     * Host only, in the lobby or after a game: moves the table to another game. Clients get told to reconnect
     * for it; bots come along.
     */
    suspend fun switchHostGame(game: GameKind): Result<AnyHost> = mutex.withLock {
        val hosting = _active.value as? ActiveSession.Hosting
            ?: return@withLock Result.failure(IllegalStateException("not hosting"))
        if (hosting.game == game) return@withLock Result.success(hosting.host)
        val oldSeats = hosting.host.lobby.value.seats
        val bots = oldSeats.filter { it.kind == SeatKind.BOT }.map { it.difficulty ?: BotDifficulty.NORMAL }
        hosting.host.switchGame(game.id)
        // Give the old server socket a moment to be released before opening the new one.
        delay(SWITCH_PAUSE_MS)
        val prefs = settings.ensureToken()
        val host = newHostFromSettings(game.binding, prefs)
        advertisedName = prefs.displayName
        // Offline (no Bluetooth) the table just stays offline, as when it was opened.
        openServer(host, game)
        bots.forEach { host.addBot(it) }
        restoreSeatOrder(host, seatKeys(oldSeats))
        _active.value = ActiveSession.Hosting(host, game)
        Result.success(host)
    }

    /**
     * After a game switch the players rejoin in whatever order their phones reconnect; put them back in the order the
     * host had set, one adjacent swap per lobby update, until everyone is back (or the window closes).
     */
    private fun restoreSeatOrder(host: AnyHost, order: List<String>) {
        appScope.launch {
            withTimeoutOrNull(SEAT_ORDER_WINDOW_MS) {
                host.lobby.first { lobby ->
                    val keys = seatKeys(lobby.seats)
                    val rank = { key: String -> order.indexOf(key).let { if (it < 0) Int.MAX_VALUE else it } }
                    val inversion = (1 until keys.size).firstOrNull { rank(keys[it]) < rank(keys[it - 1]) }
                    if (inversion != null) host.moveSeat(lobby.seats[inversion].id, -1)
                    inversion == null && keys.containsAll(order)
                }
            }
        }
    }

    /** Identities that survive a reconnect: the host, each phone's device id, and bots by their position. */
    private fun seatKeys(seats: List<SeatInfo>): List<String> {
        var bot = 0
        return seats.map {
            when (it.kind) {
                SeatKind.HOST -> "host"
                SeatKind.BOT -> "bot:${bot++}"
                SeatKind.REMOTE -> it.deviceId?.let { id -> "device:$id" } ?: "name:${it.name}"
            }
        }
    }

    private fun rejoin(old: ActiveSession.Joined, gameId: String) {
        val game = GameKind.fromId(gameId) ?: return
        appScope.launch {
            _rejoining.value = old.hostLabel
            try {
                mutex.withLock { if (_active.value === old) _active.value = null }
                repeat(REJOIN_ATTEMPTS) {
                    delay(REJOIN_DELAY_MS)
                    if (join(old.hostAddress, old.hostLabel, game, old.client.asTable) == ConnectionStatus.Connected) return@launch
                }
                platform.keepSessionAlive(active = false, hosting = false)
            } finally {
                _rejoining.value = null
            }
        }
    }

    // ================================================================== host handover

    private val migrating = mutableSetOf<ActiveSession.Joined>()

    /** The host of [old] is gone: take the table over if this phone is the successor, otherwise follow it there. */
    private fun hostGone(old: ActiveSession.Joined) {
        val next = old.client.successor.value ?: return
        if (!migrating.add(old)) return
        val me = old.client.playerId.value
        appScope.launch {
            try {
                _rejoining.value = next.name
                if (next.seatId == me) {
                    val snapshot = old.client.handover ?: return@launch
                    mutex.withLock {
                        if (_active.value !== old) return@launch
                        takeOver(old.game.binding, old, snapshot, next.seatId)
                    }
                } else {
                    follow(old, next)
                }
            } finally {
                _rejoining.value = null
                migrating.remove(old)
            }
        }
    }

    /** This phone becomes the host of the table described by [snapshot]. */
    private suspend fun <C : Any, S : Any, A : Any, V : Any> takeOver(
        binding: GameBinding<C, S, A, V>,
        old: ActiveSession.Joined,
        snapshot: HostSnapshot,
        mySeatId: String,
    ) {
        val prefs = settings.ensureToken()
        val host = newHost(binding, prefs, binding.module.defaultConfig())
        host.adopt(snapshot, mySeatId)
        val acceptor = try {
            requireTransport().openServer(prefs.displayName.also { advertisedName = it }, binding.kind.id)
        } catch (e: IOException) {
            platform.log(TAG, "cannot open the table after the host left", e)
            host.close()
            return
        }
        host.startAccepting(acceptor)
        old.client.leave()
        _active.value = ActiveSession.Hosting(host, binding.kind)
        platform.keepSessionAlive(active = true, hosting = true)
        platform.log(TAG, "took the table over as $mySeatId")
    }

    /** Connects to the new host; the old session stays on screen until that works. */
    private suspend fun follow(old: ActiveSession.Joined, next: SuccessorInfo) {
        val prefs = settings.ensureToken()
        // Give the new host a moment to open the table.
        delay(FOLLOW_FIRST_DELAY_MS)
        repeat(FOLLOW_ATTEMPTS) {
            if (_active.value !== old) return
            val client = newClient(old.game, next.address, prefs, old.client.asTable)
            if (client.connect() == ConnectionStatus.Connected) {
                mutex.withLock {
                    if (_active.value !== old) {
                        client.leave()
                        return
                    }
                    old.client.leave()
                    _active.value = ActiveSession.Joined(client, old.game, next.address, next.name)
                }
                return
            }
            client.leave()
            delay(FOLLOW_RETRY_MS)
        }
    }

    // ================================================================== ending

    /** Leaves / closes the current session. A local game stays saved so it can be resumed. */
    suspend fun endSession() = mutex.withLock { endLocked() }

    private suspend fun endLocked() {
        autosaveJob?.cancel()
        autosaveJob = null
        when (val session = _active.value) {
            is ActiveSession.Local -> {
                // Save the very latest state; the debounced autosave may not have run yet.
                saveLatest(session.game.binding, session.host)
                session.host.close()
            }
            // Another player takes the table over if there is one; otherwise it closes.
            is ActiveSession.Hosting -> if (!session.host.leaveWithHandover()) session.host.close()
            is ActiveSession.Joined -> session.client.leave()
            null -> Unit
        }
        _active.value = null
        platform.keepSessionAlive(active = false, hosting = false)
    }

    private suspend fun <C : Any, S : Any, A : Any, V : Any> saveLatest(binding: GameBinding<C, S, A, V>, anyHost: AnyHost) {
        @Suppress("UNCHECKED_CAST")
        val host = anyHost as HostSession<C, S, A, V>
        val (state, seats) = host.snapshot() ?: return
        if (binding.module.result(state) == null) save(binding, state, seats)
    }

    private fun newClient(game: GameKind, address: String, prefs: AppSettings, asTable: Boolean = false): AnyClient = ClientSession(
        module = game.binding.module,
        playerName = prefs.displayName,
        playerToken = prefs.playerToken,
        connector = { requireTransport().connect(address) },
        parentScope = appScope,
        options = ClientOptions(appVersion = platform.versionName),
        log = { platform.log(TAG_NET, it) },
        deviceId = prefs.deviceId,
        matchLog = matchLog,
        avatar = prefs.avatar,
        asTable = asTable,
        hostAddress = address,
    )

    /** A host for [binding] with the house rules from the settings. */
    private fun <C : Any, S : Any, A : Any, V : Any> newHostFromSettings(
        binding: GameBinding<C, S, A, V>,
        prefs: AppSettings,
    ): HostSession<C, S, A, V> = newHost(binding, prefs, binding.rulesFrom(prefs))

    private fun <C : Any, S : Any, A : Any, V : Any> newHost(
        binding: GameBinding<C, S, A, V>,
        prefs: AppSettings,
        rules: C,
    ): HostSession<C, S, A, V> =
        HostSession(
            module = binding.module,
            hostName = prefs.displayName,
            initialConfig = rules,
            parentScope = appScope,
            options = HostOptions(botDelayMs = prefs.botSpeed.delayMs),
            log = { platform.log(TAG_NET, it) },
            matchLog = matchLog,
            hostDeviceId = prefs.deviceId,
            hostAvatar = prefs.avatar.ifBlank { null },
            hostToken = prefs.playerToken,
        ).also { it.shuffleRitual = prefs.shuffleRitual }

    private companion object {
        const val TAG = "SessionManager"
        const val TAG_NET = "BlueCardNet"
        const val AUTOSAVE_DEBOUNCE_MS = 300L
        const val SWITCH_PAUSE_MS = 800L
        const val REJOIN_ATTEMPTS = 8
        const val REJOIN_DELAY_MS = 1_500L
        const val SEAT_ORDER_WINDOW_MS = 30_000L
        const val FOLLOW_FIRST_DELAY_MS = 2_500L
        const val FOLLOW_RETRY_MS = 2_000L
        const val FOLLOW_ATTEMPTS = 15
    }
}
