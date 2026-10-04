package nl.bluecard.engine.pesten

import nl.bluecard.engine.core.CheatWindow
import nl.bluecard.engine.core.PlayerStats
import nl.bluecard.engine.core.bump
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.core.RankingEntry
import nl.bluecard.engine.core.WinnerExchange
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Deck
import nl.bluecard.engine.model.DiscardPile
import nl.bluecard.engine.model.PlayerInfo
import nl.bluecard.engine.model.Suit
import kotlin.random.Random

/**
 * The Pesten state machine. Pure and deterministic: given the same state and action it always produces the
 * same result. Every player action goes through [apply], which validates it completely.
 */
object PsEngine {

    private const val MAX_LOG_ENTRIES = 150

    fun newGame(players: List<PlayerInfo>, rules: PsHouseRules, seed: Long, previous: GameResult?): PsGameState {
        val game = newGame(players, rules, seed)
        val exchange = WinnerExchange.after(previous, rules.winnerSwap, players.map { it.id }) ?: return game
        return game.copy(exchange = exchange)
    }

    fun newGame(players: List<PlayerInfo>, rules: PsHouseRules, seed: Long): PsGameState {
        PsRules.validateSetup(players.size, rules)?.let { reason ->
            throw IllegalArgumentException("Invalid setup: $reason")
        }
        require(players.map { it.id }.toSet().size == players.size) { "Player ids must be unique" }

        var deck = Deck.withJokers(rules.jokers).shuffled(Random(seed))
        val dealt = players.map { info ->
            val (hand, rest) = deck.draw(rules.handSize)
            deck = rest
            PsPlayerState(id = info.id, name = info.name, hand = hand.sorted())
        }
        // The game starts on an ordinary card: special cards are put back under the deck.
        var startCard: Card? = null
        repeat(deck.size) {
            if (startCard != null) return@repeat
            val (drawn, rest) = deck.draw(1)
            val card = drawn.single()
            if (PsRules.isSpecial(card, rules)) {
                deck = rest.withCardsUnderneath(listOf(card))
            } else {
                startCard = card
                deck = rest
            }
        }
        val start = startCard ?: deck.draw(1).first.single().also { deck = deck.draw(1).second }
        val starter = players[Random(seed xor 0x5EED_CAFEL).nextInt(players.size)].id
        return PsGameState(
            rules = rules,
            players = dealt,
            drawPile = deck,
            discardPile = DiscardPile(listOf(start)),
            currentPlayerId = starter,
            seed = seed,
        ).log(PsEvent.GameStarted(starter, start))
    }

    fun apply(state: PsGameState, playerId: String, action: PsAction): ActionResult<PsGameState> {
        val player = state.player(playerId) ?: return reject(PsRejectReason.UNKNOWN_PLAYER)
        if (state.phase == PsPhase.FINISHED) return reject(PsRejectReason.GAME_FINISHED)

        // First the winner of the last round gives the loser a card; nothing else until then.
        val exchange = state.exchange
        if (exchange != null || action is PsAction.GiveCard) {
            if (action !is PsAction.GiveCard || exchange == null) return reject(PsRejectReason.EXCHANGE_PENDING)
            if (player.id != exchange.winnerId) return reject(PsRejectReason.NOT_THE_WINNER)
            return giveCard(state, player, exchange, action.card)
        }
        val result = when (action) {
            // These may be called out of turn.
            is PsAction.Challenge -> challenge(state, player, action.playId)
            PsAction.CallLastCard -> callLastCard(state, player)
            PsAction.CatchLastCard -> catchLastCard(state, player)
            else -> {
                if (state.currentPlayerId != playerId) return reject(PsRejectReason.NOT_YOUR_TURN)
                when (action) {
                    is PsAction.Play -> play(state, player, action.cards, action.suit)
                    PsAction.Draw -> draw(state, player)
                    PsAction.Pass -> pass(state, player)
                    is PsAction.Challenge, PsAction.CallLastCard, PsAction.CatchLastCard, is PsAction.GiveCard -> error("handled above")
                }
            }
        }
        return when (result) {
            is ActionResult.Accepted -> {
                // Drawing or passing closes the window for "Vergeten!" ("Vals!" stays open for a while, see tick).
                val closes = action == PsAction.Draw || action == PsAction.Pass
                val next = if (closes) result.state.copy(forgottenId = null) else result.state
                ActionResult.Accepted(closeFinished(next).copy(version = state.version + 1))
            }
            is ActionResult.Rejected -> result
        }
    }

