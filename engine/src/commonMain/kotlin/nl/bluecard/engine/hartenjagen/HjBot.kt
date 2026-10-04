package nl.bluecard.engine.hartenjagen

import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.model.Suit
import kotlin.random.Random

/** Computer player for Hartenjagen: ducks tricks, dumps dangerous cards when it cannot follow. */
object HjBot {

    fun chooseAction(view: HjPlayerView, difficulty: BotDifficulty, random: Random): HjAction {
        val rules = view.rules
        val legal = view.legal
        if (legal.passCount > 0) return HjAction.PassCards(view.myHand.sortedByDescending { danger(it, view.myHand) }.take(legal.passCount))
        val options = legal.playableCards
        if (difficulty == BotDifficulty.EASY && random.nextInt(100) < 35) return HjAction.Play(options.random(random))
        return HjAction.Play(choose(view, options, rules))
    }

    /** How much a bot wants to get rid of a card before the deal. */
    private fun danger(card: Card, hand: List<Card>): Int {
        val spades = hand.count { it.suit == Suit.SPADES }
        return when {
            card == HjRules.QUEEN_OF_SPADES && spades < 4 -> 100
            card.suit == Suit.SPADES && card.rank.value > Rank.QUEEN.value && spades < 4 -> 90
            card.suit == Suit.HEARTS -> 40 + card.rank.value
            else -> card.rank.value
        }
    }

    private fun choose(view: HjPlayerView, options: List<Card>, rules: HjHouseRules): Card {
        val lead = view.leadSuit
        if (lead == null) {
            // Lead low, avoid spades while the queen is out there unless holding it is safe, avoid hearts.
            val safe = options.filter { it.suit != Suit.HEARTS && !(it.suit == Suit.SPADES && it.rank.value >= Rank.QUEEN.value) }
            return (safe.ifEmpty { options }).minBy { it.rank.value }
        }
        val following = options.filter { it.suit == lead }
        if (following.isNotEmpty()) {
            val best = view.trick.filter { it.card.suit == lead }.maxOf { it.card.rank.value }
            val trickPoints = view.trick.sumOf { HjRules.points(it.card, rules) }
            val last = view.trick.size == view.players.size - 1
            // Dump the queen of spades when someone else already played higher.
            if (lead == Suit.SPADES && HjRules.QUEEN_OF_SPADES in following && best > Rank.QUEEN.value) return HjRules.QUEEN_OF_SPADES
            val under = following.filter { it.rank.value < best }
            if (under.isNotEmpty()) return under.maxBy { it.rank.value }
            // Must win anyway: as last player without points take it with the highest (but not the queen).
            if (last && trickPoints == 0) return following.filter { it != HjRules.QUEEN_OF_SPADES }.maxByOrNull { it.rank.value } ?: following.first()
            return following.filter { it != HjRules.QUEEN_OF_SPADES }.minByOrNull { it.rank.value } ?: following.first()
        }
        // Cannot follow: get rid of the worst card.
        return options.maxBy { card ->
            when {
                card == HjRules.QUEEN_OF_SPADES -> 1_000
                card.suit == Suit.SPADES && card.rank.value > Rank.QUEEN.value -> 500
                HjRules.isPenalty(card, rules) -> 300 + card.rank.value
                else -> card.rank.value
            }
        }
    }
}
