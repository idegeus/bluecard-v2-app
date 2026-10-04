package nl.bluecard.engine.zweedspesten

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Card

/** Everything a player can try to do. All actions are validated by [ZpEngine]. */
@Serializable
sealed interface ZpAction {

    /** Swap phase: exchange a hand card with one of your face-up cards. */
    @Serializable
    @SerialName("SWAP")
    data class Swap(val handCard: Card, val faceUpCard: Card) : ZpAction

    /** Swap phase: done swapping. */
    @Serializable
    @SerialName("READY")
    data object Ready : ZpAction

    /** Play one or more cards of the same rank from the hand or (when the hand is empty) the face-up cards. */
    @Serializable
    @SerialName("PLAY_CARD")
    data class Play(val cards: List<Card>) : ZpAction

    /** Play one of your face-down cards blind, by position. */
    @Serializable
    @SerialName("PLAY_BLIND")
    data class PlayBlind(val index: Int) : ZpAction

    /** Take the whole discard pile into your hand (the penalty when you cannot play). */
    @Serializable
    @SerialName("PICK_UP")
    data object PickUp : ZpAction

    /**
     * "Vals!": accuse a player who laid cards in the last [nl.bluecard.engine.core.CheatWindow.MS] of cheating
     * (only when rules are not enforced): [playId] picks the play, null means the newest one by someone else. Any
     * other player may call it, also out of turn. Caught cheaters take the pile; a false accusation makes the
     * accuser take the pile.
     */
    @Serializable
    @SerialName("CHALLENGE")
    data class Challenge(val playId: Long? = null) : ZpAction

    /** House rule "winnaar ruilt": the winner of the last round gives this hand card to the loser. */
    @Serializable
    @SerialName("GIVE_CARD")
    data class GiveCard(val card: Card) : ZpAction

    /** House rule: turn over the top card of the draw pile and play it if it fits. */
    @Serializable
    @SerialName("DRAW_CARD")
    data object Gamble : ZpAction
}