    /** The winner's card goes to the loser, the loser's best card to the winner. */
    private fun giveCard(state: PsGameState, winner: PsPlayerState, exchange: WinnerExchange, card: Card): ActionResult<PsGameState> {
        if (card !in winner.hand) return reject(PsRejectReason.CARD_NOT_AVAILABLE)
        val loser = requireNotNull(state.player(exchange.loserId))
        val best = loser.hand.maxWithOrNull(compareBy<Card>({ PsRules.keepValue(it, state.rules) }, { it }))
            ?: return reject(PsRejectReason.CARD_NOT_AVAILABLE)
        val s = state
            .withPlayer(winner.copy(hand = (winner.hand - card + best).sorted()))
            .withPlayer(loser.copy(hand = (loser.hand - best + card).sorted()))
            .copy(exchange = null, version = state.version + 1)
            .log(PsEvent.CardsExchanged(winner.id, loser.id))
        return accept(s)
    }

    /** Giving up: the game ends now, [playerId] last and the others ranked by the cards they have left. */
    fun concede(state: PsGameState, playerId: String): PsGameState {
        if (state.phase == PsPhase.FINISHED || state.player(playerId)?.isFinished != false) return state
        var s = state.log(PsEvent.Resigned(playerId))
        for (p in s.activePlayers.filter { it.id != playerId }.sortedBy { it.hand.size } + listOfNotNull(s.player(playerId))) {
            s = s.withPlayer(p.copy(finishedPosition = s.finishOrder.size + 1)).copy(finishOrder = s.finishOrder + p.id)
        }
        return s.copy(
            phase = PsPhase.FINISHED,
            currentPlayerId = null,
            pendingDraw = 0,
            drawnCard = null,
            forgottenId = null,
            plays = emptyList(),
            version = state.version + 1,
        ).log(PsEvent.GameOver(s.finishOrder.first(), playerId))
    }

    /** Final standings, or null while the game is still running. */
    fun result(state: PsGameState): GameResult? {
        if (state.phase != PsPhase.FINISHED) return null
        val ranked = state.players.filter { it.finishedPosition != null }.sortedBy { it.finishedPosition }
        return GameResult(ranked.map { RankingEntry(it.id, it.name, it.finishedPosition ?: 0, it.hand.size) }, stats = state.stats)
    }

    // ---------------------------------------------------------------- turn actions

    private fun play(state: PsGameState, player: PsPlayerState, cards: List<Card>, suit: Suit?): ActionResult<PsGameState> {
        val rules = state.rules
        if (cards.isEmpty()) return reject(PsRejectReason.EMPTY_SELECTION)
        if (cards.toSet().size != cards.size) return reject(PsRejectReason.DUPLICATE_CARDS)
        if (!player.hand.containsAll(cards)) return reject(PsRejectReason.CARD_NOT_AVAILABLE)
        val first = cards.first()
        if (cards.any { it.rank != first.rank }) return reject(PsRejectReason.MIXED_RANKS)
        if (cards.size > 1 && !rules.allowMultiple) return reject(PsRejectReason.MULTIPLE_NOT_ALLOWED)
        if (PsRules.needsSuit(first.rank, rules) && suit == null) return reject(PsRejectReason.MUST_CHOOSE_SUIT)

        val illegal = PsRules.whyIllegal(state, cards)
        if (rules.enforceRules && illegal != null) return reject(illegal)
        // Going out with a cheated card is never allowed: there would be nobody left to catch.
        if (illegal != null && player.hand.size == cards.size) return reject(PsRejectReason.LAST_CARD_MUST_FIT)

        val chosen = suit.takeIf { PsRules.needsSuit(first.rank, rules) }
        val laid = state.withPlayer(player.copy(hand = player.hand - cards.toSet()))
            .copy(discardPile = state.discardPile.plus(cards), drawnCard = null)
            .log(PsEvent.CardsPlayed(player.id, cards, chosen))
        val resolved = resolve(laid, player.id, cards, chosen)
        if (rules.enforceRules) return accept(resolved)
        val play = PsLastPlay(
            playerId = player.id,
            cards = cards,
            legal = illegal == null,
            before = state.copy(plays = emptyList(), log = emptyList()),
            topBefore = state.discardPile.top,
            wishedSuitBefore = state.wishedSuit,
            pendingDrawBefore = state.pendingDraw,
            id = state.nextLogSeq,
            logSeqAfter = resolved.nextLogSeq,
        )
        // Only the newest play can still be undone completely; older ones drop their snapshot.
        return accept(resolved.copy(plays = resolved.plays.map { it.copy(before = null) } + play))
    }

