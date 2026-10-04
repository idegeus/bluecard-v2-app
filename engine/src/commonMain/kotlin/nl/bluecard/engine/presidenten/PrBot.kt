package nl.bluecard.engine.presidenten

import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.Card
import kotlin.random.Random

/** Computer player for Presidenten. Plays from its own view only. */
object PrBot {

    fun chooseAction(view: PrPlayerView, difficulty: BotDifficulty, random: Random): PrAction {
        val rules = view.rules
        val legal = view.legal
        val strength = { c: Card -> PrRules.strength(c, rules) }
        if (legal.giveCount > 0) {
            // Give back the weakest cards.
            return PrAction.GiveCards(view.myHand.sortedWith(PrRules.handOrder(rules)).take(legal.giveCount))
        }
        if (legal.sets.isEmpty()) return PrAction.Pass
        val easy = difficulty == BotDifficulty.EASY
        if (easy && random.nextInt(100) < 30) {
            val pick = legal.sets.random(random)
            return PrAction.Play(pick)
        }

        val counts = view.myHand.filter { !it.rank.isJoker }.groupingBy { it.rank }.eachCount()
        // Prefer sets that use whole groups (no splitting a pair for a single), without jokers, as low as possible.
        fun cost(set: List<Card>): Int {
            val natural = set.filter { !it.rank.isJoker }
            val rank = natural.firstOrNull()?.rank
            val splits = if (rank != null && (counts[rank] ?: 0) > natural.size) 1 else 0
            val jokers = set.count { it.rank.isJoker }
            val power = PrRules.strength(PrRules.setRank(set)!!, rules)
            return splits * 30 + jokers * 40 + power
        }

        val best = if (view.top == null) {
            // Leading: get rid of the lowest value, as a whole group.
            legal.sets.filter { set -> set.none { it.rank.isJoker } }
                .filter { set -> set.size == (counts[set.first().rank] ?: 0) }
                .minByOrNull { strength(it.first()) }
                ?: legal.sets.minBy(::cost)
        } else {
            legal.sets.minBy(::cost)
        }

        // Keep the strongest cards for later unless the hand is almost empty or nothing else is left.
        val power = PrRules.strength(PrRules.setRank(best)!!, rules)
        val isPower = power >= PrRules.maxStrength(rules) - (if (rules.jokers > 0) 5 else 0) || best.any { it.rank.isJoker }
        if (view.top != null && isPower && view.myHand.size > best.size + 3 && random.nextInt(100) < (if (easy) 30 else 65)) {
            return PrAction.Pass
        }
        return PrAction.Play(best)
    }
}
