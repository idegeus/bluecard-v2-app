package nl.bluecard.multiplayer.session

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.core.GameModule
import nl.bluecard.engine.model.PlayerInfo
import nl.bluecard.multiplayer.protocol.DecodeResult
import nl.bluecard.multiplayer.protocol.DisconnectReason
import nl.bluecard.multiplayer.protocol.ErrorCode
import nl.bluecard.multiplayer.protocol.HelloIntent
import nl.bluecard.multiplayer.protocol.JoinRejectReason
import nl.bluecard.multiplayer.protocol.NetMessage
import nl.bluecard.multiplayer.protocol.ProtocolCodec
import nl.bluecard.multiplayer.protocol.ProtocolJson
import nl.bluecard.multiplayer.protocol.ProtocolVersion
import nl.bluecard.multiplayer.transport.Link
import nl.bluecard.multiplayer.transport.LinkAcceptor
import kotlinx.io.IOException
import kotlin.random.Random

data class HostOptions(
    /** Pause before a bot moves, so humans can follow what happens. */
    val botDelayMs: Long = 1_250,
    val heartbeatIntervalMs: Long = 4_000,
    /** A connection is considered dead after this many intervals without any incoming message. */
    val heartbeatMissedLimit: Int = 4,
    /** A disconnected player is replaced by a bot after this delay (0 = never automatically). */
    val autoTakeoverMs: Long = 60_000,
    val maxBadMessages: Int = 20,
    val botNames: List<String> = listOf("Bot Bas", "Bot Fien", "Bot Joep", "Bot Noor", "Bot Sem", "Bot Lot"),
    /** How long a bot (or a phone that went away) "shuffles" before the cards are dealt. */
    val botShuffleMs: Long = 2_500,
    /** The deal goes ahead anyway when the shuffler has not finished after this long. */
    val shuffleTimeoutMs: Long = 30_000,
    val maxSpectators: Int = 4,
)

/**
 * Host side of a game session: owns the lobby, the authoritative [GameHost] and all client connections.
 * A local game against bots is simply a HostSession that never accepts connections.
 */