    /** Applies the effect of the cards just laid, handles going out and passes the turn. */
    private fun resolve(state: PsGameState, playerId: String, cards: List<Card>, suit: Suit?): PsGameState {
        val rules = state.rules
        val card = cards.first()
        val effect = PsRules.effectOf(card, rules)
        val draws = PsRules.drawAmount(card, rules) * cards.size
        var s = state.copy(
            wishedSuit = suit,
            pendingDraw = if (draws > 0) state.pendingDraw + draws else 0,
        )

        if (s.player(playerId)?.hand?.isEmpty() == true) {
            if (PsRules.isSpecial(card, rules) && !rules.finishOnSpecial) {
                s = drawCards(s, playerId, 1).first.log(PsEvent.SpecialFinishPenalty(playerId))
            } else {
                s = markFinished(s, playerId)
                if (isGameOver(s)) return finishGame(s)
            }
        }

        val player = requireNotNull(s.player(playerId))
        val forgot = rules.lastCardCall && !player.isFinished && player.hand.size == 1 && !player.announced
        s = s.copy(forgottenId = if (forgot) playerId else null)

        val active = !player.isFinished
        s = when (effect) {
            PsEffect.PLAY_AGAIN -> if (active) s.log(PsEvent.PlaysAgain(playerId)) else advance(s, playerId, 1)
            PsEffect.SKIP -> advance(s, playerId, 1 + cards.size)
            PsEffect.REVERSE -> {
                val flips = cards.size % 2 == 1
                val direction = if (flips) -s.direction else s.direction
                s = s.copy(direction = direction).log(PsEvent.DirectionReversed(playerId, direction))
                // With two players a reversal means: your turn again.
                if (flips && active && s.activePlayers.size == 2) s.log(PsEvent.PlaysAgain(playerId)) else advance(s, playerId, 1)
            }
            else -> advance(s, playerId, 1)
        }
        val next = s.currentPlayerId
        if (s.pendingDraw > 0 && next != null) s = s.log(PsEvent.MustDraw(next, s.pendingDraw))
        return s
    }

    private fun draw(state: PsGameState, player: PsPlayerState): ActionResult<PsGameState> {
        if (state.drawnCard != null) return reject(PsRejectReason.ALREADY_DRAWN)
        if (state.pendingDraw > 0) {
            val (s, drawn) = drawCards(state, player.id, state.pendingDraw)
            val logged = s.copy(pendingDraw = 0).log(PsEvent.CardsDrawn(player.id, drawn.size, penalty = true))
            // House rule: whoever took the pak-cards may start again themselves.
            if (state.rules.playAfterPenalty) return accept(logged.log(PsEvent.PlaysAfterPenalty(player.id)))
            return accept(advance(logged, player.id, 1))
        }
        val (s, drawn) = drawCards(state, player.id, 1)
        if (drawn.isEmpty()) {
            // Nothing left to draw at all: the turn simply passes.
            return accept(advance(s.log(PsEvent.Passed(player.id)), player.id, 1))
        }
        return accept(s.copy(drawnCard = drawn.single()).log(PsEvent.CardsDrawn(player.id, 1, penalty = false)))
    }

