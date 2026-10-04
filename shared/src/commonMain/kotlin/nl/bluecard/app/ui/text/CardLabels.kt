package nl.bluecard.app.ui.text

import nl.bluecard.app.R
import nl.bluecard.app.res.Resources
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.model.Suit

/**
 * The letters printed on court cards and the spoken names of cards differ per language (B/V/H in Dutch, J/Q/K in
 * English, V/D/R in French …). Read from the texts on every call, so a language change shows at once.
 */
object CardLabels {
    /** Kept for callers from the Android-only era; texts are always read live now. */
    @Suppress("UNUSED_PARAMETER")
    fun init(res: Resources) = Unit

    /** The letter or number in the card's corner. */
    fun label(rank: Rank): String = when (rank) {
        Rank.JACK -> Resources.getString(R.string.rank_JACK)
        Rank.QUEEN -> Resources.getString(R.string.rank_QUEEN)
        Rank.KING -> Resources.getString(R.string.rank_KING)
        Rank.ACE -> Resources.getString(R.string.rank_ACE)
        Rank.JOKER -> Resources.getString(R.string.rank_JOKER)
        else -> rank.label
    }

    /** What a screen reader says, e.g. "harten vrouw" / "queen of hearts". */
    fun speech(card: Card): String {
        if (card.rank.isJoker) {
            return Resources.getString(if (card.suit.isRed) R.string.speech_joker_red else R.string.speech_joker_black)
        }
        val rank = when (card.rank) {
            Rank.JACK -> Resources.getString(R.string.speech_JACK)
            Rank.QUEEN -> Resources.getString(R.string.speech_QUEEN)
            Rank.KING -> Resources.getString(R.string.speech_KING)
            Rank.ACE -> Resources.getString(R.string.speech_ACE)
            else -> card.rank.label
        }
        val suit = when (card.suit) {
            Suit.CLUBS -> Resources.getString(R.string.speech_suit_CLUBS)
            Suit.DIAMONDS -> Resources.getString(R.string.speech_suit_DIAMONDS)
            Suit.HEARTS -> Resources.getString(R.string.speech_suit_HEARTS)
            Suit.SPADES -> Resources.getString(R.string.speech_suit_SPADES)
        }
        return Resources.getString(R.string.speech_card, suit, rank)
    }
}
