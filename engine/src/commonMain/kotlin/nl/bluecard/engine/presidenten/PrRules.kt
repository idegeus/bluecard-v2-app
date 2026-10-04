package nl.bluecard.engine.presidenten

import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Deck
import nl.bluecard.engine.model.Rank

/** Pure rule checks for Presidenten, shared by engine, views and bots. */
object PrRules {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 7

    fun validateSetup(playerCount: Int, rules: PrHouseRules): PrRejectReason? = when {
        playerCount < MIN_PLAYERS -> PrRejectReason.TOO_FEW_PLAYERS
        playerCount > MAX_PLAYERS -> PrRejectReason.TOO_MANY_PLAYERS
        rules.jokers !in 0..PrHouseRules.MAX_JOKERS -> PrRejectReason.INVALID_JOKERS
        else -> null
    }

    fun deck(rules: PrHouseRules): Deck = Deck.withJokers(rules.jokers)

    /** Value of a rank for beating: 3 lowest … ace, then the 2 (when high), jokers on top. */
    fun strength(rank: Rank, rules: PrHouseRules): Int = when {
        rank.isJoker -> 20
        rank == Rank.TWO && rules.twoHigh -> 15
        else -> rank.value
    }

    fun strength(card: Card, rules: PrHouseRules): Int = strength(card.rank, rules)

    /** The highest strength any set can have with these rules. */
    fun maxStrength(rules: PrHouseRules): Int = if (rules.jokers > 0) 20 else if (rules.twoHigh) 15 else 14

    /** Card order for hands: weakest first, then by suit. */
    fun handOrder(rules: PrHouseRules): Comparator<Card> = compareBy<Card>({ strength(it, rules) }, { it.suit.ordinal })

    /**
     * The value a set counts as: the rank of its non-joker cards (all equal), or JOKER for jokers only. Null when the
     * cards do not form a set.
     */
    fun setRank(cards: List<Card>): Rank? {
        if (cards.isEmpty()) return null
        val natural = cards.filter { !it.rank.isJoker }.map { it.rank }.distinct()
        return when (natural.size) {
            0 -> Rank.JOKER
            1 -> natural.single()
            else -> null
        }
    }

    /** Whether [cards] may be laid on [top] (null = leading). Returns the reason when not. */
    fun whyNot(cards: List<Card>, top: PrPlay?, rules: PrHouseRules): PrRejectReason? {
        val rank = setRank(cards) ?: return PrRejectReason.MIXED_RANKS
        val topCards = top?.cards ?: return null
        if (cards.size != topCards.size) return PrRejectReason.WRONG_COUNT
        val mine = strength(rank, rules)
        val theirs = strength(requireNotNull(setRank(topCards)), rules)
        return when {
            mine > theirs -> null
            mine == theirs && rules.equalSkips -> null
            else -> PrRejectReason.TOO_LOW
        }
    }

    /** All sets that could be laid now from [hand] (each a distinct choice of cards; jokers fill up sets). */
    fun legalSets(hand: List<Card>, top: PrPlay?, rules: PrHouseRules): List<List<Card>> {
        val jokers = hand.filter { it.rank.isJoker }
        val byRank = hand.filter { !it.rank.isJoker }.groupBy { it.rank }
        val sizes = top?.cards?.size?.let { listOf(it) } ?: (1..(4 + jokers.size)).toList()
        val result = mutableListOf<List<Card>>()
        for ((_, cards) in byRank) {
            for (size in sizes) {
                val natural = minOf(cards.size, size)
                val fill = size - natural
                if (natural == 0 || fill > jokers.size) continue
                val set = cards.take(natural) + jokers.take(fill)
                if (whyNot(set, top, rules) == null) result += set
            }
        }
        for (size in sizes) {
            if (size in 1..jokers.size) {
                val set = jokers.take(size)
                if (whyNot(set, top, rules) == null) result += set
            }
        }
        return result
    }
}