    private fun pass(state: PsGameState, player: PsPlayerState): ActionResult<PsGameState> {
        if (state.drawnCard == null) return reject(PsRejectReason.NOT_DRAWN_YET)
        val s = state.copy(drawnCard = null).log(PsEvent.Passed(player.id))
        return accept(advance(s, player.id, 1))
    }

    // ---------------------------------------------------------------- out-of-turn calls

    private fun callLastCard(state: PsGameState, player: PsPlayerState): ActionResult<PsGameState> {
        if (!state.rules.lastCardCall || player.isFinished || player.announced || player.hand.size !in 1..2) {
            return reject(PsRejectReason.LAST_CARD_NOT_ALLOWED)
        }
        val s = state.withPlayer(player.copy(announced = true)).log(PsEvent.LastCardCalled(player.id))
        return accept(if (s.forgottenId == player.id) s.copy(forgottenId = null) else s)
    }

    private fun catchLastCard(state: PsGameState, catcher: PsPlayerState): ActionResult<PsGameState> {
        val target = state.forgottenId ?: return reject(PsRejectReason.NOTHING_TO_CATCH)
        if (target == catcher.id) return reject(PsRejectReason.NOTHING_TO_CATCH)
        if (catcher.isFinished) return reject(PsRejectReason.NOT_PLAYING)
        val (s, drawn) = drawCards(state.copy(forgottenId = null), target, state.rules.lastCardPenalty)
        return accept(s.log(PsEvent.LastCardForgotten(catcher.id, target, drawn.size)))
    }

    /**
     * "Vals!" on [playId] (null: the newest play by someone else). Caught right away — nothing happened since — the
     * cheat is undone completely: the cheater takes the cards back, draws what they tried to dodge plus the penalty
     * and loses the turn. Caught later, after others have played, the cheater draws the penalty. A false
     * accusation costs the accuser the penalty.
     */
    private fun challenge(state: PsGameState, accuser: PsPlayerState, playId: Long?): ActionResult<PsGameState> {
        if (accuser.isFinished) return reject(PsRejectReason.NOT_PLAYING)
        val target = if (playId != null) state.plays.firstOrNull { it.id == playId } else state.plays.lastOrNull { it.playerId != accuser.id }
        if (target == null) {
            val onlyOwn = state.plays.any { it.playerId == accuser.id }
            return reject(if (onlyOwn) PsRejectReason.CANNOT_CHALLENGE_SELF else PsRejectReason.NOTHING_TO_CHALLENGE)
        }
        if (target.playerId == accuser.id) return reject(PsRejectReason.CANNOT_CHALLENGE_SELF)
        val others = state.plays - target
        val penalty = state.rules.challengePenalty

        if (target.legal) {
            val (s, drawn) = drawCards(state.copy(plays = others), accuser.id, penalty)
            return accept(s.log(PsEvent.FalseAccusation(accuser.id, target.playerId, drawn.size)))
        }

        val before = target.before
        val immediate = before != null && state.nextLogSeq == target.logSeqAfter
        // Escalating penalty (house rule): every next time caught costs one card more.
        val extra = if (state.rules.escalatingPenalty) requireNotNull(state.player(target.playerId)).cheatsCaught else 0
        if (!immediate) {
            val cheater = requireNotNull(state.player(target.playerId))
            val counted = state.withPlayer(cheater.copy(cheatsCaught = cheater.cheatsCaught + 1)).copy(plays = others)
            val (s, drawn) = drawCards(counted, cheater.id, penalty + extra)
            return accept(s.log(PsEvent.CheatCaught(accuser.id, cheater.id, target.cards, drawn.size)))
        }
        val restored = before!!.copy(
            log = state.log,
            nextLogSeq = state.nextLogSeq,
            version = state.version,
            plays = others,
            forgottenId = null,
            drawnCard = null,
            pendingDraw = 0,
        )
        val cheater = requireNotNull(restored.player(target.playerId))
        val counted = restored.withPlayer(cheater.copy(cheatsCaught = cheater.cheatsCaught + 1))
        val (s, drawn) = drawCards(counted, target.playerId, before.pendingDraw + penalty + extra)
        val logged = s.log(PsEvent.CheatCaught(accuser.id, target.playerId, target.cards, drawn.size))
        return accept(advance(logged, target.playerId, 1))
    }

