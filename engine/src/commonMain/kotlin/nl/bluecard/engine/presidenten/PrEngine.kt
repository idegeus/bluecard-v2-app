package nl.bluecard.engine.presidenten

import nl.bluecard.engine.core.PlayerStats
import nl.bluecard.engine.core.bump
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.core.RankingEntry
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.PlayerInfo
import kotlin.random.Random

/**
 * Presidenten: all cards are dealt; the leader lays a set (one or more cards of the same value), the others must lay
 * as many cards of a higher value or pass. When nobody can or wants to go higher, whoever laid last wins the trick
 * and starts the next one. The first player out becomes president, the last one the sloeber. In the next round the
 * sloeber hands their best cards to the president, who gives cards of their own choice back.
 */
object PrEngine {

    private const val MAX_LOG_ENTRIES = 150

    fun newGame(players: List<PlayerInfo>, rules: PrHouseRules, seed: Long, previous: GameResult? = null): PrGameState {
        PrRules.validateSetup(players.size, rules)?.let { throw IllegalArgumentException("Invalid setup: $it") }
        require(players.map { it.id }.toSet().size == players.size) { "Player ids must be unique" }

        val order = PrRules.handOrder(rules)
        val deck = PrRules.deck(rules).shuffled(Random(seed)).cards
        val hands = players.indices.map { seat -> deck.filterIndexed { i, _ -> i % players.size == seat } }
        val titles = titles(previous, players)
        var state = PrGameState(
            rules = rules,
            players = players.mapIndexed { i, p -> PrPlayerState(p.id, p.name, hands[i].sortedWith(order), title = titles[p.id]) },
            seed = seed,
        )

        if (rules.exchange && titles.isNotEmpty() && previous != null) {
            val ranking = previous.ranking.sortedBy { it.position }.map { it.playerId }
            val pairs = buildList {
                add(Triple(ranking.first(), ranking.last(), if (players.size >= 4) 2 else 1))
                if (players.size >= 4) add(Triple(ranking[1], ranking[ranking.size - 2], 1))
            }
            val exchanges = mutableListOf<PrExchange>()
            for ((high, low, count) in pairs) {
                val lowPlayer = requireNotNull(state.player(low))
                val best = lowPlayer.hand.sortedWith(order).takeLast(count)
                val highPlayer = requireNotNull(state.player(high))
                state = state
                    .withPlayer(lowPlayer.copy(hand = lowPlayer.hand - best.toSet()))
                    .withPlayer(highPlayer.copy(hand = (highPlayer.hand + best).sortedWith(order)))
                    .log(PrEvent.TributeGiven(low, high, count))
                exchanges += PrExchange(high, low, count, best)
            }
            return state.copy(phase = PrPhase.EXCHANGING, exchanges = exchanges)
        }
        return start(state, previous)
    }

    /** Titles from the last round, only when exactly the same players sit at the table. */
    private fun titles(previous: GameResult?, players: List<PlayerInfo>): Map<String, PrTitle> {
        val ranking = previous?.ranking?.sortedBy { it.position }?.map { it.playerId } ?: return emptyMap()
        if (ranking.toSet() != players.map { it.id }.toSet()) return emptyMap()
        val n = ranking.size
        return ranking.mapIndexed { i, id ->
            id to when {
                i == 0 -> PrTitle.PRESIDENT
                i == n - 1 -> PrTitle.SCUM
                n >= 4 && i == 1 -> PrTitle.VICE_PRESIDENT
                n >= 4 && i == n - 2 -> PrTitle.VICE_SCUM
                else -> PrTitle.CITIZEN
            }
        }.toMap()
    }

    /** The sloeber of the last round starts; in a first round whoever holds the lowest card. */
    private fun start(state: PrGameState, previous: GameResult?): PrGameState {
        val scum = state.players.firstOrNull { it.title == PrTitle.SCUM }?.id
        val starter = scum ?: previous?.loser?.playerId?.takeIf { state.player(it) != null }
            ?: state.players.minWith(compareBy { p -> p.hand.minOfOrNull { PrRules.strength(it, state.rules) * 10 + it.suit.ordinal } ?: Int.MAX_VALUE }).id
        return state.copy(phase = PrPhase.PLAYING, currentPlayerId = starter).log(PrEvent.GameStarted(starter))
    }

