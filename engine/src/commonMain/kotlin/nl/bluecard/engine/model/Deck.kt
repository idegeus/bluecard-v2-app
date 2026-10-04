package nl.bluecard.engine.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Immutable draw pile. The top of the pile is the LAST element of [cards].
 * All operations return a new instance.
 */
@Serializable
data class Deck(val cards: List<Card> = emptyList()) {

    val size: Int get() = cards.size
    val isEmpty: Boolean get() = cards.isEmpty()

    /** Draws up to [count] cards from the top. Returns the drawn cards (first drawn first) and the remaining deck. */
    fun draw(count: Int): Pair<List<Card>, Deck> {
        require(count >= 0) { "count must be >= 0" }
        val n = minOf(count, cards.size)
        if (n == 0) return emptyList<Card>() to this
        val drawn = cards.takeLast(n).asReversed().toList()
        return drawn to Deck(cards.dropLast(n))
    }

    fun shuffled(random: Random): Deck = Deck(cards.shuffled(random))

    /** Puts [extra] underneath the current cards (used when a new draw pile is formed). */
    fun withCardsUnderneath(extra: List<Card>): Deck = Deck(extra + cards)

    companion object {
        /** All 52 cards of a standard French deck, in a fixed order. */
        fun standard52(): Deck = Deck(Suit.entries.flatMap { suit -> Rank.STANDARD.map { Card(it, suit) } })

        /** A standard deck plus [jokers] jokers (0–2: a red and a black one). */
        fun withJokers(jokers: Int): Deck {
            require(jokers in 0..JOKER_SUITS.size) { "0..${JOKER_SUITS.size} jokers" }
            return Deck(standard52().cards + JOKER_SUITS.take(jokers).map { Card(Rank.JOKER, it) })
        }

        private val JOKER_SUITS = listOf(Suit.HEARTS, Suit.SPADES)
    }
}
