package nl.bluecard.engine.pesten

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit

/** Everything a player can try to do. All actions are validated by [PsEngine]. */
@Serializable
sealed interface PsAction {

    /** Play one card (or several of the same value, if allowed). [suit] is the chosen suit after a jack. */
    @Serializable
    @SerialName("PLAY_CARD")
    data class Play(val cards: List<Card>, val suit: Suit? = null) : PsAction

    /** Draw a card, or all pending "pak"-cards. Always allowed on your turn. */
    @Serializable
    @SerialName("DRAW_CARD")
    data object Draw : PsAction

    /** After drawing: keep the card and end the turn. */
    @Serializable
    @SerialName("PASS")
    data object Pass : PsAction

    /** "Laatste kaart!": allowed with one or two cards in hand, also out of turn. */
    @Serializable
    @SerialName("LAST_CARD")
    data object CallLastCard : PsAction

    /** "Vergeten!": the player who went down to one card without calling it draws penalty cards. */
    @Serializable
    @SerialName("CATCH_LAST_CARD")
    data object CatchLastCard : PsAction

    /** House rule "winnaar ruilt": the winner of the last round gives this card to the loser. */
    @Serializable
    @SerialName("GIVE_CARD")
    data class GiveCard(val card: Card) : PsAction

    /**
     * "Vals!": accuse a player who laid cards in the last [nl.bluecard.engine.core.CheatWindow.MS] of cheating (only
     * when rules are not enforced). [playId] picks the play, null means the newest one by someone else.
     */
    @Serializable
    @SerialName("CHALLENGE")
    data class Challenge(val playId: Long? = null) : PsAction
}