@OptIn(ExperimentalTime::class)
class HostSession<C : Any, S : Any, A : Any, V : Any>(
    val module: GameModule<C, S, A, V>,
    hostName: String,
    initialConfig: C,
    parentScope: CoroutineScope,
    private val options: HostOptions = HostOptions(),
    private val seedSource: () -> Long = { Random.nextLong() },
    private val log: (String) -> Unit = {},
    /** Where finished games are recorded and exchanged with joining phones. */
    private val matchLog: MatchLog = MatchLog.NONE,
    /** Public id of the host's phone. */
    private val hostDeviceId: String? = null,
    hostAvatar: String? = null,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** The host's own player token, so the host can come back as a player after handing the table over. */
    private val hostToken: String? = null,
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val codec = ProtocolCodec()
    private val json = ProtocolJson.json
    private val lock = Mutex()

    val gameHost = GameHost(module, scope, { options.botDelayMs }, log = log, clock = clock)

    private var config: C = initialConfig
    private val seats = mutableListOf(
        SeatInfo(HOST_SEAT_ID, hostName.ifBlank { "Host" }, SeatKind.HOST, deviceId = hostDeviceId, avatar = hostAvatar?.takeIf { it.length <= MAX_AVATAR_LENGTH }),
    )
    private var gameStartedAt = 0L

    /** The host's seat: "host", or the seat of the player who took the table over. */
    @Volatile private var hostSeatId = HOST_SEAT_ID
    private val hostId = MutableStateFlow<String?>(HOST_SEAT_ID)

    /** Who takes over if this host goes away, as last announced. */
    private var successor: SuccessorInfo? = null

    /** Joined while a game was running (or the table was full); they get a seat in the next round. */
    private val spectators = mutableListOf<SeatInfo>()

    /** The host's table look, shared with everybody. */
    private var style: TableStyle? = null

    /** When true, every round starts with one player shaking their phone to shuffle (rotating around the table). */
    @Volatile var shuffleRitual: Boolean = false
    private var shuffleTurn = 0
    private var shufflerId: String? = null
    private var shuffleDone: CompletableDeferred<Long>? = null
    private val buzzLimiter = RateLimiter(SocialLimits.BUZZES_PER_MINUTE, 60_000, clock)

    /** When the game state last changed: a buzz is only allowed after [SocialLimits.BUZZ_IDLE_MS] of nothing. */
    private var stateChangedAt = 0L
    private val reactionLimiter = RateLimiter(SocialLimits.REACTIONS_PER_10S, 10_000, clock)
    private val chatLimiter = RateLimiter(ChatLimits.MESSAGES_PER_10S, 10_000, clock)
    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    private var nextChatId = 1L

    /** Result of the last finished round at this table (for rules like the winner swapping a card). */
    private var lastResult: nl.bluecard.engine.core.GameResult? = null
    private val tokens = mutableMapOf<String, String>()
    private val lastActionIds = mutableMapOf<String, Long>()
    private val connections = mutableMapOf<String, Connection>()
    private val allConnections = mutableSetOf<Connection>()
    private val takeoverJobs = mutableMapOf<String, Job>()
    private var phase = SessionPhase.LOBBY
    private var nextSeatNumber = 1
    private var acceptor: LinkAcceptor? = null
    private var acceptJob: Job? = null
    private var stoppingAccept = false
    private var closed = false

    private val _lobby = MutableStateFlow(buildSnapshot())
    val lobby: StateFlow<LobbySnapshot> = _lobby.asStateFlow()

    private val _accepting = MutableStateFlow(false)
    val accepting: StateFlow<Boolean> = _accepting.asStateFlow()

    private val _notices = MutableSharedFlow<SessionNotice>(extraBufferCapacity = 64)
    val notices: SharedFlow<SessionNotice> = _notices.asSharedFlow()

    private val _social = MutableSharedFlow<SocialEvent>(extraBufferCapacity = 64)
    val social: SharedFlow<SocialEvent> = _social.asSharedFlow()

    /** The host's own seat, for the UI. */
    val hostPort: PlayerPort<V, A> = HostPort()

    val currentConfig: C get() = config

    init {
        scope.launch {
            gameHost.state.collect { state -> if (state != null) onStateChanged(state) }
        }
    }

    // ================================================================== lobby management

    fun startAccepting(linkAcceptor: LinkAcceptor) {
        if (acceptJob?.isActive == true || closed) return
        acceptor = linkAcceptor
        stoppingAccept = false
        _accepting.value = true
        acceptJob = scope.launch {
            try {
                while (true) {
                    val link = linkAcceptor.accept()
                    log("incoming connection from ${link.description}")
                    lock.withLock { openConnection(link) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (!stoppingAccept && !closed) {
                    log("accept failed: ${e.message}")
                    _notices.tryEmit(SessionNotice.AcceptingStopped(e.message ?: "accept failed"))
                }
            } finally {
                _accepting.value = false
            }
        }
    }

    fun stopAccepting() {
        stoppingAccept = true
        acceptor?.close()
        acceptor = null
        acceptJob?.cancel()
        acceptJob = null
        _accepting.value = false
    }

    suspend fun addBot(difficulty: BotDifficulty): Boolean = lock.withLock {
        if (phase == SessionPhase.IN_GAME || phase == SessionPhase.SHUFFLING || seats.size >= effectiveMaxPlayers()) return@withLock false
        val used = seats.map { it.name }.toSet()
        val name = options.botNames.firstOrNull { it !in used } ?: "Bot ${seats.size + 1}"
        seats += SeatInfo("bot${nextSeatNumber++}", name, SeatKind.BOT, botControlled = true, difficulty = difficulty)
        broadcastLobby()
        true
    }

    /** Sets how well all bots at the table play (only outside a running game). */
    suspend fun setBotDifficulty(difficulty: BotDifficulty): Boolean = lock.withLock {
        if (phase == SessionPhase.IN_GAME || phase == SessionPhase.SHUFFLING) return@withLock false
        if (seats.none { it.kind == SeatKind.BOT && it.difficulty != difficulty }) return@withLock true
        for (i in seats.indices) {
            if (seats[i].kind == SeatKind.BOT) seats[i] = seats[i].copy(difficulty = difficulty)
        }
        broadcastLobby()
        true
    }

    /** Removes a bot or kicks a remote player (only outside a running game). */
    suspend fun removeSeat(seatId: String): Boolean = lock.withLock {
        if (phase == SessionPhase.IN_GAME) return@withLock false
        val seat = seats.firstOrNull { it.id == seatId } ?: return@withLock false
        if (seat.kind == SeatKind.HOST) return@withLock false
        connections.remove(seatId)?.let { conn ->
            conn.seatId = null
            conn.send(NetMessage.Disconnect(DisconnectReason.KICKED))
            conn.closeGracefully()
        }
        seats.removeAll { it.id == seatId }
        tokens.remove(seatId)
        broadcastLobby()
        true
    }

    suspend fun updateConfig(newConfig: C) = lock.withLock {
        if (phase == SessionPhase.IN_GAME || phase == SessionPhase.SHUFFLING) return@withLock
        config = newConfig
        broadcastLobby()
    }

    /** The host's card back and cloth, applied on every phone at the table. */
    suspend fun setStyle(newStyle: TableStyle?) = lock.withLock {
        if (style == newStyle) return@withLock
        style = newStyle
        broadcastLobby()
    }

    /** Lets a bot play for a (disconnected) remote player, or gives control back to the human (null). */
    suspend fun setBotControl(seatId: String, difficulty: BotDifficulty?) = lock.withLock {
        setBotControlLocked(seatId, difficulty)
    }

    /**
     * The host gives up. Against bots only ([endGame]) the game ends with the host last; in a Bluetooth game a
     * bot takes over the host's seat so the others can play on.
     */
    suspend fun resignHost(endGame: Boolean) = lock.withLock {
        val hostSeat = seats.firstOrNull { it.kind == SeatKind.HOST } ?: return@withLock
        if (endGame) {
            gameHost.concede(hostSeat.id)
        } else {
            updateSeat(hostSeat.id) { it.copy(botControlled = true, difficulty = BotDifficulty.NORMAL) }
            gameHost.setBot(hostSeat.id, BotDifficulty.NORMAL)
            broadcastLobby()
        }
    }

    /**
     * Moves a seat [delta] places in the seating order (which is also the order of play), only in the lobby.
     * Returns false when that is not possible.
     */
    suspend fun moveSeat(seatId: String, delta: Int): Boolean = lock.withLock {
        if (phase == SessionPhase.IN_GAME) return@withLock false
        val from = seats.indexOfFirst { it.id == seatId }
        val to = from + delta
        if (from < 0 || to !in seats.indices) return@withLock false
        seats.add(to, seats.removeAt(from))
        broadcastLobby()
        true
    }

    /**
     * Starts a round. With [shuffleRitual] on, one player first shakes their phone (SHUFFLING) and this suspends until
     * they are done. Returns null on success, otherwise a reason code (e.g. TOO_FEW_PLAYERS).
     */
    suspend fun startGame(): String? {
        val waitFor: CompletableDeferred<Long> = lock.withLock {
            if (closed) return "CLOSED"
            if (phase == SessionPhase.IN_GAME || phase == SessionPhase.SHUFFLING) return "ALREADY_RUNNING"
            seats.removeAll { it.kind == SeatKind.REMOTE && !it.connected && !it.botControlled }
            seatSpectatorsLocked()
            module.validateSetup(seats.size, config)?.let { return it }
            if (!shuffleRitual) return dealLocked(0L)
            val shuffler = seats[shuffleTurn++ % seats.size]
            val done = CompletableDeferred<Long>()
            shuffleDone = done
            shufflerId = shuffler.id
            phase = SessionPhase.SHUFFLING
            broadcastLobby()
            if (shuffler.botControlled) finishShuffleLater(done)
            done
        }
        val entropy = withTimeoutOrNull(options.shuffleTimeoutMs) { waitFor.await() } ?: 0L
        return lock.withLock {
            if (closed) return "CLOSED"
            if (phase != SessionPhase.SHUFFLING || shuffleDone !== waitFor) return "ALREADY_RUNNING"
            shuffleDone = null
            shufflerId = null
            seats.removeAll { it.kind == SeatKind.REMOTE && !it.connected && !it.botControlled }
            val invalid = module.validateSetup(seats.size, config)
            if (invalid != null) {
                phase = SessionPhase.LOBBY
                broadcastLobby()
                return invalid
            }
            dealLocked(entropy)
        }
    }

    /** The shuffler on this phone (the host) is done; see [startGame]. Also used by the host to skip the shuffle. */
    fun finishShuffle(entropy: Long = 0L) {
        shuffleDone?.complete(entropy)
    }

    private fun finishShuffleLater(done: CompletableDeferred<Long>) {
        scope.launch {
            delay(options.botShuffleMs)
            done.complete(0L)
        }
    }

    private fun dealLocked(entropy: Long): String? {
        gameStartedAt = clock()
        val players = seats.map { PlayerInfo(it.id, it.name) }
        val initial = try {
            module.newGame(players, config, seedSource() xor entropy, lastResult)
        } catch (e: IllegalArgumentException) {
            phase = SessionPhase.LOBBY
            broadcastLobby()
            return "INVALID_SETUP"
        }
        phase = SessionPhase.IN_GAME
        val botSeats = seats.filter { it.botControlled }.associate { it.id to (it.difficulty ?: BotDifficulty.NORMAL) }
        connections.values.forEach { it.send(NetMessage.GameStart(module.info.id, seats.toList())) }
        broadcastLobby()
        gameHost.start(initial, botSeats)
        return null
    }

    /** Spectators take free seats (in the order they came in). */
    private fun seatSpectatorsLocked() {
        val max = effectiveMaxPlayers()
        val iterator = spectators.iterator()
        while (iterator.hasNext() && seats.size < max) {
            val spectator = iterator.next()
            // Table displays stay displays.
            if (spectator.table) continue
            iterator.remove()
            if (spectator.connected) {
                seats += spectator
                _notices.tryEmit(SessionNotice.PlayerJoined(spectator.name))
            } else {
                tokens.remove(spectator.id)
            }
        }
    }

    /** After a finished game: back to the lobby so the host can change players or rules. */
    suspend fun returnToLobby() = lock.withLock {
        if (phase == SessionPhase.LOBBY) return@withLock
        gameHost.stop()
        phase = SessionPhase.LOBBY
        shuffleDone?.cancel()
        shuffleDone = null
        shufflerId = null
        removeGoneRemoteSeats()
        seatSpectatorsLocked()
        broadcastLobby()
    }

    /** Resumes a previously saved local game. */
    suspend fun restore(state: S, savedSeats: List<SeatInfo>) = lock.withLock {
        seats.clear()
        seats += savedSeats
        nextSeatNumber = savedSeats.size + 1
        phase = if (module.result(state) != null) SessionPhase.FINISHED else SessionPhase.IN_GAME
        val botSeats = seats.filter { it.botControlled }.associate { it.id to (it.difficulty ?: BotDifficulty.NORMAL) }
        broadcastLobby()
        gameHost.start(state, botSeats)
    }

    /** Current state plus seats, for saving a local game. */
    fun snapshot(): Pair<S, List<SeatInfo>>? = gameHost.state.value?.let { it to _lobby.value.seats }

    /** Ends the session: tells all clients the host is leaving and closes everything. */
    /**
     * Hands the table over to another game: tells every client to reconnect for [gameId] and shuts this session
     * down (also releasing the server socket, so the new session can open it).
     */
    suspend fun switchGame(gameId: String) {
        lock.withLock {
            if (closed) return
            closed = true
            stopAccepting()
            allConnections.toList().forEach { conn ->
                conn.send(NetMessage.SwitchGame(gameId))
                conn.closeGracefully()
            }
            takeoverJobs.values.forEach { it.cancel() }
            shuffleDone?.cancel()
            gameHost.stop()
        }
        scope.launch {
            delay(CLOSE_GRACE_MS)
            allConnections.toList().forEach { it.link.close() }
            job.cancel()
        }
    }

    suspend fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            stopAccepting()
            allConnections.toList().forEach { conn ->
                conn.send(NetMessage.Disconnect(DisconnectReason.HOST_CLOSED))
                conn.closeGracefully()
            }
            takeoverJobs.values.forEach { it.cancel() }
            shuffleDone?.cancel()
            gameHost.stop()
        }
        // Give writers a moment to flush the DISCONNECT messages before tearing everything down.
        scope.launch {
            delay(CLOSE_GRACE_MS)
            allConnections.toList().forEach { it.link.close() }
            job.cancel()
        }
    }

    // ================================================================== game state broadcasting

    private suspend fun onStateChanged(state: S) = lock.withLock {
        if (phase == SessionPhase.LOBBY || phase == SessionPhase.SHUFFLING) return@withLock
        stateChangedAt = clock()
        val version = module.stateVersion(state)
        for ((seatId, conn) in connections) {
            conn.send(NetMessage.GameState(version, json.encodeToJsonElement(module.viewSerializer, module.view(state, seatId))))
        }
        // The successor keeps an up-to-date copy of the whole table.
        successor?.let { next -> connections[next.seatId]?.send(NetMessage.Handover(handoverSnapshot())) }
        val result = module.result(state)
        if (result != null && phase == SessionPhase.IN_GAME) {
            phase = SessionPhase.FINISHED
            lastResult = result
            connections.values.forEach { it.send(NetMessage.GameEnd(result)) }
            val record = matchRecord(result)
            connections.values.forEach { it.send(NetMessage.MatchRecorded(record)) }
            scope.launch { matchLog.merge(listOf(record)) }
            broadcastLobby()
        }
    }

    private fun matchRecord(result: nl.bluecard.engine.core.GameResult): MatchRecord = MatchRecord(
        id = "${hostDeviceId ?: "host"}-$gameStartedAt",
        gameId = module.info.id,
        playedAt = clock(),
        players = result.ranking.map { entry ->
            val seat = seats.firstOrNull { it.id == entry.playerId }
            val human = seat != null && seat.kind != SeatKind.BOT
            MatchPlayer(
                deviceId = seat?.deviceId,
                name = seat?.name ?: entry.name,
                position = entry.position,
                human = human,
                gaveUp = human && seat?.botControlled == true,
                rightCalls = result.stats[entry.playerId]?.cheatsSpotted ?: 0,
                wrongCalls = result.stats[entry.playerId]?.falseCalls ?: 0,
                caught = result.stats[entry.playerId]?.cheatsCaught ?: 0,
                unnoticed = result.stats[entry.playerId]?.cheatsUnnoticed ?: 0,
            )
        },
    )

    // ================================================================== connections

    private fun openConnection(link: Link): Connection {
        val conn = Connection(link)
        allConnections += conn
        conn.start()
        return conn
    }

    private suspend fun onLine(conn: Connection, line: String) {
        when (val decoded = codec.decode(line)) {
            is DecodeResult.Ok -> handleMessage(conn, decoded.envelope.msg)
            is DecodeResult.VersionMismatch -> {
                log("version mismatch from ${conn.link.description}: v${decoded.remoteVersion}")
                conn.send(NetMessage.JoinRejected(JoinRejectReason.VERSION_MISMATCH, "host v${ProtocolVersion.CURRENT}"))
                conn.closeGracefully()
            }
            is DecodeResult.UnknownType -> badMessage(conn, ErrorCode.UNKNOWN_TYPE, decoded.type)
            is DecodeResult.Malformed -> badMessage(conn, ErrorCode.MALFORMED, decoded.reason)
        }
    }

    private fun badMessage(conn: Connection, code: ErrorCode, detail: String) {
        log("bad message ($code) from ${conn.link.description}: $detail")
        conn.badMessages++
        conn.send(NetMessage.Error(code, detail.take(200)))
        _notices.tryEmit(SessionNotice.ProtocolProblem(detail.take(200)))
        if (conn.badMessages > options.maxBadMessages) conn.link.close()
    }

    private suspend fun handleMessage(conn: Connection, msg: NetMessage) {
        when (msg) {
            is NetMessage.Hello -> handleHello(conn, msg)
            is NetMessage.PlayerAction -> handleAction(conn, msg)
            NetMessage.SyncRequest -> lock.withLock { if (conn.seatId != null) sendFullState(conn) }
            is NetMessage.Ping -> conn.send(NetMessage.Pong(msg.nonce))
            is NetMessage.Pong -> Unit
            is NetMessage.Disconnect -> {
                conn.leftVoluntarily = true
                conn.link.close()
            }
            is NetMessage.Error -> log("client reported error ${msg.code}: ${msg.detail}")
            is NetMessage.Social -> lock.withLock {
                val from = conn.seatId ?: return@withLock
                val kind = SocialKind.parse(msg.kind) ?: return@withLock
                relaySocialLocked(from, kind, msg.to, msg.emoji)
            }
            is NetMessage.Chat -> lock.withLock {
                val from = conn.seatId ?: return@withLock
                relayChatLocked(from, msg.text)
            }
            is NetMessage.Shuffled -> lock.withLock {
                if (conn.seatId != null && conn.seatId == shufflerId) finishShuffle(msg.entropy)
            }
            is NetMessage.MatchHistory -> if (conn.seatId != null) {
                // Merge what the new player knows, then send back the combined history.
                matchLog.merge(msg.records.take(MatchLog.MAX_EXCHANGE))
                conn.send(NetMessage.MatchHistory(matchLog.shareable()))
            }
            else -> conn.send(NetMessage.Error(ErrorCode.UNEXPECTED, "unexpected ${msg::class.simpleName}"))
        }
    }

    private suspend fun handleHello(conn: Connection, hello: NetMessage.Hello) = lock.withLock {
        if (conn.seatId != null) {
            conn.send(NetMessage.Error(ErrorCode.UNEXPECTED, "already joined"))
            return@withLock
        }
        if (hello.intent == HelloIntent.QUERY) {
            conn.send(
                NetMessage.LobbyInfo(
                    hostName = seats.first { it.kind == SeatKind.HOST }.name,
                    gameId = module.info.id,
                    gameName = module.info.displayName,
                    rulesVersion = module.info.rulesVersion,
                    playerCount = seats.size,
                    maxPlayers = effectiveMaxPlayers(),
                    phase = phase,
                ),
            )
            conn.closeGracefully()
            return@withLock
        }
        if (closed) {
            conn.send(NetMessage.JoinRejected(JoinRejectReason.HOST_CLOSING))
            conn.closeGracefully()
            return@withLock
        }

        // Reconnect: the token identifies an existing seat.
        val existing = tokens.entries.firstOrNull { it.value == hello.playerToken }?.key
            ?.takeIf { id -> seats.any { it.id == id } }
        val returningSpectator = tokens.entries.firstOrNull { it.value == hello.playerToken }?.key
            ?.takeIf { id -> spectators.any { it.id == id } }
        if (returningSpectator != null) {
            connections[returningSpectator]?.takeIf { it !== conn }?.let { old ->
                old.seatId = null
                old.link.close()
            }
            attach(conn, returningSpectator)
            updateSpectator(returningSpectator) { it.copy(connected = true) }
            conn.send(
                NetMessage.JoinAccepted(returningSpectator, hostName(), module.info.id, module.info.rulesVersion, reconnected = true, spectator = true),
            )
            broadcastLobby()
            sendFullState(conn)
            return@withLock
        }
        if (existing != null) {
            connections[existing]?.takeIf { it !== conn }?.let { old ->
                old.seatId = null
                old.link.close()
            }
            attach(conn, existing)
            takeoverJobs.remove(existing)?.cancel()
            updateSeat(existing) { it.copy(connected = true) }
            val seat = seats.first { it.id == existing }
            if (seat.kind == SeatKind.REMOTE && seat.botControlled) setBotControlLocked(existing, null)
            conn.send(
                NetMessage.JoinAccepted(existing, hostName(), module.info.id, module.info.rulesVersion, reconnected = true),
            )
            _notices.tryEmit(SessionNotice.PlayerReconnected(seat.name))
            broadcastLobby()
            sendFullState(conn)
            return@withLock
        }

        // A table display always watches; others watch when a game runs (or the table is full) and play next round.
        val asTable = hello.role == NetMessage.Hello.ROLE_TABLE
        val watch = asTable || phase != SessionPhase.LOBBY || seats.size >= effectiveMaxPlayers()
        if (watch && spectators.size >= options.maxSpectators) {
            conn.send(
                NetMessage.JoinRejected(if (phase == SessionPhase.LOBBY) JoinRejectReason.LOBBY_FULL else JoinRejectReason.GAME_IN_PROGRESS),
            )
            conn.closeGracefully()
            return@withLock
        }
        val seatId = "p${nextSeatNumber++}"
        val name = uniqueName(hello.playerName.trim().take(MAX_NAME_LENGTH).ifBlank { "Speler" })
        val seat = SeatInfo(
            seatId,
            name,
            SeatKind.REMOTE,
            deviceId = hello.deviceId.ifBlank { null },
            avatar = hello.avatar.takeIf { it.isNotBlank() && it.length <= MAX_AVATAR_LENGTH },
            table = asTable,
        )
        if (watch) spectators += seat else seats += seat
        tokens[seatId] = hello.playerToken
        attach(conn, seatId)
        conn.send(NetMessage.JoinAccepted(seatId, hostName(), module.info.id, module.info.rulesVersion, spectator = watch))
        _notices.tryEmit(if (watch) SessionNotice.SpectatorJoined(name) else SessionNotice.PlayerJoined(name))
        broadcastLobby()
        if (watch) sendFullState(conn)
    }

    private suspend fun handleAction(conn: Connection, msg: NetMessage.PlayerAction) {
        val seatId = lock.withLock {
            val id = conn.seatId
            if (id == null) {
                conn.send(NetMessage.Error(ErrorCode.NOT_JOINED))
                return
            }
            val last = lastActionIds[id] ?: 0L
            if (msg.actionId <= last) {
                conn.send(NetMessage.ActionResult(msg.actionId, accepted = false, reason = REASON_DUPLICATE))
                return
            }
            lastActionIds[id] = msg.actionId
            if (spectators.any { it.id == id }) {
                conn.send(NetMessage.ActionResult(msg.actionId, accepted = false, reason = REASON_SPECTATOR))
                return
            }
            if (phase != SessionPhase.IN_GAME) {
                conn.send(NetMessage.ActionResult(msg.actionId, accepted = false, reason = REASON_NOT_RUNNING))
                return
            }
            id
        }
        val action = try {
            json.decodeFromJsonElement(module.actionSerializer, msg.action)
        } catch (e: SerializationException) {
            conn.send(NetMessage.ActionResult(msg.actionId, accepted = false, reason = REASON_MALFORMED))
            return
        } catch (e: IllegalArgumentException) {
            conn.send(NetMessage.ActionResult(msg.actionId, accepted = false, reason = REASON_MALFORMED))
            return
        }
        when (val result = gameHost.submit(seatId, action)) {
            is ActionResult.Accepted -> conn.send(NetMessage.ActionResult(msg.actionId, accepted = true))
            is ActionResult.Rejected -> {
                conn.send(NetMessage.ActionResult(msg.actionId, accepted = false, reason = result.reason))
                // Resynchronise: the client may have acted on an outdated picture.
                gameHost.state.value?.let { state ->
                    conn.send(
                        NetMessage.GameState(
                            module.stateVersion(state),
                            json.encodeToJsonElement(module.viewSerializer, module.view(state, seatId)),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun onConnectionLost(conn: Connection) = lock.withLock {
        if (conn.lost) return@withLock
        conn.lost = true
        allConnections -= conn
        conn.outgoing.close()
        conn.link.close()
        val seatId = conn.seatId ?: return@withLock
        if (connections[seatId] !== conn) return@withLock
        connections.remove(seatId)
        spectators.firstOrNull { it.id == seatId }?.let { spectator ->
            // Spectators have no seat to keep: they simply leave (a reconnect within the round still works).
            spectators.removeAll { it.id == seatId }
            if (conn.leftVoluntarily) tokens.remove(seatId) else spectators += spectator.copy(connected = false)
            broadcastLobby()
            return@withLock
        }
        val seat = seats.firstOrNull { it.id == seatId } ?: return@withLock
        log("connection lost: ${seat.name} (voluntary=${conn.leftVoluntarily})")
        when (phase) {
            SessionPhase.LOBBY, SessionPhase.SHUFFLING -> {
                seats.removeAll { it.id == seatId }
                tokens.remove(seatId)
                _notices.tryEmit(SessionNotice.PlayerLeft(seat.name))
                // Nobody is going to shake that phone any more.
                if (seatId == shufflerId) shuffleDone?.complete(0L)
            }
            SessionPhase.IN_GAME, SessionPhase.FINISHED -> {
                updateSeat(seatId) { it.copy(connected = false) }
                if (conn.leftVoluntarily) {
                    _notices.tryEmit(SessionNotice.PlayerLeft(seat.name))
                    if (phase == SessionPhase.IN_GAME) setBotControlLocked(seatId, BotDifficulty.NORMAL)
                } else {
                    _notices.tryEmit(SessionNotice.PlayerDisconnected(seat.name))
                    scheduleTakeover(seatId)
                }
            }
        }
        broadcastLobby()
    }

    private fun scheduleTakeover(seatId: String) {
        if (options.autoTakeoverMs <= 0 || phase != SessionPhase.IN_GAME) return
        takeoverJobs.remove(seatId)?.cancel()
        takeoverJobs[seatId] = scope.launch {
            delay(options.autoTakeoverMs)
            lock.withLock {
                val seat = seats.firstOrNull { it.id == seatId }
                if (seat != null && !seat.connected && !seat.botControlled && phase == SessionPhase.IN_GAME) {
                    setBotControlLocked(seatId, BotDifficulty.NORMAL)
                    broadcastLobby()
                }
                takeoverJobs.remove(seatId)
            }
        }
    }

    // ================================================================== helpers (call with lock held)

    private fun setBotControlLocked(seatId: String, difficulty: BotDifficulty?) {
        val seat = seats.firstOrNull { it.id == seatId } ?: return
        if (seat.kind != SeatKind.REMOTE) return
        updateSeat(seatId) { it.copy(botControlled = difficulty != null, difficulty = difficulty) }
        gameHost.setBot(seatId, difficulty)
        if (difficulty != null) _notices.tryEmit(SessionNotice.BotTookOver(seat.name))
        broadcastLobby()
    }

    private fun attach(conn: Connection, seatId: String) {
        conn.seatId = seatId
        connections[seatId] = conn
    }

    private fun sendFullState(conn: Connection) {
        val seatId = conn.seatId ?: return
        conn.send(NetMessage.PlayerList(_lobby.value))
        val state = gameHost.state.value ?: return
        if (phase == SessionPhase.LOBBY || phase == SessionPhase.SHUFFLING) return
        conn.send(NetMessage.GameStart(module.info.id, seats.toList()))
        conn.send(
            NetMessage.GameState(module.stateVersion(state), json.encodeToJsonElement(module.viewSerializer, module.view(state, seatId))),
        )
        module.result(state)?.let { conn.send(NetMessage.GameEnd(it)) }
    }

    private fun broadcastLobby() {
        val snapshot = buildSnapshot()
        _lobby.value = snapshot
        connections.values.forEach { it.send(NetMessage.PlayerList(snapshot)) }
        updateSuccessorLocked()
    }

    // ================================================================== host handover

    /** The first connected human player (in seat order) whose phone can be reached again. */
    private fun pickSuccessor(): SuccessorInfo? {
        for (seat in seats) {
            if (seat.kind != SeatKind.REMOTE || !seat.connected || seat.botControlled) continue
            val address = connections[seat.id]?.link?.remoteAddress ?: continue
            return SuccessorInfo(seat.id, address, seat.name)
        }
        return null
    }

    /** Tells everybody who would take over, and keeps that player's copy of the table up to date. */
    private fun updateSuccessorLocked() {
        if (closed) return
        val next = pickSuccessor()
        successor = next
        // Also tells players who joined after the successor was chosen.
        for (conn in connections.values) {
            if (conn.toldSuccessor && conn.knownSuccessor == next) continue
            conn.send(NetMessage.Successor(next?.seatId, next?.address, next?.name))
            conn.knownSuccessor = next
            conn.toldSuccessor = true
        }
        next?.let { connections[it.seatId]?.send(NetMessage.Handover(handoverSnapshot())) }
    }

    private fun handoverSnapshot(): HostSnapshot = HostSnapshot(
        gameId = module.info.id,
        rulesVersion = module.info.rulesVersion,
        config = json.encodeToJsonElement(module.configSerializer, config),
        state = gameHost.state.value?.takeIf { phase == SessionPhase.IN_GAME || phase == SessionPhase.FINISHED }
            ?.let { json.encodeToJsonElement(module.stateSerializer, it) },
        seats = seats.toList(),
        spectators = spectators.toList(),
        tokens = tokens.toMap() + listOfNotNull(hostToken?.let { hostSeatId to it }),
        phase = if (phase == SessionPhase.SHUFFLING) SessionPhase.LOBBY else phase,
        lastResult = lastResult,
        shuffleTurn = shuffleTurn,
        style = style,
    )

    /** Who takes over if this host leaves now (null: nobody, the table closes). */
    val currentSuccessor: SuccessorInfo? get() = successor

    /**
     * The host leaves but the table goes on: the successor becomes host and everybody moves there (a bot plays for
     * the old host). Returns false when nobody can take over; then nothing happened.
     */
    suspend fun leaveWithHandover(): Boolean {
        lock.withLock {
            if (closed) return false
            val next = pickSuccessor() ?: return false
            // Make sure the successor has the very latest table.
            connections[next.seatId]?.send(NetMessage.Handover(handoverSnapshot()))
            if (next != successor) {
                successor = next
                connections.values.forEach { it.send(NetMessage.Successor(next.seatId, next.address, next.name)) }
            }
            closed = true
            stopAccepting()
            allConnections.toList().forEach { conn ->
                conn.send(NetMessage.Disconnect(DisconnectReason.HOST_MOVED))
                conn.closeGracefully()
            }
            takeoverJobs.values.forEach { it.cancel() }
            shuffleDone?.cancel()
            gameHost.stop()
        }
        scope.launch {
            delay(CLOSE_GRACE_MS)
            allConnections.toList().forEach { it.link.close() }
            job.cancel()
        }
        return true
    }

    /**
     * Takes over a table from [snapshot] (this phone was the successor, playing as [mySeatId]): this phone's seat
     * becomes the host, the old host's seat is played by a bot until they come back, the others reconnect here.
     */
    suspend fun adopt(snapshot: HostSnapshot, mySeatId: String) = lock.withLock {
        config = json.decodeFromJsonElement(module.configSerializer, snapshot.config)
        seats.clear()
        val running = snapshot.state != null && snapshot.phase == SessionPhase.IN_GAME
        for (seat in snapshot.seats) {
            seats += when {
                seat.id == mySeatId -> seat.copy(kind = SeatKind.HOST, connected = true, botControlled = false, difficulty = null)
                seat.kind == SeatKind.HOST -> seat.copy(
                    kind = SeatKind.REMOTE,
                    connected = false,
                    botControlled = running,
                    difficulty = if (running) BotDifficulty.NORMAL else null,
                )
                seat.kind == SeatKind.REMOTE -> seat.copy(connected = false)
                else -> seat
            }
        }
        spectators.clear()
        spectators += snapshot.spectators.map { it.copy(connected = false) }
        tokens.clear()
        tokens += snapshot.tokens - mySeatId
        hostSeatId = mySeatId
        hostId.value = mySeatId
        nextSeatNumber = (seats + spectators).mapNotNull { it.id.removePrefix("p").toIntOrNull() }.maxOrNull()?.plus(1) ?: 1
        lastResult = snapshot.lastResult
        shuffleTurn = snapshot.shuffleTurn
        style = snapshot.style
        val state = snapshot.state?.let { json.decodeFromJsonElement(module.stateSerializer, it) }
        phase = when {
            state == null || snapshot.phase == SessionPhase.LOBBY -> SessionPhase.LOBBY
            module.result(state) != null -> SessionPhase.FINISHED
            else -> SessionPhase.IN_GAME
        }
        if (phase == SessionPhase.LOBBY) seats.removeAll { it.kind == SeatKind.REMOTE && !it.connected && it.id !in tokens }
        broadcastLobby()
        if (state != null && phase != SessionPhase.LOBBY) {
            val botSeats = seats.filter { it.botControlled }.associate { it.id to (it.difficulty ?: BotDifficulty.NORMAL) }
            gameHost.start(state, botSeats)
            // Players who do not come back get a bot after a while, as after any lost connection.
            seats.filter { it.kind == SeatKind.REMOTE && !it.connected && !it.botControlled }.forEach { scheduleTakeover(it.id) }
        }
    }

    private fun buildSnapshot() = LobbySnapshot(
        gameId = module.info.id,
        gameName = module.info.displayName,
        hostName = hostName(),
        seats = seats.toList(),
        minPlayers = module.info.minPlayers,
        maxPlayers = effectiveMaxPlayers(),
        phase = phase,
        config = json.encodeToJsonElement(module.configSerializer, config),
        spectators = spectators.toList(),
        style = style,
        shufflerId = shufflerId,
    )

    private fun hostName(): String = seats.firstOrNull { it.kind == SeatKind.HOST }?.name ?: "Host"

    /** Largest player count the current configuration supports. */
    private fun effectiveMaxPlayers(): Int =
        (module.info.maxPlayers downTo module.info.minPlayers).firstOrNull { module.validateSetup(it, config) == null }
            ?: module.info.maxPlayers

    private fun updateSpectator(seatId: String, transform: (SeatInfo) -> SeatInfo) {
        val index = spectators.indexOfFirst { it.id == seatId }
        if (index >= 0) spectators[index] = transform(spectators[index])
    }

    /** Checks the rate limit and the target, then shows [kind] from [fromId] on every phone. */
    /** Numbers a chat line, keeps it and sends it to everybody (also back to the sender). */
    private fun relayChatLocked(fromId: String, text: String): Boolean {
        val sender = seats.firstOrNull { it.id == fromId } ?: spectators.firstOrNull { it.id == fromId } ?: return false
        val clean = ChatLimits.clean(text) ?: return false
        if (!chatLimiter.tryAcquire(fromId)) return false
        val line = ChatMessage(nextChatId++, fromId, sender.name, clean)
        _chat.value = (_chat.value + line).takeLast(ChatLimits.KEEP)
        val message = NetMessage.Chat(clean, fromId, line.id)
        connections.values.forEach { it.send(message) }
        return true
    }

    private fun relaySocialLocked(fromId: String, kind: SocialKind, toId: String?, emoji: String? = null): Boolean {
        val sender = seats.firstOrNull { it.id == fromId } ?: spectators.firstOrNull { it.id == fromId } ?: return false
        val target = if (kind == SocialKind.BUZZ) {
            // Only people can be buzzed, not yourself, and only someone the game is waiting for who has done
            // nothing for a while: a buzz is a nudge, not a way to rush people.
            val waitingFor = gameHost.state.value?.takeIf { phase == SessionPhase.IN_GAME }?.let { module.pendingActors(it) } ?: return false
            if (toId !in waitingFor || clock() - stateChangedAt < SocialLimits.BUZZ_IDLE_MS) return false
            seats.firstOrNull { it.id == toId && it.id != fromId && !it.botControlled && it.kind != SeatKind.BOT } ?: return false
        } else {
            null
        }
        // A free reaction must be one of the known emoji, never arbitrary text.
        if (kind == SocialKind.REACTION && !EmojiCatalog.isAllowed(emoji)) return false
        val sentEmoji = emoji.takeIf { kind == SocialKind.REACTION }
        val limiter = if (kind == SocialKind.BUZZ) buzzLimiter else reactionLimiter
        if (!limiter.tryAcquire(fromId)) return false
        val message = NetMessage.Social(kind.name, target?.id, fromId, sentEmoji)
        connections.values.forEach { it.send(message) }
        _social.tryEmit(SocialEvent(fromId, sender.name, kind, target?.id, sentEmoji))
        return true
    }

    private fun updateSeat(seatId: String, transform: (SeatInfo) -> SeatInfo) {
        val index = seats.indexOfFirst { it.id == seatId }
        if (index >= 0) seats[index] = transform(seats[index])
    }

    private fun removeGoneRemoteSeats() {
        val gone = seats.filter { it.kind == SeatKind.REMOTE && !it.connected }.map { it.id }
        seats.removeAll { it.id in gone }
        gone.forEach { tokens.remove(it) }
        // Remote players that were bot-controlled but are connected again play themselves.
        for (i in seats.indices) {
            if (seats[i].kind == SeatKind.REMOTE) seats[i] = seats[i].copy(botControlled = false, difficulty = null)
        }
    }

    private fun uniqueName(requested: String): String {
        val used = seats.map { it.name }.toSet()
        if (requested !in used) return requested
        var n = 2
        while ("$requested ($n)" in used) n++
        return "$requested ($n)"
    }

    // ================================================================== connection

    private inner class Connection(val link: Link) {
        val outgoing = Channel<NetMessage>(Channel.UNLIMITED)
        @Volatile var seatId: String? = null
        @Volatile var missedBeats = 0
        @Volatile var lost = false
        @Volatile var leftVoluntarily = false
        var knownSuccessor: SuccessorInfo? = null
        var toldSuccessor = false
        var badMessages = 0
        private var seq = 0L
        private var nonce = 0L

        fun send(message: NetMessage) {
            outgoing.trySend(message)
        }

        /** Sends everything still queued, then closes the link. */
        fun closeGracefully() {
            outgoing.close()
        }

        fun start() {
            // Writer: drains the queue in order; closes the link when the queue is closed or writing fails.
            scope.launch {
                try {
                    for (message in outgoing) link.writeLine(codec.encode(++seq, message))
                } catch (e: IOException) {
                    log("write failed to ${link.description}: ${e.message}")
                } finally {
                    link.close()
                }
            }
            // Reader.
            val reader = scope.launch {
                try {
                    while (true) {
                        val line = link.readLine() ?: break
                        missedBeats = 0
                        onLine(this@Connection, line)
                    }
                } catch (e: IOException) {
                    log("read failed from ${link.description}: ${e.message}")
                } finally {
                    onConnectionLost(this@Connection)
                }
            }
            // Heartbeat: ping regularly and drop silent connections.
            scope.launch {
                while (reader.isActive) {
                    delay(options.heartbeatIntervalMs)
                    if (!reader.isActive) break
                    send(NetMessage.Ping(++nonce))
                    if (++missedBeats > options.heartbeatMissedLimit) {
                        log("heartbeat timeout: ${link.description}")
                        link.close()
                        break
                    }
                }
            }
        }
    }

    // ================================================================== host's own port

    private inner class HostPort : PlayerPort<V, A> {
        override val playerId: StateFlow<String?> = hostId
        override val view: StateFlow<V?> = gameHost.state
            .map { state -> state?.let { module.view(it, hostSeatId) } }
            .stateIn(scope, SharingStarted.Eagerly, null)
        override val lobby: StateFlow<LobbySnapshot?> = _lobby
        override val connection: StateFlow<ConnectionStatus> = MutableStateFlow(ConnectionStatus.Local)
        override val notices: SharedFlow<SessionNotice> = _notices

        override suspend fun submit(action: A): SubmitResult =
            when (val result = gameHost.submit(hostSeatId, action)) {
                is ActionResult.Accepted -> SubmitResult.Accepted
                is ActionResult.Rejected -> SubmitResult.Rejected(result.reason)
            }

        override val social: SharedFlow<SocialEvent> = _social

        override suspend fun sendSocial(kind: SocialKind, toId: String?, emoji: String?): Boolean =
            lock.withLock { relaySocialLocked(hostSeatId, kind, toId, emoji) }

        override val chat: StateFlow<List<ChatMessage>> = _chat

        override suspend fun sendChat(text: String): Boolean = lock.withLock { relayChatLocked(hostSeatId, text) }

        override suspend fun shuffled(entropy: Long) {
            lock.withLock { if (shufflerId == hostSeatId) finishShuffle(entropy) }
        }
    }

    companion object {
        /** Avatars are small; anything bigger is ignored so lobby messages stay small. */
        const val MAX_AVATAR_LENGTH = 24_000
        const val HOST_SEAT_ID = "host"
        const val REASON_DUPLICATE = "DUPLICATE_ACTION"
        const val REASON_NOT_RUNNING = "GAME_NOT_RUNNING"
        const val REASON_MALFORMED = "MALFORMED_ACTION"
        const val REASON_SPECTATOR = "NOT_PLAYING"
        const val MAX_NAME_LENGTH = 20
        private const val CLOSE_GRACE_MS = 600L
    }
}
