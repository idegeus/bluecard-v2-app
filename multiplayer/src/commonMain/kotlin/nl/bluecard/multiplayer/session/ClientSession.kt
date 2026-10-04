package nl.bluecard.multiplayer.session

import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import nl.bluecard.engine.core.GameModule
import nl.bluecard.multiplayer.protocol.DecodeResult
import nl.bluecard.multiplayer.protocol.DisconnectReason
import nl.bluecard.multiplayer.protocol.ErrorCode
import nl.bluecard.multiplayer.protocol.HelloIntent
import nl.bluecard.multiplayer.protocol.JoinRejectReason
import nl.bluecard.multiplayer.protocol.NetMessage
import nl.bluecard.multiplayer.protocol.ProtocolCodec
import nl.bluecard.multiplayer.protocol.ProtocolJson
import nl.bluecard.multiplayer.transport.Link
import kotlinx.io.IOException

data class ClientOptions(
    val connectTimeoutMs: Long = 15_000,
    val joinTimeoutMs: Long = 10_000,
    val actionTimeoutMs: Long = 8_000,
    val heartbeatIntervalMs: Long = 4_000,
    val heartbeatMissedLimit: Int = 4,
    val reconnectAttempts: Int = 4,
    val reconnectDelayMs: Long = 2_000,
    val appVersion: String = "",
)