    /**
     * The host's clock: stamps new plays and closes the "Vals!" window of plays older than [CheatWindow.MS]
     * (an uncaught cheat counts for the ninja).
     */
    fun tick(state: PsGameState, nowMs: Long): PsGameState {
        if (state.plays.isEmpty()) return state
        val stamped = state.plays.map { if (it.atMs == null) it.copy(atMs = nowMs) else it }
        val (open, closed) = stamped.partition { nowMs - (it.atMs ?: nowMs) < CheatWindow.MS }
        return when {
            closed.isNotEmpty() -> escaped(state.copy(plays = open, version = state.version + 1), closed)
            stamped != state.plays -> state.copy(plays = stamped)
            else -> state
        }
    }

    /** When the oldest open play's window closes (or now, for a play not stamped yet). */
    fun nextTickAt(state: PsGameState): Long? =
        state.plays.minOfOrNull { it.atMs?.plus(CheatWindow.MS) ?: 0L }

    // ---------------------------------------------------------------- helpers

    /**
     * Gives [count] cards from the draw pile to the player. When the draw pile runs short, the discard pile
     * (except its top card) is shuffled into a new draw pile. Returns the new state and the cards drawn.
     */
    private fun drawCards(state: PsGameState, playerId: String, count: Int): Pair<PsGameState, List<Card>> {
        var s = state
        if (s.drawPile.size < count && s.discardPile.size > 1) {
            val top = requireNotNull(s.discardPile.top)
            val recycled = Deck(s.discardPile.cards.dropLast(1)).shuffled(Random(s.seed xor s.version xor s.nextLogSeq xor 0x0DECAL))
            s = s.copy(drawPile = s.drawPile.withCardsUnderneath(recycled.cards), discardPile = DiscardPile(listOf(top)))
                .log(PsEvent.DrawPileReshuffled(recycled.size))
        }
        val (drawn, rest) = s.drawPile.draw(count)
        val player = requireNotNull(s.player(playerId))
        // Whoever draws has to call "Laatste kaart!" again before going down to one card.
        val announced = player.announced && drawn.isEmpty()
        return s.withPlayer(player.copy(hand = (player.hand + drawn).sorted(), announced = announced)).copy(drawPile = rest) to drawn
    }

    private fun markFinished(state: PsGameState, playerId: String): PsGameState {
        val player = requireNotNull(state.player(playerId))
        val position = state.finishOrder.size + 1
        return state.withPlayer(player.copy(finishedPosition = position))
            .copy(finishOrder = state.finishOrder + playerId)
            .log(PsEvent.PlayerFinished(playerId, position))
    }

    private fun isGameOver(state: PsGameState): Boolean =
        state.activePlayers.size <= 1 || (!state.rules.playUntilLast && state.finishOrder.isNotEmpty())

    /** Assigns the remaining positions (fewest cards first, then seat order) and ends the game. */
    private fun finishGame(state: PsGameState): PsGameState {
        var s = state
        for (p in s.activePlayers.sortedBy { it.hand.size }) {
            s = s.withPlayer(p.copy(finishedPosition = s.finishOrder.size + 1)).copy(finishOrder = s.finishOrder + p.id)
        }
        val winner = s.finishOrder.first()
        val loser = s.finishOrder.last().takeIf { s.finishOrder.size > 1 }
        return s.copy(
            phase = PsPhase.FINISHED,
            currentPlayerId = null,
            pendingDraw = 0,
            drawnCard = null,
            forgottenId = null,
        ).log(PsEvent.GameOver(winner, loser))
    }

