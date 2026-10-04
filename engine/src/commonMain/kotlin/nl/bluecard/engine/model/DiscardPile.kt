package nl.bluecard.engine.model

import kotlinx.serialization.Serializable

/** Immutable discard pile. The top card is the LAST element of [cards]. */
@Serializable
data class DiscardPile(val cards: List<Card> = emptyList()) {

    val size: Int get() = cards.size
    val isEmpty: Boolean get() = cards.isEmpty()
    val top: Card? get() = cards.lastOrNull()

    fun plus(played: List<Card>): DiscardPile = DiscardPile(cards + played)

    /** Number of consecutive cards of the same rank at the top of the pile. */
    fun topRunLength(): Int {
        val topRank = top?.rank ?: return 0
        var count = 0
        for (i in cards.indices.reversed()) {
            if (cards[i].rank == topRank) count++ else break
        }
        return count
    }

    /** The topmost card that matches [predicate], searching downwards. */
    fun topMost(predicate: (Card) -> Boolean): Card? = cards.lastOrNull(predicate)

    /** The last [n] cards, bottom to top. */
    fun lastCards(n: Int): List<Card> = cards.takeLast(n)

    fun cleared(): DiscardPile = DiscardPile()
}
