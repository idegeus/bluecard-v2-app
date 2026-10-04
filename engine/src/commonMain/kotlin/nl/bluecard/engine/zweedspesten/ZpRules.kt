package nl.bluecard.engine.zweedspesten

import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.DiscardPile
import nl.bluecard.engine.model.Rank

/**
 * What the next card must satisfy. Both bounds are inclusive; null means unbounded.
 * Derived purely from the discard pile and the house rules.
 */
@Serializable
data class ZpRequirement(val minValue: Int? = null, val maxValue: Int? = null) {
    val isUnrestricted: Boolean get() = minValue == null && maxValue == null

    fun allows(value: Int): Boolean =
        (minValue == null || value >= minValue) && (maxValue == null || value <= maxValue)

    companion object {
        val NONE = ZpRequirement()
    }
}

/** Pure rule functions. No state, no side effects — shared by the engine, the views and the bots. */
object ZpRules {

    fun requirement(pile: DiscardPile, rules: ZpHouseRules): ZpRequirement {
        val top = pile.top ?: return ZpRequirement.NONE
        return if (rules.effectOf(top.rank) == ZpEffect.LOWER) {
            ZpRequirement(maxValue = if (rules.lowerIncludesEqual) top.rank.value else top.rank.value - 1)
        } else {
            ZpRequirement(minValue = top.rank.value)
        }
    }

    fun canPlayRank(rank: Rank, pile: DiscardPile, rules: ZpHouseRules): Boolean =
        fits(rank, requirement(pile, rules), rules)

    /** Whether a card of [rank] satisfies [requirement]. Also used to judge a past play ("Vals!"). */
    fun fits(rank: Rank, requirement: ZpRequirement, rules: ZpHouseRules): Boolean {
        // "7 or lower" binds every card, special ones included: no 10 on a 7. A 2 is low enough anyway.
        if (requirement.maxValue != null) return requirement.allows(rank.value)
        if (rules.effectOf(rank).alwaysPlayable) return true
        return requirement.allows(rank.value)
    }

    /** Cards from the player's current source that follow the rules. Face-down cards are never "known". */
    fun fittingCards(player: ZpPlayerState, pile: DiscardPile, rules: ZpHouseRules): List<Card> {
        val source = player.activeSource ?: return emptyList()
        if (source == CardSource.FACE_DOWN) return emptyList()
        return player.cardsIn(source).filter { canPlayRank(it.rank, pile, rules) && !bounceBlocked(player, it, pile) }
    }

    /** How often per game a player may lay the card they just picked up straight back on the pile. */
    const val MAX_BOUNCES = 3

    /**
     * Laying [card] again would be one bounce too many (stops two players passing a 7 back and forth forever). Only
     * when there is something else to do: another card, or taking the pile.
     */
    fun bounceBlocked(player: ZpPlayerState, card: Card, pile: DiscardPile): Boolean {
        if (player.bounceCard != card || player.bounces < MAX_BOUNCES) return false
        val source = player.activeSource ?: return false
        return !pile.isEmpty || player.cardsIn(source).any { it != card }
    }

    /**
     * Cards the engine will accept: the fitting ones when rules are enforced, otherwise every card from the
     * current source (cheating is up to the players to spot, like at a real table).
     */
    fun playableCards(player: ZpPlayerState, pile: DiscardPile, rules: ZpHouseRules): List<Card> {
        if (rules.enforceRules) return fittingCards(player, pile, rules)
        val source = player.activeSource ?: return emptyList()
        if (source == CardSource.FACE_DOWN) return emptyList()
        return player.cardsIn(source).filter { !bounceBlocked(player, it, pile) }
    }

    /** Sort key used to find the starting player: lowest ordinary card wins; always-playable ranks are excluded. */
    fun startCandidate(hand: List<Card>, rules: ZpHouseRules): Card? =
        hand.filter { !rules.effectOf(it.rank).alwaysPlayable }.minOrNull()

    /** Validates player count, hand size and whether the deck has enough cards. Returns a reason or null. */
    fun validateSetup(playerCount: Int, rules: ZpHouseRules): ZpRejectReason? = when {
        playerCount < ZpModule.MIN_PLAYERS -> ZpRejectReason.TOO_FEW_PLAYERS
        playerCount > ZpModule.MAX_PLAYERS -> ZpRejectReason.TOO_MANY_PLAYERS
        rules.handSize !in ZpHouseRules.MIN_HAND_SIZE..ZpHouseRules.MAX_HAND_SIZE -> ZpRejectReason.INVALID_HAND_SIZE
        playerCount * cardsPerPlayer(rules) > 52 -> ZpRejectReason.NOT_ENOUGH_CARDS
        else -> null
    }

    fun cardsPerPlayer(rules: ZpHouseRules): Int =
        rules.handSize + ZpHouseRules.FACE_UP_COUNT + ZpHouseRules.FACE_DOWN_COUNT

    /** Largest number of players the deck supports with these rules (capped by the game maximum). */
    fun maxPlayersFor(rules: ZpHouseRules): Int = minOf(ZpModule.MAX_PLAYERS, 52 / cardsPerPlayer(rules))

    /** How valuable a card is to hold: burn and reset cards first, then the highest value. */
    fun keepValue(card: Card, rules: ZpHouseRules): Int = when (rules.effectOf(card.rank)) {
        ZpEffect.BURN -> 120
        ZpEffect.RESET -> 110
        else -> card.rank.value
    }
}
