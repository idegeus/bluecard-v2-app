package nl.bluecard.engine.zweedspesten

import nl.bluecard.engine.core.CheatWindow
import nl.bluecard.engine.core.PlayerStats
import nl.bluecard.engine.core.bump
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.core.RankingEntry
import nl.bluecard.engine.core.WinnerExchange
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Deck
import nl.bluecard.engine.model.PlayerInfo
import kotlin.random.Random

/**
 * The Zweeds Pesten state machine. Pure and deterministic: given the same state and action it always
 * produces the same result. Every player action goes through [apply], which validates it completely.
 */
object ZpEngine {

    private const val MAX_LOG_ENTRIES = 150

    fun newGame(players: List<PlayerInfo>, rules: ZpHouseRules, seed: Long, previous: GameResult?): ZpGameState {
        val game = newGame(players, rules, seed)
        val exchange = WinnerExchange.after(previous, rules.winnerSwap, players.map { it.id }) ?: return game
        return game.copy(exchange = exchange)
    }

    fun newGame(players: List<PlayerInfo>, rules: ZpHouseRules, seed: Long): ZpGameState {
        ZpRules.validateSetup(players.size, rules)?.let { reason ->
            throw IllegalArgumentException("Invalid setup: $reason")
        }
        require(players.map { it.id }.toSet().size == players.size) { "Player ids must be unique" }

        var deck = Deck.standard52().shuffled(Random(seed))
        val dealt = players.map { info ->
            val (faceDown, afterDown) = deck.draw(ZpHouseRules.FACE_DOWN_COUNT)
            val (faceUp, afterUp) = afterDown.draw(ZpHouseRules.FACE_UP_COUNT)
            val (hand, afterHand) = afterUp.draw(rules.handSize)
            deck = afterHand
            ZpPlayerState(id = info.id, name = info.name, hand = hand.sorted(), faceUp = faceUp, faceDown = faceDown)
        }

        val base = ZpGameState(
            rules = rules,
            players = dealt,
            drawPile = deck,
            phase = ZpPhase.SWAPPING,
            seed = seed,
        )
        return if (rules.swapPhase) {
            base.log(ZpEvent.SwapPhaseStarted)
        } else {
            beginPlay(base)
        }
    }

    fun apply(state: ZpGameState, playerId: String, action: ZpAction): ActionResult<ZpGameState> {
        val player = state.player(playerId) ?: return reject(ZpRejectReason.UNKNOWN_PLAYER)
        if (state.phase == ZpPhase.FINISHED) return reject(ZpRejectReason.GAME_FINISHED)

        // First the winner of the last round gives the loser a card; nothing else until then.
        val exchange = state.exchange
        if (exchange != null || action is ZpAction.GiveCard) {
            if (action !is ZpAction.GiveCard || exchange == null) return reject(ZpRejectReason.EXCHANGE_PENDING)
            if (player.id != exchange.winnerId) return reject(ZpRejectReason.NOT_THE_WINNER)
            return giveCard(state, player, exchange, action.card)
        }
        val result = when (action) {
            is ZpAction.Swap -> swap(state, player, action)
            ZpAction.Ready -> ready(state, player)
            // Calling "Vals!" is allowed out of turn.
            is ZpAction.Challenge -> challenge(state, player, action.playId)
            else -> {
                if (state.phase != ZpPhase.PLAYING) return reject(ZpRejectReason.WRONG_PHASE)
                if (state.turn.currentPlayerId != playerId) return reject(ZpRejectReason.NOT_YOUR_TURN)
                when (action) {
                    is ZpAction.Play -> play(state, player, action.cards)
                    is ZpAction.PlayBlind -> playBlind(state, player, action.index)
                    ZpAction.PickUp -> pickUp(state, player)
                    ZpAction.Gamble -> gamble(state, player)
                    is ZpAction.Swap, ZpAction.Ready, is ZpAction.Challenge, is ZpAction.GiveCard -> error("handled above")
                }
            }
        }
        return when (result) {
            is ActionResult.Accepted -> ActionResult.Accepted(closeFinished(result.state).copy(version = state.version + 1))
            is ActionResult.Rejected -> result
        }
    }

