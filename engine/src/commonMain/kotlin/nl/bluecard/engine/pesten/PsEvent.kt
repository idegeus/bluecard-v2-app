package nl.bluecard.engine.pesten

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit

/** Something that happened, shown to players as a log line / banner. */
@Serializable
sealed interface PsEvent {
    @Serializable
    @SerialName("started")
    data class GameStarted(val startingPlayerId: String, val startCard: Card) : PsEvent

    @Serializable
    @SerialName("played")
    data class CardsPlayed(val playerId: String, val cards: List<Card>, val suit: Suit? = null) : PsEvent

    /** [penalty] is true for pending "pak"-cards, false for an ordinary draw. */
    @Serializable
    @SerialName("drew")
    data class CardsDrawn(val playerId: String, val count: Int, val penalty: Boolean) : PsEvent

    @Serializable
    @SerialName("plays_after_penalty")
    data class PlaysAfterPenalty(val playerId: String) : PsEvent

    @Serializable
    @SerialName("passed")
    data class Passed(val playerId: String) : PsEvent

    @Serializable
    @SerialName("must_draw")
    data class MustDraw(val playerId: String, val count: Int) : PsEvent

    @Serializable
    @SerialName("plays_again")
    data class PlaysAgain(val playerId: String) : PsEvent

    @Serializable
    @SerialName("skipped")
    data class PlayersSkipped(val byPlayerId: String, val skippedIds: List<String>) : PsEvent

    @Serializable
    @SerialName("reversed")
    data class DirectionReversed(val playerId: String, val direction: Int) : PsEvent

    @Serializable
    @SerialName("last_card")
    data class LastCardCalled(val playerId: String) : PsEvent

    @Serializable
    @SerialName("last_card_forgotten")
    data class LastCardForgotten(val catcherId: String, val playerId: String, val penaltyCount: Int) : PsEvent

    /** Went out on a special card while that is not allowed: one card penalty. */
    @Serializable
    @SerialName("special_finish")
    data class SpecialFinishPenalty(val playerId: String) : PsEvent

    @Serializable
    @SerialName("reshuffled")
    data class DrawPileReshuffled(val count: Int) : PsEvent

    @Serializable
    @SerialName("player_out")
    data class PlayerFinished(val playerId: String, val position: Int) : PsEvent

    @Serializable
    @SerialName("cheat_caught")
    data class CheatCaught(val accuserId: String, val cheaterId: String, val cards: List<Card>, val penaltyCount: Int) : PsEvent

    @Serializable
    @SerialName("false_accusation")
    data class FalseAccusation(val accuserId: String, val accusedId: String, val penaltyCount: Int) : PsEvent

    /** The cards stay secret; everybody only sees that the exchange happened. */
    @Serializable
    @SerialName("exchanged")
    data class CardsExchanged(val winnerId: String, val loserId: String) : PsEvent

    @Serializable
    @SerialName("resigned")
    data class Resigned(val playerId: String) : PsEvent

    @Serializable
    @SerialName("game_over")
    data class GameOver(val winnerId: String, val loserId: String? = null) : PsEvent
}

@Serializable
data class PsLogEntry(val seq: Long, val event: PsEvent)

/** Stable reason codes for rejected actions. The app maps these to user-facing messages. */
enum class PsRejectReason {
    UNKNOWN_PLAYER,
    GAME_FINISHED,
    NOT_YOUR_TURN,
    CARD_NOT_AVAILABLE,
    EMPTY_SELECTION,
    DUPLICATE_CARDS,
    MIXED_RANKS,
    MULTIPLE_NOT_ALLOWED,
    DOES_NOT_FIT,
    WRONG_SUIT,
    MUST_DRAW_OR_STACK,
    ONLY_DRAWN_CARD,
    MUST_CHOOSE_SUIT,
    ALREADY_DRAWN,
    NOTHING_TO_DRAW,
    NOT_DRAWN_YET,
    LAST_CARD_NOT_ALLOWED,
    NOTHING_TO_CATCH,
    NOTHING_TO_CHALLENGE,
    CANNOT_CHALLENGE_SELF,
    LAST_CARD_MUST_FIT,
    /** Only players still in the game may call "Vals!" or "Vergeten!". */
    NOT_PLAYING,
    TOO_FEW_PLAYERS,
    TOO_MANY_PLAYERS,
    NOT_ENOUGH_CARDS,
    INVALID_HAND_SIZE,
    INVALID_JOKERS,
    EXCHANGE_PENDING,
    NOT_THE_WINNER,
}