/**
 * Client side of a game session. Connects to the host through [connector] (which opens a new [Link] each
 * time, so it can also be used to reconnect), joins with a persistent [playerToken] and mirrors the host's
 * lobby and the player's personal game view.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class ClientSession<C : Any, S : Any, A : Any, V : Any>(
    val module: GameModule<C, S, A, V>,
    private val playerName: String,
    private val playerToken: String,
    private val connector: suspend () -> Link,
    parentScope: CoroutineScope,
    private val options: ClientOptions = ClientOptions(),
    private val log: (String) -> Unit = {},
    /** Public id of this phone, for the shared leaderboard. */
    private val deviceId: String = "",
    private val matchLog: MatchLog = MatchLog.NONE,
    private val avatar: String = "",
    /** Join as a table display (see [NetMessage.Hello.ROLE_TABLE]). */
    val asTable: Boolean = false,
    private val clock: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : PlayerPort<V, A> {

    private sealed interface JoinOutcome {
        data object Joined : JoinOutcome
        data class Refused(val reason: String) : JoinOutcome
        data class Failed(val reason: String) : JoinOutcome
    }

    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val codec = ProtocolCodec()
    private val json = ProtocolJson.json

    private val _playerId = MutableStateFlow<String?>(null)
    override val playerId: StateFlow<String?> = _playerId.asStateFlow()

    private val _view = MutableStateFlow<V?>(null)
    override val view: StateFlow<V?> = _view.asStateFlow()

    private val _lobby = MutableStateFlow<LobbySnapshot?>(null)
    override val lobby: StateFlow<LobbySnapshot?> = _lobby.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Connecting)
    override val connection: StateFlow<ConnectionStatus> = _connection.asStateFlow()

    private val _notices = MutableSharedFlow<SessionNotice>(extraBufferCapacity = 64)
    override val notices: SharedFlow<SessionNotice> = _notices.asSharedFlow()

    private val _successor = MutableStateFlow<SuccessorInfo?>(null)

    /** Who takes over if the host goes away (announced by the host). */
    val successor: StateFlow<SuccessorInfo?> = _successor.asStateFlow()

    /** The latest copy of the whole table, only kept when this phone is the successor. */
    @Volatile var handover: HostSnapshot? = null
        private set

    private val _social = MutableSharedFlow<SocialEvent>(extraBufferCapacity = 64)
    override val social: SharedFlow<SocialEvent> = _social.asSharedFlow()

    // The host enforces the same limits; checking here too gives the button immediate feedback.
    private val buzzLimiter = RateLimiter(SocialLimits.BUZZES_PER_MINUTE, 60_000, clock)
    private val reactionLimiter = RateLimiter(SocialLimits.REACTIONS_PER_10S, 10_000, clock)

    /** Guards [pending] and [nextActionId]: they are touched from the reader loop and from callers. */
    private val pendingLock = SynchronizedObject()
    private val pending = mutableMapOf<Long, CompletableDeferred<SubmitResult>>()
    private var nextActionId = 0L

    /** Everything that belongs to one physical connection, so a dying old link cannot affect a newer one. */
    private class LinkContext(
        val link: Link,
        val out: Channel<NetMessage>,
        val join: CompletableDeferred<JoinOutcome>,
    )

    @Volatile private var current: LinkContext? = null
    private val outgoing: Channel<NetMessage>? get() = current?.out
    @Volatile private var hostDisconnectReason: DisconnectReason? = null
    @Volatile private var leaving = false
    @Volatile private var lastStateVersion = -1L
    @Volatile private var hasJoined = false
    @Volatile private var missedBeats = 0
    private var connectionJob: Job? = null

    /** Connects and joins. Returns the resulting status (Connected, Rejected or Lost). */
    suspend fun connect(): ConnectionStatus {
        _connection.value = ConnectionStatus.Connecting
        return when (val outcome = openAndJoin()) {
            JoinOutcome.Joined -> ConnectionStatus.Connected.also { _connection.value = it }
            is JoinOutcome.Refused -> ConnectionStatus.Rejected(outcome.reason).also { _connection.value = it }
            is JoinOutcome.Failed -> ConnectionStatus.Lost(LostReason.CONNECTION_LOST).also { _connection.value = it }
        }
    }

    /** Manual reconnect after the automatic attempts gave up. */
    suspend fun reconnect(): ConnectionStatus {
        if (leaving) return _connection.value
        if (_connection.value is ConnectionStatus.Connected || _connection.value is ConnectionStatus.Reconnecting) {
            return _connection.value
        }
        return reconnectLoop()
    }

    /** Leaves the game deliberately: tells the host and closes the connection. */
    fun leave() {
        if (leaving) return
        leaving = true
        _connection.value = ConnectionStatus.Lost(LostReason.LEFT)
        outgoing?.let { out ->
            out.trySend(NetMessage.Disconnect(DisconnectReason.LEFT))
            out.close()
        }
        failPending("LEFT")
        scope.launch {
            delay(LEAVE_GRACE_MS)
            current?.link?.close()
            job.cancel()
        }
    }

    /** Asks the host to resend the complete lobby and game state. */
    fun requestSync() {
        outgoing?.trySend(NetMessage.SyncRequest)
    }

    override suspend fun submit(action: A): SubmitResult {
        val out = outgoing
        if (out == null || _connection.value != ConnectionStatus.Connected) return SubmitResult.Failed(NOT_CONNECTED)
        val deferred = CompletableDeferred<SubmitResult>()
        val id = synchronized(pendingLock) { (++nextActionId).also { pending[it] = deferred } }
        val payload = json.encodeToJsonElement(module.actionSerializer, action)
        if (out.trySend(NetMessage.PlayerAction(id, lastStateVersion, payload)).isFailure) {
            synchronized(pendingLock) { pending.remove(id) }
            return SubmitResult.Failed(NOT_CONNECTED)
        }
        val result = withTimeoutOrNull(options.actionTimeoutMs) { deferred.await() }
        synchronized(pendingLock) { pending.remove(id) }
        return result ?: SubmitResult.Failed(TIMEOUT)
    }

    override suspend fun sendSocial(kind: SocialKind, toId: String?, emoji: String?): Boolean {
        val out = outgoing ?: return false
        if (_connection.value != ConnectionStatus.Connected) return false
        if (kind == SocialKind.REACTION && !EmojiCatalog.isAllowed(emoji)) return false
        val limiter = if (kind == SocialKind.BUZZ) buzzLimiter else reactionLimiter
        if (!limiter.tryAcquire("me")) return false
        return out.trySend(NetMessage.Social(kind.name, toId, emoji = emoji)).isSuccess
    }

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()
    private val chatLimiter = RateLimiter(ChatLimits.MESSAGES_PER_10S, 10_000, clock)

    override suspend fun sendChat(text: String): Boolean {
        val out = outgoing ?: return false
        if (_connection.value != ConnectionStatus.Connected) return false
        val clean = ChatLimits.clean(text) ?: return false
        if (!chatLimiter.tryAcquire("me")) return false
        return out.trySend(NetMessage.Chat(clean)).isSuccess
    }

    override suspend fun shuffled(entropy: Long) {
        outgoing?.trySend(NetMessage.Shuffled(entropy))
    }

    // ================================================================== connection lifecycle

    private suspend fun openAndJoin(): JoinOutcome {
        val link = try {
            withTimeout(options.connectTimeoutMs) { connector() }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) return JoinOutcome.Failed("CONNECT_TIMEOUT")
            throw e
        } catch (e: IOException) {
            log("connect failed: ${e.message}")
            return JoinOutcome.Failed(e.message ?: "CONNECT_FAILED")
        } catch (e: RuntimeException) {
            // A missing platform permission (e.g. Android's SecurityException) or another platform error.
            log("connect failed: $e")
            return JoinOutcome.Failed(if (e::class.simpleName == "SecurityException") "PERMISSION" else "CONNECT_FAILED")
        }
        if (leaving) {
            link.close()
            return JoinOutcome.Failed("LEFT")
        }
        val context = LinkContext(link, Channel(Channel.UNLIMITED), CompletableDeferred())
        hostDisconnectReason = null
        current = context
        val role = if (asTable) NetMessage.Hello.ROLE_TABLE else NetMessage.Hello.ROLE_PLAYER
        context.out.trySend(NetMessage.Hello(playerName, playerToken, HelloIntent.JOIN, options.appVersion, deviceId, avatar, role))
        connectionJob = scope.launch {
            runLink(context)
            onLinkEnded(context)
        }
        val outcome = withTimeoutOrNull(options.joinTimeoutMs) { context.join.await() } ?: JoinOutcome.Failed("JOIN_TIMEOUT")
        if (outcome !is JoinOutcome.Joined) {
            link.close()
        }
        return outcome
    }

    private suspend fun runLink(context: LinkContext) = coroutineScope {
        val link = context.link
        val out = context.out
        missedBeats = 0
        val writer = launch {
            var seq = 0L
            try {
                for (message in out) link.writeLine(codec.encode(++seq, message))
            } catch (e: IOException) {
                log("write failed: ${e.message}")
            } finally {
                link.close()
            }
        }
        val heartbeat = launch {
            var nonce = 0L
            while (true) {
                delay(options.heartbeatIntervalMs)
                out.trySend(NetMessage.Ping(++nonce))
                if (++missedBeats > options.heartbeatMissedLimit) {
                    log("host silent, closing link")
                    link.close()
                    break
                }
            }
        }
        try {
            while (true) {
                val line = link.readLine() ?: break
                missedBeats = 0
                handleLine(context, line)
            }
        } catch (e: IOException) {
            log("read failed: ${e.message}")
        } finally {
            out.close()
            link.close()
            heartbeat.cancel()
            writer.cancel()
        }
    }

    /** Runs on the connection coroutine after the link has ended. */
    private fun onLinkEnded(context: LinkContext) {
        context.join.complete(JoinOutcome.Failed(CONNECTION_LOST))
        if (current !== context) return // an old link; a newer connection is already in charge
        current = null
        failPending(CONNECTION_LOST)
        if (leaving) return
        when (hostDisconnectReason) {
            DisconnectReason.HOST_CLOSED -> {
                _connection.value = ConnectionStatus.Lost(LostReason.HOST_CLOSED)
                return
            }
            DisconnectReason.KICKED -> {
                _connection.value = ConnectionStatus.Lost(LostReason.KICKED)
                return
            }
            DisconnectReason.VERSION_MISMATCH -> {
                _connection.value = ConnectionStatus.Lost(LostReason.VERSION_MISMATCH)
                return
            }
            DisconnectReason.HOST_MOVED -> {
                // The host left and handed the table to the successor; the app moves there.
                _connection.value = ConnectionStatus.Lost(LostReason.HOST_MOVED)
                return
            }
            else -> Unit
        }
        // Only reconnect automatically when we were actually playing / in the lobby.
        if (hasJoined && _connection.value == ConnectionStatus.Connected) {
            scope.launch { reconnectLoop() }
        }
    }

    private suspend fun reconnectLoop(): ConnectionStatus {
        for (attempt in 1..options.reconnectAttempts) {
            if (leaving) return _connection.value
            _connection.value = ConnectionStatus.Reconnecting(attempt, options.reconnectAttempts)
            delay(options.reconnectDelayMs * attempt)
            if (leaving) return _connection.value
            when (val outcome = openAndJoin()) {
                JoinOutcome.Joined -> {
                    _connection.value = ConnectionStatus.Connected
                    return ConnectionStatus.Connected
                }
                is JoinOutcome.Refused -> {
                    _connection.value = ConnectionStatus.Rejected(outcome.reason)
                    return _connection.value
                }
                is JoinOutcome.Failed -> log("reconnect attempt $attempt failed: ${outcome.reason}")
            }
        }
        _connection.value = ConnectionStatus.Lost(LostReason.CONNECTION_LOST)
        return _connection.value
    }

    private fun failPending(reason: String) {
        val all = synchronized(pendingLock) { pending.values.toList().also { pending.clear() } }
        all.forEach { it.complete(SubmitResult.Failed(reason)) }
    }

    // ================================================================== incoming messages

    private fun handleLine(context: LinkContext, line: String) {
        when (val decoded = codec.decode(line)) {
            is DecodeResult.Ok -> handleMessage(context, decoded.envelope.msg)
            is DecodeResult.VersionMismatch -> {
                log("host uses protocol v${decoded.remoteVersion}")
                hostDisconnectReason = DisconnectReason.VERSION_MISMATCH
                context.join.complete(JoinOutcome.Refused(REASON_VERSION))
                context.link.close()
            }
            is DecodeResult.UnknownType -> {
                log("unknown message type ${decoded.type}")
                context.out.trySend(NetMessage.Error(ErrorCode.UNKNOWN_TYPE, decoded.type))
            }
            is DecodeResult.Malformed -> {
                log("malformed message: ${decoded.reason}")
                context.out.trySend(NetMessage.Error(ErrorCode.MALFORMED, decoded.reason.take(200)))
                _notices.tryEmit(SessionNotice.ProtocolProblem(decoded.reason.take(200)))
            }
        }
    }

    private fun handleMessage(context: LinkContext, msg: NetMessage) {
        if (current !== context) return // late message on an abandoned link
        when (msg) {
            is NetMessage.JoinAccepted -> {
                if (msg.gameId != module.info.id || msg.rulesVersion != module.info.rulesVersion) {
                    hostDisconnectReason = DisconnectReason.VERSION_MISMATCH
                    context.join.complete(JoinOutcome.Refused(REASON_VERSION))
                    context.out.trySend(NetMessage.Disconnect(DisconnectReason.VERSION_MISMATCH))
                    context.out.close()
                    return
                }
                _playerId.value = msg.playerId
                hasJoined = true
                context.join.complete(JoinOutcome.Joined)
                // Share the games this phone knows; the host answers with the combined history.
                scope.launch { context.out.trySend(NetMessage.MatchHistory(matchLog.shareable())) }
            }
            is NetMessage.JoinRejected -> {
                if (msg.reason == JoinRejectReason.VERSION_MISMATCH) {
                    hostDisconnectReason = DisconnectReason.VERSION_MISMATCH
                }
                context.join.complete(JoinOutcome.Refused(msg.reason.name))
            }
            is NetMessage.PlayerList -> _lobby.value = msg.lobby
            is NetMessage.GameStart -> {
                // A new game restarts state versions at zero.
                lastStateVersion = -1
                _view.value = null
            }
            is NetMessage.GameState -> {
                if (msg.stateVersion < lastStateVersion) return
                try {
                    _view.value = json.decodeFromJsonElement(module.viewSerializer, msg.view)
                    lastStateVersion = msg.stateVersion
                } catch (e: SerializationException) {
                    log("cannot decode view: ${e.message}")
                    _notices.tryEmit(SessionNotice.ProtocolProblem("view"))
                    requestSync()
                } catch (e: IllegalArgumentException) {
                    log("cannot decode view: ${e.message}")
                    _notices.tryEmit(SessionNotice.ProtocolProblem("view"))
                }
            }
            is NetMessage.ActionResult -> {
                val result = if (msg.accepted) SubmitResult.Accepted else SubmitResult.Rejected(msg.reason ?: "REJECTED")
                synchronized(pendingLock) { pending.remove(msg.actionId) }?.complete(result)
            }
            is NetMessage.GameEnd -> Unit // the final view carries the result
            is NetMessage.SwitchGame -> {
                // The host moves the table to another game: stop here, the app reconnects with that game.
                leaving = true
                _connection.value = ConnectionStatus.Lost(LostReason.GAME_SWITCHED)
                failPending("GAME_SWITCHED")
                _notices.tryEmit(SessionNotice.GameSwitched(msg.gameId))
                context.out.close()
            }
            is NetMessage.Social -> {
                val kind = SocialKind.parse(msg.kind) ?: return
                val lobby = _lobby.value
                val name = (lobby?.seats.orEmpty() + lobby?.spectators.orEmpty()).firstOrNull { it.id == msg.from }?.name ?: ""
                _social.tryEmit(SocialEvent(msg.from, name, kind, msg.to, msg.emoji))
            }
            is NetMessage.Chat -> {
                val lobby = _lobby.value
                val name = (lobby?.seats.orEmpty() + lobby?.spectators.orEmpty()).firstOrNull { it.id == msg.from }?.name ?: ""
                val text = ChatLimits.clean(msg.text) ?: return
                if (_chat.value.any { it.id == msg.id }) return
                _chat.value = (_chat.value + ChatMessage(msg.id, msg.from, name, text)).takeLast(ChatLimits.KEEP)
            }
            is NetMessage.Successor -> _successor.value =
                if (msg.seatId != null && msg.address != null) SuccessorInfo(msg.seatId, msg.address, msg.name ?: "") else null
            is NetMessage.Handover -> if (msg.snapshot.gameId == module.info.id) handover = msg.snapshot
            is NetMessage.MatchRecorded -> scope.launch { matchLog.merge(listOf(msg.record)) }
            is NetMessage.MatchHistory -> scope.launch { matchLog.merge(msg.records.take(MatchLog.MAX_EXCHANGE)) }
            is NetMessage.Ping -> context.out.trySend(NetMessage.Pong(msg.nonce))
            is NetMessage.Pong -> Unit
            is NetMessage.Disconnect -> hostDisconnectReason = msg.reason
            is NetMessage.Error -> log("host reported error ${msg.code}: ${msg.detail}")
            is NetMessage.Hello, is NetMessage.LobbyInfo, is NetMessage.PlayerAction, NetMessage.SyncRequest, is NetMessage.Shuffled ->
                log("ignoring unexpected ${msg::class.simpleName} from host")
        }
    }

    companion object {
        const val NOT_CONNECTED = "NOT_CONNECTED"
        const val TIMEOUT = "TIMEOUT"
        const val CONNECTION_LOST = "CONNECTION_LOST"
        const val REASON_VERSION = "VERSION_MISMATCH"
        private const val LEAVE_GRACE_MS = 800L
    }
}
