package nl.bluecard.multiplayer.session

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The only thing the UI talks to. Identical for a local bot game, the host's own seat and a remote client,
 * so the game screen does not care where the authoritative state lives.
 */
interface PlayerPort<V : Any, A : Any> {
    val playerId: StateFlow<String?>
    val view: StateFlow<V?>
    val lobby: StateFlow<LobbySnapshot?>
    val connection: StateFlow<ConnectionStatus>
    val notices: SharedFlow<SessionNotice>

    /** Sends an intent to the authoritative host. The result says whether the engine accepted it. */
    suspend fun submit(action: A): SubmitResult

    /** Buzzes and reactions at this table (also your own, once the host accepted them). */
    val social: SharedFlow<SocialEvent> get() = NoSocial.flow

    /**
     * Sends a buzz (to [toId]) or a reaction. False when it could not be sent, e.g. because of the rate limit
     * ([SocialLimits]).
     */
    suspend fun sendSocial(kind: SocialKind, toId: String? = null, emoji: String? = null): Boolean = false

    /** The chat at this table (newest last, at most [ChatLimits.KEEP]). */
    val chat: StateFlow<List<ChatMessage>> get() = NoSocial.chat

    /** Sends a chat line; false when it could not be sent (empty, too fast, not connected). */
    suspend fun sendChat(text: String): Boolean = false

    /** This phone's player finished shaking during SHUFFLING; [entropy] is mixed into the deal. */
    suspend fun shuffled(entropy: Long) {}
}

/** How often buzzes and reactions may be sent, per person. */
object SocialLimits {
    const val BUZZES_PER_MINUTE = 3
    const val REACTIONS_PER_10S = 6

    /** A player can only be buzzed after this long without anything happening at the table. */
    const val BUZZ_IDLE_MS = 5_000L
}

internal object NoSocial {
    val flow: SharedFlow<SocialEvent> = kotlinx.coroutines.flow.MutableSharedFlow()
    val chat: StateFlow<List<ChatMessage>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
}

/** Sliding-window limiter: at most [max] events per [windowMs] per key. */
class RateLimiter(private val max: Int, private val windowMs: Long, private val clock: () -> Long) {
    private val times = mutableMapOf<String, ArrayDeque<Long>>()

    fun tryAcquire(key: String): Boolean {
        val now = clock()
        val queue = times.getOrPut(key) { ArrayDeque() }
        while (queue.isNotEmpty() && now - queue.first() >= windowMs) queue.removeFirst()
        if (queue.size >= max) return false
        queue.addLast(now)
        return true
    }
}