    /** The winner's card goes to the loser, the loser's best hand card to the winner. */
    private fun giveCard(state: ZpGameState, winner: ZpPlayerState, exchange: WinnerExchange, card: Card): ActionResult<ZpGameState> {
        if (card !in winner.hand) return reject(ZpRejectReason.CARD_NOT_AVAILABLE)
        val loser = requireNotNull(state.player(exchange.loserId))
        val best = loser.hand.maxWithOrNull(compareBy<Card>({ ZpRules.keepValue(it, state.rules) }, { it }))
            ?: return reject(ZpRejectReason.CARD_NOT_AVAILABLE)
        val s = state
            .withPlayer(winner.copy(hand = (winner.hand - card + best).sorted()))
            .withPlayer(loser.copy(hand = (loser.hand - best + card).sorted()))
            .copy(exchange = null, version = state.version + 1)
            .log(ZpEvent.CardsExchanged(winner.id, loser.id))
        return accept(s)
    }

    /** Giving up: the game ends now, [playerId] last and the others ranked by the cards they have left. */
    fun concede(state: ZpGameState, playerId: String): ZpGameState {
        if (state.phase == ZpPhase.FINISHED || state.player(playerId)?.isFinished != false) return state
        var s = state.log(ZpEvent.Resigned(playerId))
        for (p in s.activePlayers.filter { it.id != playerId }.sortedBy { it.totalCards } + listOfNotNull(s.player(playerId))) {
            s = s.withPlayer(p.copy(finishedPosition = s.finishOrder.size + 1)).copy(finishOrder = s.finishOrder + p.id)
        }
        return s.copy(phase = ZpPhase.FINISHED, turn = s.turn.copy(currentPlayerId = null), plays = emptyList(), version = state.version + 1)
            .log(ZpEvent.GameOver(s.finishOrder.first(), playerId))
    }

    /** Final standings, or null while the game is still running. */
    fun result(state: ZpGameState): GameResult? {
        if (state.phase != ZpPhase.FINISHED) return null
        val ranked = state.players
            .filter { it.finishedPosition != null }
            .sortedBy { it.finishedPosition }
        return GameResult(
            ranked.map { RankingEntry(it.id, it.name, it.finishedPosition ?: 0, it.totalCards) },
            stats = state.stats,
        )
    }

    // ---------------------------------------------------------------- swap phase

    private fun swap(state: ZpGameState, player: ZpPlayerState, action: ZpAction.Swap): ActionResult<ZpGameState> {
        if (state.phase != ZpPhase.SWAPPING) return reject(ZpRejectReason.WRONG_PHASE)
        if (player.ready) return reject(ZpRejectReason.ALREADY_READY)
        if (action.handCard !in player.hand || action.faceUpCard !in player.faceUp) {
            return reject(ZpRejectReason.CARD_NOT_AVAILABLE)
        }
        val updated = player.copy(
            hand = (player.hand - action.handCard + action.faceUpCard).sorted(),
            faceUp = player.faceUp.map { if (it == action.faceUpCard) action.handCard else it },
        )
        return accept(state.withPlayer(updated).log(ZpEvent.Swapped(player.id)))
    }

    private fun ready(state: ZpGameState, player: ZpPlayerState): ActionResult<ZpGameState> {
        if (state.phase != ZpPhase.SWAPPING) return reject(ZpRejectReason.WRONG_PHASE)
        if (player.ready) return reject(ZpRejectReason.ALREADY_READY)
        val next = state.withPlayer(player.copy(ready = true)).log(ZpEvent.PlayerReady(player.id))
        return accept(if (next.players.all { it.ready }) beginPlay(next) else next)
    }