    /**
     * Moves the turn [steps] active players onward from [fromId] in the current direction.
     * Every active player passed over except the last one is "skipped".
     */
    private fun advance(state: PsGameState, fromId: String, steps: Int): PsGameState {
        val players = state.players
        val n = players.size
        var index = state.indexOf(fromId)
        val skipped = mutableListOf<String>()
        var remaining = steps
        var guard = 0
        while (remaining > 0 && guard < n * (steps + 1)) {
            index = (index + state.direction).mod(n)
            guard++
            if (!players[index].isFinished) {
                remaining--
                if (remaining > 0) skipped += players[index].id
            }
        }
        var s = state.copy(currentPlayerId = players[index].id, drawnCard = null)
        if (skipped.isNotEmpty()) s = s.log(PsEvent.PlayersSkipped(fromId, skipped))
        return s
    }

    /** Replaces a player; "Laatste kaart!" no longer counts once the hand has grown beyond two cards. */
    private fun PsGameState.withPlayer(updated: PsPlayerState): PsGameState {
        val normalized = if (updated.announced && updated.hand.size > 2) updated.copy(announced = false) else updated
        return copy(players = players.map { if (it.id == normalized.id) normalized else it })
    }

    private fun PsGameState.log(event: PsEvent): PsGameState =
        copy(log = (log + PsLogEntry(nextLogSeq, event)).takeLast(MAX_LOG_ENTRIES), nextLogSeq = nextLogSeq + 1, stats = count(stats, event))

    /** Plays of players who are out (or of a finished game) can no longer be called out. */
    private fun closeFinished(state: PsGameState): PsGameState {
        if (state.plays.isEmpty()) return state
        val (open, closed) = state.plays.partition { state.phase == PsPhase.PLAYING && state.player(it.playerId)?.isFinished == false }
        return if (closed.isEmpty()) state else escaped(state.copy(plays = open), closed)
    }

    /** Illegal plays whose "Vals!" window closed without anybody catching them: one each for the ninja. */
    private fun escaped(state: PsGameState, closed: List<PsLastPlay>): PsGameState =
        closed.filter { !it.legal }.fold(state) { s, cheat ->
            s.copy(stats = s.stats.bump(cheat.playerId) { copy(cheatsUnnoticed = cheatsUnnoticed + 1) })
        }

    /** Keeps the counters for the summary up to date; every event passes through here. */
    private fun count(stats: Map<String, PlayerStats>, event: PsEvent): Map<String, PlayerStats> = when (event) {
        is PsEvent.CardsPlayed -> stats.bump(event.playerId) { copy(cardsPlayed = cardsPlayed + event.cards.size) }
        is PsEvent.CardsDrawn -> stats.bump(event.playerId) { copy(cardsDrawn = cardsDrawn + event.count) }
        is PsEvent.Passed -> stats.bump(event.playerId) { copy(passes = passes + 1) }
        is PsEvent.LastCardCalled -> stats.bump(event.playerId) { copy(lastCardCalls = lastCardCalls + 1) }
        is PsEvent.LastCardForgotten -> stats.bump(event.playerId) { copy(lastCardForgotten = lastCardForgotten + 1) }
            .bump(event.catcherId) { copy(forgetsCaught = forgetsCaught + 1) }
        is PsEvent.CheatCaught -> stats.bump(event.accuserId) { copy(cheatsSpotted = cheatsSpotted + 1) }
            .bump(event.cheaterId) { copy(cheatsCaught = cheatsCaught + 1) }
        is PsEvent.FalseAccusation -> stats.bump(event.accuserId) { copy(falseCalls = falseCalls + 1) }
        else -> stats
    }

    private fun accept(state: PsGameState): ActionResult<PsGameState> = ActionResult.Accepted(state)

    private fun reject(reason: PsRejectReason): ActionResult<Nothing> = ActionResult.Rejected(reason.name)
}
