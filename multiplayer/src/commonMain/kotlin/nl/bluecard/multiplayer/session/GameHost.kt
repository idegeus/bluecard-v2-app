package nl.bluecard.multiplayer.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.core.GameModule
import kotlin.random.Random

/**
 * Holds the single authoritative game state, applies actions through the [GameModule] and lets bots play
 * for the seats registered in [bots]. Thread-safe: all state changes are serialized by a mutex.
 */
class GameHost<C : Any, S : Any, A : Any, V : Any>(
    val module: GameModule<C, S, A, V>,
    private val scope: CoroutineScope,
    private val botDelayMs: () -> Long,
    private val random: Random = Random.Default,
    private val log: (String) -> Unit = {},
    /** Wall clock (millis) for the game's time windows, see [GameModule.tick]. */
    private val clock: () -> Long,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<S?>(null)
    val state: StateFlow<S?> = _state.asStateFlow()

    private val _bots = MutableStateFlow<Map<String, BotDifficulty>>(emptyMap())
    val bots: StateFlow<Map<String, BotDifficulty>> = _bots.asStateFlow()

    private var botJob: Job? = null
    private var tickJob: Job? = null

    fun start(initial: S, botSeats: Map<String, BotDifficulty>) {
        _bots.value = botSeats
        _state.value = initial
        ensureBotLoop()
        ensureTicker()
    }

    /** Hands a seat to a bot ([difficulty] non-null) or back to a human (null). */
    fun setBot(playerId: String, difficulty: BotDifficulty?) {
        _bots.value = if (difficulty == null) _bots.value - playerId else _bots.value + (playerId to difficulty)
    }

    suspend fun submit(playerId: String, action: A): ActionResult<S> = mutex.withLock {
        val now = clock()
        // Close what timed out before judging the action (a late "Vals!" must not count).
        val current = _state.value?.let { module.tick(it, now) } ?: return@withLock ActionResult.Rejected(NO_GAME)
        val result = module.apply(current, playerId, action)
        _state.value = if (result is ActionResult.Accepted) module.tick(result.state, now) else current
        result
    }

    /** [playerId] gives up: the game ends with that player last. */
    suspend fun concede(playerId: String) = mutex.withLock {
        _state.value?.let { _state.value = module.concede(it, playerId) }
    }

    fun view(playerId: String): V? = _state.value?.let { module.view(it, playerId) }

    fun stop() {
        botJob?.cancel()
        botJob = null
        tickJob?.cancel()
        tickJob = null
        _state.value = null
    }

    /** Wakes the game when one of its time windows closes ([GameModule.nextTickAt]). */
    private fun ensureTicker() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (true) {
                val snapshot = _state.value
                val at = snapshot?.let(module::nextTickAt)
                if (at == null) {
                    _state.first { it !== snapshot }
                    continue
                }
                // Wait for the deadline, unless the state changes first (then look again).
                val changed = withTimeoutOrNull((at - clock()).coerceAtLeast(0L)) { _state.first { it !== snapshot } }
                if (changed != null) continue
                mutex.withLock {
                    val current = _state.value ?: return@withLock
                    // The delay has passed: tick at least at the deadline (also under a virtual test clock).
                    val ticked = module.tick(current, maxOf(clock(), at))
                    if (ticked !== current) _state.value = ticked
                }
            }
        }
    }

    private fun ensureBotLoop() {
        if (botJob?.isActive == true) return
        botJob = scope.launch {
            while (true) {
                val snapshot = _state.value
                val botSeats = _bots.value
                if (!runBotRound(snapshot, botSeats)) {
                    // Nothing for bots to do: wait until the state or the bot assignment changes.
                    combine(_state, _bots) { s, b -> s to b }
                        .first { (s, b) -> s !== snapshot || b != botSeats }
                }
            }
        }
    }

    /** Lets every bot that may act right now take its turn. Returns false when no bot had anything to do. */
    private suspend fun runBotRound(snapshot: S?, botSeats: Map<String, BotDifficulty>): Boolean {
        if (snapshot == null || module.result(snapshot) != null) return false
        val pending = module.pendingActors(snapshot)
        val actors = pending.filter { it in botSeats }
        if (actors.isEmpty()) return false
        delay(module.botDelayMs(snapshot, botDelayMs()))
        // In a simultaneous phase (e.g. swapping) a bot finishes all its moves at once; on a normal turn a bot
        // makes one move, so that a follow-up move (extra turn) gets its own delay and stays visible.
        val simultaneous = pending.size > 1
        for (id in actors) {
            var moves = 0
            do {
                val current = _state.value ?: return true
                if (id !in module.pendingActors(current) || id !in _bots.value) break
                playBotMove(id, current, _bots.value.getValue(id))
                moves++
            } while (simultaneous && moves < MAX_SIMULTANEOUS_MOVES)
        }
        return true
    }

    private suspend fun playBotMove(id: String, current: S, difficulty: BotDifficulty) {
        var result = submit(id, module.chooseBotAction(module.view(current, id), difficulty, random))
        // Defensive: bots only choose from legal moves, but never let a bot stall the game.
        var attempts = 0
        while (result is ActionResult.Rejected && attempts < MAX_BOT_RETRIES) {
            log("bot $id action rejected: ${result.reason}; retrying")
            val latest = _state.value ?: return
            result = submit(id, module.chooseBotAction(module.view(latest, id), BotDifficulty.EASY, random))
            attempts++
        }
    }

    companion object {
        const val NO_GAME = "NO_GAME"
        private const val MAX_BOT_RETRIES = 10
        private const val MAX_SIMULTANEOUS_MOVES = 12
    }
}