    private fun beginPlay(state: ZpGameState): ZpGameState {
        val rules = state.rules
        val (starterIndex, startCard) = when (rules.startRule) {
            StartRule.RANDOM -> Random(state.seed xor 0x5EED_CAFEL).nextInt(state.players.size) to null
            StartRule.LOWEST_CARD -> {
                val candidates = state.players.mapIndexedNotNull { index, p ->
                    ZpRules.startCandidate(p.hand, rules)?.let { index to it }
                }
                candidates.minByOrNull { it.second } ?: (0 to null)
            }
        }
        val starter = state.players[starterIndex]
        return state.copy(
            phase = ZpPhase.PLAYING,
            players = state.players.map { it.copy(ready = true) },
            turn = ZpTurnState(currentPlayerId = starter.id, direction = 1),
        ).log(ZpEvent.GameStarted(starter.id, startCard))
    }

    // ---------------------------------------------------------------- playing

    private fun play(state: ZpGameState, player: ZpPlayerState, cards: List<Card>): ActionResult<ZpGameState> {
        if (cards.isEmpty()) return reject(ZpRejectReason.EMPTY_SELECTION)
        if (cards.toSet().size != cards.size) return reject(ZpRejectReason.DUPLICATE_CARDS)
        val source = player.activeSource ?: return reject(ZpRejectReason.CARD_NOT_AVAILABLE)
        if (source == CardSource.FACE_DOWN) return reject(ZpRejectReason.MUST_PLAY_FACE_DOWN)
        val available = player.cardsIn(source)
        if (!available.containsAll(cards)) {
            val elsewhere = (player.hand + player.faceUp).containsAll(cards)
            return reject(if (elsewhere) ZpRejectReason.WRONG_SOURCE else ZpRejectReason.CARD_NOT_AVAILABLE)
        }
        if (cards.any { ZpRules.bounceBlocked(player, it, state.discardPile) }) return reject(ZpRejectReason.REPLAY_LIMIT)
        val rank = cards.first().rank
        if (cards.any { it.rank != rank }) return reject(ZpRejectReason.MIXED_RANKS)
        if (cards.size > 1 && !state.rules.allowMultiple) return reject(ZpRejectReason.MULTIPLE_NOT_ALLOWED)
        val fits = ZpRules.canPlayRank(rank, state.discardPile, state.rules)
        if (state.rules.enforceRules && !fits) {
            val requirement = ZpRules.requirement(state.discardPile, state.rules)
            val tooHigh = requirement.maxValue != null && rank.value > requirement.maxValue
            return reject(if (tooHigh) ZpRejectReason.CARD_TOO_HIGH else ZpRejectReason.CARD_TOO_LOW)
        }
        // Going out with a cheated card is never allowed: there would be nobody left to catch.
        val goesOut = player.totalCards == cards.size && state.drawPile.isEmpty
        if (!fits && goesOut) return reject(ZpRejectReason.LAST_CARD_MUST_FIT)

        // Laying the card that was just picked up straight back counts as a bounce; any play ends the chance.
        val bounced = player.bounceCard != null && player.bounceCard in cards
        val counted = player.copy(bounceCard = null, bounces = if (bounced) player.bounces + 1 else player.bounces)
        val afterRemoval = state.withPlayer(counted.without(source, cards))
        val resolved = resolvePlay(afterRemoval, player.id, cards, source)
        if (state.rules.enforceRules) return accept(resolved)
        val play = ZpLastPlay(
            playerId = player.id,
            cards = cards,
            legal = fits,
            requirementBefore = ZpRules.requirement(state.discardPile, state.rules),
            before = state.copy(plays = emptyList(), log = emptyList()),
            id = state.nextLogSeq,
            logSeqAfter = resolved.nextLogSeq,
        )
        // Only the newest play can still be undone completely; older ones drop their snapshot.
        return accept(resolved.copy(plays = resolved.plays.map { it.copy(before = null) } + play))
    }

