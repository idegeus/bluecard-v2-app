package nl.bluecard.engine.zweedspesten

import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Rank
import kotlin.random.Random

/**
 * Computer player. Decides purely from its own [ZpPlayerView] (no peeking at hidden cards) and only
 * picks from the engine-provided legal moves, so it can never cheat or produce an invalid move.
 */
object ZpBot {

    fun chooseAction(view: ZpPlayerView, difficulty: BotDifficulty, random: Random): ZpAction {
        val legal = view.legal
        // Winner's exchange: give away the least useful card.
        if (legal.giveCards.isNotEmpty()) {
            return ZpAction.GiveCard(legal.giveCards.minWith(compareBy<Card>({ ZpRules.keepValue(it, view.rules) }, { it })))
        }
        return when (view.phase) {
            ZpPhase.SWAPPING -> chooseSwap(view, difficulty)
            ZpPhase.PLAYING -> choosePlay(view, legal, difficulty, random)
            ZpPhase.FINISHED -> ZpAction.Ready // never requested: finished games have no pending actors
        }
    }

    // ---------------------------------------------------------------- swap phase

    private fun chooseSwap(view: ZpPlayerView, difficulty: BotDifficulty): ZpAction {
        if (!view.legal.canSwap || difficulty == BotDifficulty.EASY) return ZpAction.Ready
        val faceUp = view.me?.faceUp ?: return ZpAction.Ready
        val hand = view.myHand
        // Keep the strongest cards face-up for the end game; shed weak cards from the hand first.
        val desiredFaceUp = (hand + faceUp)
            // Total order (score, then the card itself) so the desired set never flip-flops between swaps.
            .sortedWith(compareByDescending<Card> { keepScore(it, view.rules) }.thenByDescending { it })
            .take(faceUp.size)
            .toSet()
        val promote = hand.firstOrNull { it in desiredFaceUp }
        val demote = faceUp.firstOrNull { it !in desiredFaceUp }
        return if (promote != null && demote != null) ZpAction.Swap(promote, demote) else ZpAction.Ready
    }

    /** Higher means "more valuable to keep for later". */
    private fun keepScore(card: Card, rules: ZpHouseRules): Int = when (rules.effectOf(card.rank)) {
        ZpEffect.BURN -> 120
        ZpEffect.RESET -> 110
        else -> card.rank.value
    }

    // ---------------------------------------------------------------- playing

    private fun choosePlay(
        view: ZpPlayerView,
        legal: ZpLegalMoves,
        difficulty: BotDifficulty,
        random: Random,
    ): ZpAction {
        // Bots never cheat, but they do watch the pile: a play that broke the rules is called out.
        val cheat = view.challenges.lastOrNull { play ->
            play.playerId != view.viewerId && play.cards.any { !ZpRules.fits(it.rank, play.requirementBefore, view.rules) }
        }
        if (legal.canChallenge && cheat != null && random.nextInt(100) < if (difficulty == BotDifficulty.EASY) 35 else 85) {
            return ZpAction.Challenge(cheat.id)
        }
        if (legal.source == CardSource.FACE_DOWN && legal.blindIndices.isNotEmpty()) {
            return ZpAction.PlayBlind(legal.blindIndices[random.nextInt(legal.blindIndices.size)])
        }
        // Bots never cheat: they only consider cards that follow the rules.
        if (legal.fittingCards.isEmpty()) {
            return when {
                legal.canGamble -> ZpAction.Gamble
                legal.canPickUp -> ZpAction.PickUp
                // Unreachable for a valid view; Ready will simply be rejected and logged by the host.
                else -> ZpAction.Ready
            }
        }
        val byRank: Map<Rank, List<Card>> = legal.fittingCards.groupBy { it.rank }
        val rank = when (difficulty) {
            // Easy: half of the time the lowest card, otherwise anything that fits.
            BotDifficulty.EASY -> if (random.nextBoolean()) byRank.keys.minBy { it.value } else byRank.keys.random(random)
            BotDifficulty.NORMAL ->
                // A little unpredictability makes the bot feel human and prevents endless repeating patterns.
                if (random.nextInt(100) < NORMAL_RANDOM_PERCENT) byRank.keys.random(random) else chooseRankNormal(view, byRank)
        }
        val cards = byRank.getValue(rank)
        val playAll = legal.canPlayMultiple && (difficulty == BotDifficulty.NORMAL || random.nextBoolean())
        return ZpAction.Play(if (playAll) cards else listOf(cards.first()))
    }

    /** The next active player after the viewer in the current direction of play. */
    private fun nextPlayer(view: ZpPlayerView): ZpPublicPlayer? {
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

    private const val NORMAL_RANDOM_PERCENT = 15

    private fun chooseRankNormal(view: ZpPlayerView, byRank: Map<Rank, List<Card>>): Rank {
        val rules = view.rules
        val pileSize = view.discardCount

        // Completing four of a kind burns the pile for free.
        if (rules.fourOfAKindBurns) {
            val top = view.discardTop.lastOrNull()
            if (top != null) {
                val run = view.discardTop.takeLastWhile { it.rank == top.rank }.size
                val mine = byRank[top.rank]?.size ?: 0
                if (mine > 0 && run + mine >= 4 && (rules.allowMultiple || mine == 1)) return top.rank
            }
        }

        val ordinary = byRank.keys.filter { !rules.effectOf(it).alwaysPlayable }
        val burnRank = byRank.keys.firstOrNull { rules.effectOf(it) == ZpEffect.BURN }

        // A big pile is worth burning even with a valuable card.
        if (burnRank != null && pileSize >= 6) return burnRank

        // The next player is close to going out: play the highest ordinary card (or a LOWER card) to make it hard.
        val next = nextPlayer(view)
        val threatened = next != null && next.totalCards in 1..2 && pileSize > 0
        if (threatened && ordinary.isNotEmpty()) {
            ordinary.firstOrNull { rules.effectOf(it) == ZpEffect.LOWER }?.let { return it }
            return ordinary.maxBy { it.value }
        }

        // Normal play: get rid of the lowest ordinary card, keep specials for emergencies.
        if (ordinary.isNotEmpty()) return ordinary.minBy { it.value }

        // Only specials are playable: use the least valuable one.
        return byRank.keys.minBy {
            when (rules.effectOf(it)) {
                ZpEffect.RESET -> 1
                ZpEffect.BURN -> 2
                else -> 3
            }
        }
    }
}
