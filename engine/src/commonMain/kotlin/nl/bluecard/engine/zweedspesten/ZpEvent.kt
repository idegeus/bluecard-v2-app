package nl.bluecard.engine.zweedspesten

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Card

@Serializable
enum class BurnReason { BURN_CARD, FOUR_OF_A_KIND }

/** Something that happened, shown to players as a log line / banner. */
@Serializable
sealed interface ZpEvent {
    @Serializable
    @SerialName("swap_phase")
    data object SwapPhaseStarted : ZpEvent

    @Serializable
    @SerialName("swapped")
    data class Swapped(val playerId: String) : ZpEvent

    @Serializable
    @SerialName("ready")
    data class PlayerReady(val playerId: String) : ZpEvent

    @Serializable
    @SerialName("started")
    data class GameStarted(val startingPlayerId: String, val startCard: Card? = null) : ZpEvent

    @Serializable
    @SerialName("played")
    data class CardsPlayed(val playerId: String, val cards: List<Card>, val source: CardSource) : ZpEvent

    @Serializable
    @SerialName("blind")
    data class BlindRevealed(val playerId: String, val card: Card, val success: Boolean) : ZpEvent

    @Serializable
    @SerialName("gamble")
    data class GambleRevealed(val playerId: String, val card: Card, val success: Boolean) : ZpEvent

    @Serializable
    @SerialName("picked_up")
    data class PileTaken(val playerId: String, val count: Int) : ZpEvent

    @Serializable
    @SerialName("burned")
    data class PileBurned(val playerId: String, val count: Int, val reason: BurnReason) : ZpEvent

    @Serializable
    @SerialName("skipped")
    data class PlayersSkipped(val byPlayerId: String, val skippedIds: List<String>) : ZpEvent

    @Serializable
    @SerialName("reversed")
    data class DirectionReversed(val playerId: String, val direction: Int) : ZpEvent

    @Serializable
    @SerialName("starts_after_pickup")
    data class StartsAfterPickUp(val playerId: String) : ZpEvent

    @Serializable
    @SerialName("extra_turn")
    data class ExtraTurn(val playerId: String) : ZpEvent

    @Serializable
    @SerialName("reshuffled")
    data class DrawPileReshuffled(val count: Int) : ZpEvent

    @Serializable
    @SerialName("player_out")
    data class PlayerFinished(val playerId: String, val position: Int) : ZpEvent

    @Serializable
    @SerialName("cheat_caught")
    data class CheatCaught(val accuserId: String, val cheaterId: String, val cards: List<Card>, val penaltyCount: Int) : ZpEvent

    @Serializable
    @SerialName("false_accusation")
    data class FalseAccusation(val accuserId: String, val accusedId: String, val penaltyCount: Int) : ZpEvent

    /** The cards stay secret; everybody only sees that the exchange happened. */
    @Serializable
    @SerialName("exchanged")
    data class CardsExchanged(val winnerId: String, val loserId: String) : ZpEvent

    @Serializable
    @SerialName("resigned")
    data class Resigned(val playerId: String) : ZpEvent

    @Serializable
    @SerialName("game_over")
    data class GameOver(val winnerId: String, val loserId: String? = null) : ZpEvent
}

@Serializable
data class ZpLogEntry(val seq: Long, val event: ZpEvent)