    /**
     * "Vals!" on [playId] (null: the newest play by someone else). Caught right away — nothing happened since — the
     * cheat is undone completely (including burns, skips and the hand refill): the cheater gets the cards back,
     * takes the pile as it was and loses the turn. Caught later, after others have played, the cheater takes the
     * pile as it is now (topped up from the draw pile to at least the cheated cards) and loses the turn if it is
     * theirs. A false accusation costs the accuser the pile.
     */
    private fun challenge(state: ZpGameState, accuser: ZpPlayerState, playId: Long?): ActionResult<ZpGameState> {
        if (state.phase != ZpPhase.PLAYING) return reject(ZpRejectReason.WRONG_PHASE)
        if (accuser.isFinished) return reject(ZpRejectReason.NOT_PLAYING)
        val target = if (playId != null) state.plays.firstOrNull { it.id == playId } else state.plays.lastOrNull { it.playerId != accuser.id }
        if (target == null) {
            val onlyOwn = state.plays.any { it.playerId == accuser.id }
            return reject(if (onlyOwn) ZpRejectReason.CANNOT_CHALLENGE_SELF else ZpRejectReason.NOTHING_TO_CHALLENGE)
        }
        if (target.playerId == accuser.id) return reject(ZpRejectReason.CANNOT_CHALLENGE_SELF)
        val others = state.plays - target

        if (target.legal) {
            val pile = state.discardPile.cards
            var s = state.withPlayer(accuser.copy(hand = (accuser.hand + pile).sorted()))
                .copy(discardPile = state.discardPile.cleared(), plays = others)
                .log(ZpEvent.FalseAccusation(accuser.id, target.playerId, pile.size))
            // An accuser who was about to play loses the turn, just like after picking up the pile.
            if (state.turn.currentPlayerId == accuser.id) s = advanceTurn(s, fromId = accuser.id, steps = 1)
            return accept(s)
        }

        val before = target.before
        if (before == null || state.nextLogSeq != target.logSeqAfter) return accept(catchLate(state, accuser, target, others))
        val cheater = requireNotNull(before.player(target.playerId))
        // Escalating penalty (house rule): the n-th time caught also costs n-1 cards from the draw pile.
        val extraCount = if (before.rules.escalatingPenalty) cheater.cheatsCaught else 0
        val (extra, drawRest) = before.drawPile.draw(extraCount)
        val penalty = before.discardPile.cards + target.cards + extra
        val punished = cheater.copy(
            hand = (cheater.hand - target.cards.toSet() + penalty).sorted(),
            faceUp = cheater.faceUp - target.cards.toSet(),
            cheatsCaught = cheater.cheatsCaught + 1,
        )
        val restored = before.withPlayer(punished).copy(
            drawPile = drawRest,
            discardPile = before.discardPile.cleared(),
            log = state.log,
            nextLogSeq = state.nextLogSeq,
            plays = others,
        ).log(ZpEvent.CheatCaught(accuser.id, cheater.id, target.cards, penalty.size))
        return accept(advanceTurn(restored, fromId = cheater.id, steps = 1))
    }

    /** A cheat caught after the game has moved on: the cheater takes the pile as it is now. */
    private fun catchLate(state: ZpGameState, accuser: ZpPlayerState, target: ZpLastPlay, others: List<ZpLastPlay>): ZpGameState {
        val cheater = requireNotNull(state.player(target.playerId))
        val pile = state.discardPile.cards
        val extraCount = (if (state.rules.escalatingPenalty) cheater.cheatsCaught else 0) +
            (target.cards.size - pile.size).coerceAtLeast(0)
        val (extra, drawRest) = state.drawPile.draw(extraCount)
        val penalty = pile + extra
        val punished = cheater.copy(hand = (cheater.hand + penalty).sorted(), cheatsCaught = cheater.cheatsCaught + 1, bounceCard = null)
        val s = state.withPlayer(punished)
            .copy(drawPile = drawRest, discardPile = state.discardPile.cleared(), plays = others)
            .log(ZpEvent.CheatCaught(accuser.id, cheater.id, target.cards, penalty.size))
        return if (state.turn.currentPlayerId == cheater.id) advanceTurn(s, fromId = cheater.id, steps = 1) else s
    }

