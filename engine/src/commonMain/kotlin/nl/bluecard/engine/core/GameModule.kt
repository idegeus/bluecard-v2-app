package nl.bluecard.engine.core

import kotlinx.serialization.KSerializer
import nl.bluecard.engine.model.PlayerInfo
import kotlin.random.Random

/**
 * Contract every card game implements. Everything above this interface (sessions, Bluetooth protocol,
 * lobby, bot scheduling) is game-agnostic; adding a new card game means adding a new [GameModule]
 * plus a game screen.
 *
 * @param C configuration / house rules
 * @param S full, authoritative game state (only ever lives on the host)
 * @param A player action
 * @param V per-player view (what one player is allowed to see, including legal moves)
 */
interface GameModule<C : Any, S : Any, A : Any, V : Any> {
    val info: GameInfo

    val configSerializer: KSerializer<C>
    val stateSerializer: KSerializer<S>
    val actionSerializer: KSerializer<A>
    val viewSerializer: KSerializer<V>

    fun defaultConfig(): C

    /** Returns a reason code when this player count / config combination cannot be played, else null. */
    fun validateSetup(playerCount: Int, config: C): String?

    /** Creates a freshly dealt game. Must be deterministic for a given [seed]. */
    fun newGame(players: List<PlayerInfo>, config: C, seed: Long): S

    /**
     * A new round after [previous] (same table): lets a game apply rules about the last result, e.g. the winner
     * swapping a card with the loser. Defaults to a fresh game.
     */
    fun newGame(players: List<PlayerInfo>, config: C, seed: Long, previous: GameResult?): S = newGame(players, config, seed)

    /** Validates and applies [action] on behalf of [playerId]. Never throws for invalid input. */
    fun apply(state: S, playerId: String, action: A): ActionResult<S>

    /** What [viewerId] may see. Must never leak hidden information of other players. */
    fun view(state: S, viewerId: String): V

    /** Players that may act right now (e.g. the current player, or everyone during a simultaneous phase). */
    fun pendingActors(state: S): Set<String>

    /** Monotonically increasing version, bumped on every accepted action. */
    fun stateVersion(state: S): Long

    /** Non-null once the game is over. */
    fun result(state: S): GameResult?

    /** Lets a bot choose an action using only the information in its own [view]. */
    fun chooseBotAction(view: V, difficulty: BotDifficulty, random: Random): A

    /** Players in seating order. */
    fun players(state: S): List<PlayerInfo>

    /**
     * Ends the game at once because [playerId] gives up (against bots): that player is ranked last, the others as
     * at a normal end of the game. Returns the state unchanged when the game is already over.
     */
    fun concede(state: S, playerId: String): S

    /** How long a bot waits before acting; a game can give humans more time to react in some situations. */
    fun botDelayMs(state: S, baseMs: Long): Long = baseMs

    /**
     * The host's clock ([nowMs], wall-clock millis) reaches the game: right after every accepted action and at
     * [nextTickAt]. Lets a game stamp and close time windows, like calling "Vals!" up to [CheatWindow.MS] after a
     * play. Returns the very same instance when nothing changes.
     */
    fun tick(state: S, nowMs: Long): S = state

    /** When [tick] next has something to do (wall-clock millis), or null when nothing is waiting for the clock. */
    fun nextTickAt(state: S): Long? = null
}

/** Calling "Vals!" stays possible this long after a play, also when others have played since. */
object CheatWindow {
    const val MS = 10_000L
}
