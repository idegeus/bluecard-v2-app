package nl.bluecard.engine.pesten

import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit
import kotlin.random.Random

/**
 * Computer player for Pesten. Decides purely from its own [PsPlayerView] and only plays cards that follow
 * the rules, so it never cheats. It does call "Vals!" and "Vergeten!" on others.
 */
object PsBot {

    private const val NORMAL_RANDOM_PERCENT = 10

    fun chooseAction(view: PsPlayerView, difficulty: BotDifficulty, random: Random): PsAction {
        val legal = view.legal
        val easy = difficulty == BotDifficulty.EASY
        // Winner's exchange: give away the least useful card.
        if (legal.giveCards.isNotEmpty()) {
            return PsAction.GiveCard(legal.giveCards.minWith(compareBy<Card>({ PsRules.keepValue(it, view.rules) }, { it })))
        }

        val cheat = view.challenges.lastOrNull { it.playerId != view.viewerId && !looksLegal(it, view.rules) }
        if (legal.canChallenge && cheat != null && random.nextInt(100) < if (easy) 35 else 85) {
            return PsAction.Challenge(cheat.id)
        }
        if (legal.canCatch && random.nextInt(100) < if (easy) 40 else 90) return PsAction.CatchLastCard
        // Announce before laying the second-to-last card (easy bots sometimes forget).
        if (legal.canCallLastCard && view.isMyTurn && view.myHand.size == 2 && (!easy || random.nextInt(100) < 70)) {
            return PsAction.CallLastCard
        }
        if (!view.isMyTurn) return PsAction.Draw // never requested: bots only act on their own turn

        val fitting = legal.fittingCards
        view.drawnCard?.let { drawn ->
            return if (drawn in fitting) play(view, listOf(drawn)) else PsAction.Pass
        }
        if (fitting.isEmpty()) return PsAction.Draw
        if (view.pendingDraw > 0) {
            // Answering a "pak"-card with another one passes the bill on.
            return if (!easy || random.nextBoolean()) play(view, listOf(fitting.first())) else PsAction.Draw
        }
        val card = when {
            easy || random.nextInt(100) < NORMAL_RANDOM_PERCENT -> fitting.random(random)
            else -> chooseNormal(view, fitting)
        }
        val sameRank = if (legal.canPlayMultiple && !easy) fitting.filter { it.rank == card.rank } else listOf(card)
        return play(view, sameRank)
    }

    private fun play(view: PsPlayerView, cards: List<Card>): PsAction {
        val first = cards.first()
        val suit = if (PsRules.needsSuit(first.rank, view.rules)) bestSuit(view.myHand - cards.toSet(), first.suit) else null
        return PsAction.Play(cards, suit)
    }

    /** The suit the bot holds most of (after this play). */
    private fun bestSuit(rest: List<Card>, fallback: Suit): Suit =
        rest.filter { !it.rank.isJoker }.groupingBy { it.suit }.eachCount().maxByOrNull { it.value }?.key ?: fallback

    private fun chooseNormal(view: PsPlayerView, fitting: List<Card>): Card {
        val rules = view.rules
        val next = nextPlayer(view)
        // Someone is about to go out: attack with a pak-card or a skip.
        if (next != null && next.handCount <= 2) {
            fitting.firstOrNull { PsRules.isDrawCard(it, rules) }?.let { return it }
            fitting.firstOrNull { rules.effectOf(it.rank) == PsEffect.SKIP }?.let { return it }
        }
        val ordinary = fitting.filter { !PsRules.isSpecial(it, rules) }
        // Don't end up with only a special card: going out on one is not allowed by default.
        if (!rules.finishOnSpecial && view.myHand.size == 2) {
            fitting.firstOrNull { PsRules.isSpecial(it, rules) }?.let { return it }
        }
        // Play-again cards are free moves; otherwise shed ordinary cards of the suit we hold most of.
        fitting.firstOrNull { rules.effectOf(it.rank) == PsEffect.PLAY_AGAIN && !it.rank.isJoker }?.let { return it }
        if (ordinary.isNotEmpty()) {
            val counts = view.myHand.groupingBy { it.suit }.eachCount()
            return ordinary.maxWith(compareBy<Card>({ counts[it.suit] ?: 0 }, { it.rank.value }))
        }
        // Keep jokers and jacks for last.
        return fitting.minBy {
            when {
                it.rank.isJoker -> 3
                rules.effectOf(it.rank) == PsEffect.CHOOSE_SUIT -> 2
                else -> 1
            }
        }
    }

    /** Whether the last play looks allowed from what was on the table (bots cannot see the drawn card). */
    private fun looksLegal(last: PsChallengeView, rules: PsHouseRules): Boolean {
        val card = last.cards.first()
        if (last.pendingDrawBefore > 0) return rules.stackDraws && PsRules.isDrawCard(card, rules)
        if (PsRules.isWild(card, rules)) return true
        last.wishedSuitBefore?.let { return card.suit == it }
        val top = last.topBefore ?: return true
        return top.rank.isJoker || card.suit == top.suit || card.rank == top.rank
    }

    private fun nextPlayer(view: PsPlayerView): PsPublicPlayer? {
        val players = view.players
        val me = players.indexOfFirst { it.id == view.viewerId }
        if (me < 0) return null
        var index = me
        repeat(players.size) {
            index = (index + view.direction).mod(players.size)
            val candidate = players[index]
            if (candidate.id != view.viewerId && candidate.finishedPosition == null) return candidate
        }
        return null
    }
}