    fun apply(state: PrGameState, playerId: String, action: PrAction): ActionResult<PrGameState> {
        val player = state.player(playerId) ?: return reject(PrRejectReason.UNKNOWN_PLAYER)
        if (state.phase == PrPhase.FINISHED) return reject(PrRejectReason.GAME_FINISHED)
        val result = when (action) {
            is PrAction.GiveCards -> give(state, player, action.cards)
            is PrAction.Play -> {
                if (state.phase != PrPhase.PLAYING) return reject(PrRejectReason.WRONG_PHASE)
                if (state.currentPlayerId != playerId) return reject(PrRejectReason.NOT_YOUR_TURN)
                play(state, player, action.cards)
            }
            PrAction.Pass -> {
                if (state.phase != PrPhase.PLAYING) return reject(PrRejectReason.WRONG_PHASE)
                if (state.currentPlayerId != playerId) return reject(PrRejectReason.NOT_YOUR_TURN)
                pass(state, player)
            }
        }
        return when (result) {
            is ActionResult.Accepted -> ActionResult.Accepted(result.state.copy(version = state.version + 1))
            is ActionResult.Rejected -> result
        }
    }

    private fun give(state: PrGameState, player: PrPlayerState, cards: List<Card>): ActionResult<PrGameState> {
        if (state.phase != PrPhase.EXCHANGING) return reject(PrRejectReason.WRONG_PHASE)
        val exchange = state.exchanges.firstOrNull { it.highId == player.id && !it.done } ?: return reject(PrRejectReason.NOTHING_TO_GIVE)
        if (cards.size != exchange.count) return reject(PrRejectReason.WRONG_GIVE_COUNT)
        if (cards.toSet().size != cards.size) return reject(PrRejectReason.DUPLICATE_CARDS)
        if (!player.hand.containsAll(cards)) return reject(PrRejectReason.CARD_NOT_AVAILABLE)
        val order = PrRules.handOrder(state.rules)
        val low = requireNotNull(state.player(exchange.lowId))
        var s = state
            .withPlayer(player.copy(hand = player.hand - cards.toSet()))
            .withPlayer(low.copy(hand = (low.hand + cards).sortedWith(order)))
            .copy(exchanges = state.exchanges.map { if (it === exchange) it.copy(done = true) else it })
            .log(PrEvent.CardsReturned(player.id, low.id, cards.size))
        if (s.exchanges.all { it.done }) s = start(s, null)
        return accept(s)
    }

    private fun play(state: PrGameState, player: PrPlayerState, cards: List<Card>): ActionResult<PrGameState> {
        val rules = state.rules
        if (cards.isEmpty()) return reject(PrRejectReason.EMPTY_SELECTION)
        if (cards.toSet().size != cards.size) return reject(PrRejectReason.DUPLICATE_CARDS)
        if (!player.hand.containsAll(cards)) return reject(PrRejectReason.CARD_NOT_AVAILABLE)
        PrRules.whyNot(cards, state.top, rules)?.let { return reject(it) }

        val previousTop = state.top
        var s = state
            .withPlayer(player.copy(hand = player.hand - cards.toSet()))
            .copy(
                plays = state.plays + PrPlay(player.id, cards),
                lastPlayerId = player.id,
                passed = if (rules.passIsFinal) state.passed else emptySet(),
            )
            .log(PrEvent.CardsPlayed(player.id, cards))

        if (s.player(player.id)!!.hand.isEmpty()) {
            s = markFinished(s, player.id)
            if (s.activePlayers.size <= 1) return accept(finishGame(s))
        }

        val strength = PrRules.strength(PrRules.setRank(cards)!!, rules)
        // Nothing can go higher: the trick is won straight away.
        if (strength >= PrRules.maxStrength(rules) && !rules.equalSkips) return accept(winTrick(s))

        var next = nextEligible(s, player.id)
        // Equal value: the next player is skipped.
        if (next != null && previousTop != null && rules.equalSkips &&
            PrRules.strength(PrRules.setRank(previousTop.cards)!!, rules) == strength
        ) {
            s = s.log(PrEvent.PlayerSkipped(player.id, next))
            next = nextEligible(s, next)
        }
        return accept(if (next == null || next == player.id) winTrick(s) else s.copy(currentPlayerId = next))
    }

    private fun pass(state: PrGameState, player: PrPlayerState): ActionResult<PrGameState> {
        if (state.plays.isEmpty()) return reject(PrRejectReason.CANNOT_PASS_LEAD)
        val s = state.copy(passed = state.passed + player.id).log(PrEvent.Passed(player.id))
        val next = nextEligible(s, player.id)
        return accept(if (next == null || next == s.lastPlayerId) winTrick(s) else s.copy(currentPlayerId = next))
    }

