package nl.bluecard.engine.core

import kotlinx.serialization.Serializable

/**
 * Counters for the "fun facts" after a game. Everything here was visible to all players during the game, so it can
 * be shown to everybody. Each game fills the counters that apply to it.
 */
@Serializable
data class PlayerStats(
    val cardsPlayed: Int = 0,
    /** Caught cheating ("Vals!" was right about them). */
    val cheatsCaught: Int = 0,
    /** Cheated and got away with it: nobody called "Vals!" in time (the ninja). */
    val cheatsUnnoticed: Int = 0,
    /** Called "Vals!" and was right. */
    val cheatsSpotted: Int = 0,
    /** Called "Vals!" and was wrong. */
    val falseCalls: Int = 0,
    val lastCardCalls: Int = 0,
    /** Forgot to call "Last card!" and got caught. */
    val lastCardForgotten: Int = 0,
    /** Caught someone who forgot to call "Last card!". */
    val forgetsCaught: Int = 0,
    val pilesTaken: Int = 0,
    val cardsFromPiles: Int = 0,
    val burns: Int = 0,
    val cardsDrawn: Int = 0,
    val blindHits: Int = 0,
    val blindMisses: Int = 0,
    val passes: Int = 0,
    val tricksWon: Int = 0,
    val pointsTaken: Int = 0,
    val moonShots: Int = 0,
)

/** Updates the counters of [playerId]. */
fun Map<String, PlayerStats>.bump(playerId: String, change: PlayerStats.() -> PlayerStats): Map<String, PlayerStats> =
    this + (playerId to (this[playerId] ?: PlayerStats()).change())