    /**
     * The host's clock: stamps new plays and closes the "Vals!" window of plays older than [CheatWindow.MS]
     * (an uncaught cheat counts for the ninja).
     */
    fun tick(state: ZpGameState, nowMs: Long): ZpGameState {
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
    fun nextTickAt(state: ZpGameState): Long? =
        state.plays.minOfOrNull { it.atMs?.plus(CheatWindow.MS) ?: 0L }

    private fun playBlind(state: ZpGameState, player: ZpPlayerState, index: Int): ActionResult<ZpGameState> {
        if (player.activeSource != CardSource.FACE_DOWN) return reject(ZpRejectReason.WRONG_SOURCE)
        if (index !in player.faceDown.indices) return reject(ZpRejectReason.INVALID_BLIND_INDEX)
        val card = player.faceDown[index]
        val without = player.copy(faceDown = player.faceDown.filterIndexed { i, _ -> i != index })
        val afterRemoval = state.withPlayer(without)
        return if (ZpRules.canPlayRank(card.rank, state.discardPile, state.rules)) {
            val logged = afterRemoval.log(ZpEvent.BlindRevealed(player.id, card, success = true))
            accept(resolvePlay(logged, player.id, listOf(card), CardSource.FACE_DOWN))
        } else {
            val logged = afterRemoval.log(ZpEvent.BlindRevealed(player.id, card, success = false))
            accept(takePile(logged, player.id, extra = listOf(card)))
        }
    }

    private fun pickUp(state: ZpGameState, player: ZpPlayerState): ActionResult<ZpGameState> {
        // Taking the pile is always allowed, even when a card would fit.
        if (state.discardPile.isEmpty) return reject(ZpRejectReason.PILE_EMPTY)
        return accept(takePile(state, player.id, extra = emptyList()))
    }

    private fun gamble(state: ZpGameState, player: ZpPlayerState): ActionResult<ZpGameState> {
        if (!state.rules.drawGamble) return reject(ZpRejectReason.GAMBLE_NOT_ALLOWED)
        if (state.drawPile.isEmpty) return reject(ZpRejectReason.DRAW_PILE_EMPTY)
        val (drawn, rest) = state.drawPile.draw(1)
        val card = drawn.single()
        val afterDraw = state.copy(drawPile = rest)
        return if (ZpRules.canPlayRank(card.rank, state.discardPile, state.rules)) {
            val logged = afterDraw.log(ZpEvent.GambleRevealed(player.id, card, success = true))
            accept(resolvePlay(logged, player.id, listOf(card), CardSource.HAND))
        } else {
            val logged = afterDraw.log(ZpEvent.GambleRevealed(player.id, card, success = false))
            accept(takePile(logged, player.id, extra = listOf(card)))
        }
    }

    /** Puts the discard pile (plus [extra]) into the player's hand and passes the turn. */
    private fun takePile(state: ZpGameState, playerId: String, extra: List<Card>): ZpGameState {
        val player = requireNotNull(state.player(playerId))
        val taken = state.discardPile.cards + extra
        val updated = player.copy(hand = (player.hand + taken).sorted(), bounceCard = state.discardPile.top)
        val next = state.withPlayer(updated)
            .copy(discardPile = state.discardPile.cleared())
            .log(ZpEvent.PileTaken(playerId, taken.size))
        // House rule: the player who took the pile starts the new one.
        if (state.rules.playAfterPickUp) return next.log(ZpEvent.StartsAfterPickUp(playerId))
        return advanceTurn(next, fromId = playerId, steps = 1)
    }

    /**
     * Cards have been removed from the player (or drawn) and are now put on the pile.
     * Applies burn / skip / reverse effects, refills the hand, handles finishing and passes the turn.
     */
    private fun resolvePlay(state: ZpGameState, playerId: String, cards: List<Card>, source: CardSource): ZpGameState {
        val rules = state.rules
        val effect = rules.effectOf(cards.first().rank)
        var s = state.copy(discardPile = state.discardPile.plus(cards))
            .log(ZpEvent.CardsPlayed(playerId, cards, source))

        val burnReason = when {
            effect == ZpEffect.BURN -> BurnReason.BURN_CARD
            rules.fourOfAKindBurns && s.discardPile.topRunLength() >= 4 -> BurnReason.FOUR_OF_A_KIND
            else -> null
        }
        if (burnReason != null) {
            val count = s.discardPile.size
            s = s.copy(burned = s.burned + s.discardPile.cards, discardPile = s.discardPile.cleared())
                .log(ZpEvent.PileBurned(playerId, count, burnReason))
        }

        s = refillHand(s, playerId)
        s = markFinishedIfDone(s, playerId)
        if (isGameOver(s)) return finishGame(s)

        val player = requireNotNull(s.player(playerId))
        if (burnReason != null && rules.burnGivesExtraTurn && !player.isFinished) {
            return s.copy(turn = s.turn.copy(currentPlayerId = playerId)).log(ZpEvent.ExtraTurn(playerId))
        }
        if (burnReason == null && effect == ZpEffect.REVERSE) {
            var direction = s.turn.direction
            repeat(cards.size) { direction = -direction }
            s = s.copy(turn = s.turn.copy(direction = direction)).log(ZpEvent.DirectionReversed(playerId, direction))
        }
        val skips = if (burnReason == null && effect == ZpEffect.SKIP) cards.size else 0
        return advanceTurn(s, fromId = playerId, steps = 1 + skips)
    }

    private fun refillHand(state: ZpGameState, playerId: String): ZpGameState {
        var s = state
        val player = requireNotNull(s.player(playerId))
        val missing = s.rules.handSize - player.hand.size
        if (missing <= 0) return s
        if (s.drawPile.isEmpty && s.rules.reshuffleBurned && s.burned.isNotEmpty()) {
            val newDeck = Deck(s.burned).shuffled(Random(s.seed xor s.version xor 0x0DECAL))
            s = s.copy(drawPile = newDeck, burned = emptyList()).log(ZpEvent.DrawPileReshuffled(newDeck.size))
        }
        val (drawn, rest) = s.drawPile.draw(missing)
        if (drawn.isEmpty()) return s
        return s.withPlayer(player.copy(hand = (player.hand + drawn).sorted())).copy(drawPile = rest)
    }

    private fun markFinishedIfDone(state: ZpGameState, playerId: String): ZpGameState {
        val player = requireNotNull(state.player(playerId))
        if (player.isFinished || player.totalCards > 0) return state
        val position = state.finishOrder.size + 1
        return state.withPlayer(player.copy(finishedPosition = position))
            .copy(finishOrder = state.finishOrder + playerId)
            .log(ZpEvent.PlayerFinished(playerId, position))
    }

    private fun isGameOver(state: ZpGameState): Boolean {
        val active = state.activePlayers.size
        return active <= 1 || (!state.rules.playUntilLast && state.finishOrder.isNotEmpty())
    }

    /** Assigns the remaining positions (fewest cards first, then seat order) and ends the game. */
    private fun finishGame(state: ZpGameState): ZpGameState {
        var s = state
        val remaining = s.activePlayers.sortedBy { it.totalCards }
        for (p in remaining) {
            val position = s.finishOrder.size + 1
            s = s.withPlayer(p.copy(finishedPosition = position)).copy(finishOrder = s.finishOrder + p.id)
        }
        val winner = s.finishOrder.first()
        val loser = s.finishOrder.last().takeIf { s.finishOrder.size > 1 }
        return s.copy(phase = ZpPhase.FINISHED, turn = s.turn.copy(currentPlayerId = null))
            .log(ZpEvent.GameOver(winner, loser))
    }

    /**
     * Moves the turn [steps] active players onward from [fromId] in the current direction.
     * Every active player passed over except the last one is "skipped".
     */
    private fun advanceTurn(state: ZpGameState, fromId: String, steps: Int): ZpGameState {
        val players = state.players
        val n = players.size
        var index = state.indexOf(fromId)
        val skipped = mutableListOf<String>()
        var remaining = steps
        var guard = 0
        while (remaining > 0 && guard < n * (steps + 1)) {
            index = (index + state.turn.direction).mod(n)
            guard++
            if (!players[index].isFinished) {
                remaining--
                if (remaining > 0) skipped += players[index].id
            }
        }
        var s = state.copy(turn = state.turn.copy(currentPlayerId = players[index].id))
        if (skipped.isNotEmpty()) s = s.log(ZpEvent.PlayersSkipped(fromId, skipped))
        return s
    }

    // ---------------------------------------------------------------- helpers

    private fun ZpGameState.withPlayer(updated: ZpPlayerState): ZpGameState =
        copy(players = players.map { if (it.id == updated.id) updated else it })

    private fun ZpGameState.log(event: ZpEvent): ZpGameState =
        copy(log = (log + ZpLogEntry(nextLogSeq, event)).takeLast(MAX_LOG_ENTRIES), nextLogSeq = nextLogSeq + 1, stats = count(stats, event))

    /** Plays of players who are out (or of a finished game) can no longer be called out. */
    private fun closeFinished(state: ZpGameState): ZpGameState {
        if (state.plays.isEmpty()) return state
        val (open, closed) = state.plays.partition { state.phase != ZpPhase.FINISHED && state.player(it.playerId)?.isFinished == false }
        return if (closed.isEmpty()) state else escaped(state.copy(plays = open), closed)
    }

    /** Illegal plays whose "Vals!" window closed without anybody catching them: one each for the ninja. */
    private fun escaped(state: ZpGameState, closed: List<ZpLastPlay>): ZpGameState =
        closed.filter { !it.legal }.fold(state) { s, cheat ->
            s.copy(stats = s.stats.bump(cheat.playerId) { copy(cheatsUnnoticed = cheatsUnnoticed + 1) })
        }

    /** Keeps the counters for the summary up to date; every event passes through here. */
    private fun count(stats: Map<String, PlayerStats>, event: ZpEvent): Map<String, PlayerStats> = when (event) {
        is ZpEvent.CardsPlayed -> stats.bump(event.playerId) { copy(cardsPlayed = cardsPlayed + event.cards.size) }
        is ZpEvent.BlindRevealed -> stats.bump(event.playerId) {
            if (event.success) copy(blindHits = blindHits + 1, cardsPlayed = cardsPlayed + 1) else copy(blindMisses = blindMisses + 1)
        }
        is ZpEvent.PileTaken -> stats.bump(event.playerId) { copy(pilesTaken = pilesTaken + 1, cardsFromPiles = cardsFromPiles + event.count) }
        is ZpEvent.PileBurned -> stats.bump(event.playerId) { copy(burns = burns + 1) }
        is ZpEvent.CheatCaught -> stats.bump(event.accuserId) { copy(cheatsSpotted = cheatsSpotted + 1) }
            .bump(event.cheaterId) { copy(cheatsCaught = cheatsCaught + 1) }
        is ZpEvent.FalseAccusation -> stats.bump(event.accuserId) { copy(falseCalls = falseCalls + 1) }
        else -> stats
    }

    private fun accept(state: ZpGameState): ActionResult<ZpGameState> = ActionResult.Accepted(state)

    private fun reject(reason: ZpRejectReason): ActionResult<Nothing> = ActionResult.Rejected(reason.name)
}