    /** The next player after [fromId] who is still in the round and has not passed (may be the last player). */
    private fun nextEligible(state: PrGameState, fromId: String): String? {
        val n = state.players.size
        val start = state.indexOf(fromId)
        for (step in 1..n) {
            val candidate = state.players[(start + step).mod(n)]
            if (candidate.isFinished || candidate.id in state.passed) continue
            if (candidate.id == fromId) return null
            return candidate.id
        }
        return null
    }

    /** The last player wins the trick and starts the next one (or the next player in line if they are out). */
    private fun winTrick(state: PrGameState): PrGameState {
        val winner = requireNotNull(state.lastPlayerId)
        val cleared = state.copy(
            outOfPlay = state.outOfPlay + state.plays.flatMap { it.cards },
            lastTrick = state.plays,
            lastTrickWinnerId = winner,
            plays = emptyList(),
            passed = emptySet(),
            lastPlayerId = null,
        ).log(PrEvent.TrickWon(winner))
        val leader = if (cleared.player(winner)?.isFinished == false) {
            winner
        } else {
            val n = cleared.players.size
            val start = cleared.indexOf(winner)
            (1..n).map { cleared.players[(start + it).mod(n)] }.first { !it.isFinished }.id
        }
        return cleared.copy(currentPlayerId = leader)
    }

    private fun markFinished(state: PrGameState, playerId: String): PrGameState {
        val position = state.finishOrder.size + 1
        val player = requireNotNull(state.player(playerId))
        return state.withPlayer(player.copy(finishedPosition = position))
            .copy(finishOrder = state.finishOrder + playerId)
            .log(PrEvent.PlayerFinished(playerId, position))
    }

    private fun finishGame(state: PrGameState): PrGameState {
        var s = state
        for (p in s.activePlayers) s = markFinished(s, p.id)
        return s.copy(
            phase = PrPhase.FINISHED,
            currentPlayerId = null,
            outOfPlay = s.outOfPlay + s.plays.flatMap { it.cards },
            plays = emptyList(),
            passed = emptySet(),
        ).log(PrEvent.GameOver(s.finishOrder.first(), s.finishOrder.last()))
    }

    /** Giving up ends the game: [playerId] last, the others ranked by the cards they still hold. */
    fun concede(state: PrGameState, playerId: String): PrGameState {
        if (state.phase == PrPhase.FINISHED || state.player(playerId)?.isFinished != false) return state
        var s = state.log(PrEvent.Resigned(playerId))
        for (p in s.activePlayers.filter { it.id != playerId }.sortedBy { it.hand.size }) s = markFinished(s, p.id)
        s = markFinished(s, playerId)
        return s.copy(
            phase = PrPhase.FINISHED,
            currentPlayerId = null,
            outOfPlay = s.outOfPlay + s.plays.flatMap { it.cards },
            plays = emptyList(),
            exchanges = emptyList(),
            version = state.version + 1,
        ).log(PrEvent.GameOver(s.finishOrder.first(), playerId))
    }

    fun result(state: PrGameState): GameResult? {
        if (state.phase != PrPhase.FINISHED) return null
        val ranked = state.players.filter { it.finishedPosition != null }.sortedBy { it.finishedPosition }
        return GameResult(ranked.map { RankingEntry(it.id, it.name, it.finishedPosition ?: 0, it.hand.size) }, stats = state.stats)
    }

    private fun PrGameState.withPlayer(updated: PrPlayerState): PrGameState =
        copy(players = players.map { if (it.id == updated.id) updated else it })

    private fun PrGameState.log(event: PrEvent): PrGameState =
        copy(log = (log + PrLogEntry(nextLogSeq, event)).takeLast(MAX_LOG_ENTRIES), nextLogSeq = nextLogSeq + 1, stats = count(stats, event))

    /** Keeps the counters for the summary up to date; every event passes through here. */
    private fun count(stats: Map<String, PlayerStats>, event: PrEvent): Map<String, PlayerStats> = when (event) {
        is PrEvent.CardsPlayed -> stats.bump(event.playerId) { copy(cardsPlayed = cardsPlayed + event.cards.size) }
        is PrEvent.Passed -> stats.bump(event.playerId) { copy(passes = passes + 1) }
        is PrEvent.TrickWon -> stats.bump(event.playerId) { copy(tricksWon = tricksWon + 1) }
        else -> stats
    }

    private fun accept(state: PrGameState): ActionResult<PrGameState> = ActionResult.Accepted(state)

    private fun reject(reason: PrRejectReason): ActionResult<Nothing> = ActionResult.Rejected(reason.name)
}
