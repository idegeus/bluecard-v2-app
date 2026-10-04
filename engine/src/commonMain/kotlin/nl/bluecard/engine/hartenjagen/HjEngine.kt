package nl.bluecard.engine.hartenjagen

import nl.bluecard.engine.core.PlayerStats
import nl.bluecard.engine.core.bump
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.core.RankingEntry
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Deck
import nl.bluecard.engine.model.PlayerInfo
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.model.Suit
import kotlin.random.Random

/** Pure rule checks for Hartenjagen. */
object HjRules {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 6

    val QUEEN_OF_SPADES = Card(Rank.QUEEN, Suit.SPADES)
    val JACK_OF_CLUBS = Card(Rank.JACK, Suit.CLUBS)

    /** Left out (in this order) so the cards divide evenly; never hearts, clubs or the queen of spades. */
    private val REMOVABLE = listOf("2D", "2S", "3D", "3S", "4D").map { Card.of(it) }

    fun validateSetup(playerCount: Int): HjRejectReason? = when {
        playerCount < MIN_PLAYERS -> HjRejectReason.TOO_FEW_PLAYERS
        playerCount > MAX_PLAYERS -> HjRejectReason.TOO_MANY_PLAYERS
        else -> null
    }

    fun removedCards(playerCount: Int): List<Card> = REMOVABLE.take(52 % playerCount)

    fun points(card: Card, rules: HjHouseRules): Int = when {
        card.suit == Suit.HEARTS -> 1
        card == QUEEN_OF_SPADES -> rules.queenPoints
        card == JACK_OF_CLUBS -> rules.jackPoints
        else -> 0
    }

    fun isPenalty(card: Card, rules: HjHouseRules): Boolean = points(card, rules) > 0

    /** All penalty points in one deal. */
    fun totalPoints(rules: HjHouseRules): Int = 13 + rules.queenPoints + rules.jackPoints

    fun direction(deal: Int, players: Int, rules: HjHouseRules): HjPassDirection {
        if (rules.passCount == 0) return HjPassDirection.NONE
        val cycle = if (players == 4) {
            listOf(HjPassDirection.LEFT, HjPassDirection.RIGHT, HjPassDirection.ACROSS, HjPassDirection.NONE)
        } else {
            listOf(HjPassDirection.LEFT, HjPassDirection.RIGHT, HjPassDirection.NONE)
        }
        return cycle[deal % cycle.size]
    }

    fun passTarget(state: HjGameState, fromId: String): String? {
        val n = state.players.size
        val i = state.indexOf(fromId)
        val step = when (state.passDirection) {
            HjPassDirection.LEFT -> 1
            HjPassDirection.RIGHT -> n - 1
            HjPassDirection.ACROSS -> 2
            HjPassDirection.NONE -> return null
        }
        return state.players[(i + step) % n].id
    }

    /** The lowest club still in the game (the 2 unless left out). */
    fun lowestClub(state: HjGameState): Card? =
        state.players.flatMap { it.hand }.filter { it.suit == Suit.CLUBS }.minByOrNull { it.rank.value }

    /** Why [card] may not be played now by [player], or null when it may. */
    fun whyNot(state: HjGameState, player: HjPlayerState, card: Card): HjRejectReason? {
        val rules = state.rules
        val hand = player.hand
        if (card !in hand) return HjRejectReason.CARD_NOT_AVAILABLE
        val firstTrick = state.trickNumber == 0
        val lead = state.leadSuit
        if (lead == null) {
            if (firstTrick && rules.clubsLead) {
                val club = hand.filter { it.suit == Suit.CLUBS }.minByOrNull { it.rank.value }
                if (club != null && club == lowestClub(state) && card != club) return HjRejectReason.MUST_LEAD_CLUB
            }
            if (card.suit == Suit.HEARTS && rules.heartsBroken && !state.heartsBroken && hand.any { it.suit != Suit.HEARTS }) {
                return HjRejectReason.HEARTS_NOT_BROKEN
            }
            return null
        }
        if (card.suit != lead && hand.any { it.suit == lead }) return HjRejectReason.MUST_FOLLOW_SUIT
        if (card.suit != lead && firstTrick && rules.noPointsFirstTrick && isPenalty(card, rules) &&
            hand.any { !isPenalty(it, rules) }
        ) {
            return HjRejectReason.NO_POINTS_FIRST_TRICK
        }
        return null
    }

    fun playable(state: HjGameState, player: HjPlayerState): List<Card> = player.hand.filter { whyNot(state, player, it) == null }

    /** Order for a hand on screen: by suit, low to high. */
    val handOrder: Comparator<Card> = compareBy<Card>({ SUIT_ORDER.indexOf(it.suit) }, { it.rank.value })
    private val SUIT_ORDER = listOf(Suit.CLUBS, Suit.DIAMONDS, Suit.SPADES, Suit.HEARTS)
}

/**
 * Hartenjagen: tricks of one card each, follow suit if you can, the highest card of the led suit wins the trick.
 * Avoid hearts (1 point each), the queen of spades and, in the Dutch game, the jack of clubs.
 */
object HjEngine {
    private const val MAX_LOG_ENTRIES = 150

    fun newGame(players: List<PlayerInfo>, rules: HjHouseRules, seed: Long): HjGameState {
        HjRules.validateSetup(players.size)?.let { throw IllegalArgumentException("Invalid setup: $it") }
        require(players.map { it.id }.toSet().size == players.size) { "Player ids must be unique" }
        val state = HjGameState(rules = rules, players = players.map { HjPlayerState(it.id, it.name) }, seed = seed)
        return deal(state, 0)
    }

    private fun deal(state: HjGameState, number: Int): HjGameState {
        val n = state.players.size
        val removed = HjRules.removedCards(n)
        val deck = (Deck.standard52().cards - removed.toSet()).shuffled(Random(state.seed + number * 7_919L))
        val per = deck.size / n
        val direction = HjRules.direction(number, n, state.rules)
        var s = state.copy(
            players = state.players.mapIndexed { i, p ->
                p.copy(hand = deck.subList(i * per, (i + 1) * per).sortedWith(HjRules.handOrder), taken = emptyList(), tricks = 0, passing = null)
            },
            deal = number,
            passDirection = direction,
            phase = if (direction == HjPassDirection.NONE) HjPhase.PLAYING else HjPhase.PASSING,
            currentPlayerId = null,
            trick = emptyList(),
            lastTrick = emptyList(),
            lastTrickWinnerId = null,
            trickNumber = 0,
            heartsBroken = false,
            removed = removed,
            discards = emptyList(),
        ).log(HjEvent.DealStarted(number, direction))
        if (s.phase == HjPhase.PLAYING) s = startPlay(s)
        return s
    }

    private fun startPlay(state: HjGameState): HjGameState {
        val leader = if (state.rules.clubsLead) {
            val club = HjRules.lowestClub(state)
            state.players.first { club in it.hand }.id
        } else {
            state.players[state.deal % state.players.size].id
        }
        return state.copy(phase = HjPhase.PLAYING, currentPlayerId = leader).log(HjEvent.Leads(leader))
    }

    fun apply(state: HjGameState, playerId: String, action: HjAction): ActionResult<HjGameState> {
        val player = state.player(playerId) ?: return reject(HjRejectReason.UNKNOWN_PLAYER)
        if (state.phase == HjPhase.FINISHED) return reject(HjRejectReason.GAME_FINISHED)
        val result = when (action) {
            is HjAction.PassCards -> pass(state, player, action.cards)
            is HjAction.Play -> {
                if (state.phase != HjPhase.PLAYING) return reject(HjRejectReason.WRONG_PHASE)
                if (state.currentPlayerId != playerId) return reject(HjRejectReason.NOT_YOUR_TURN)
                play(state, player, action.card)
            }
        }
        return when (result) {
            is ActionResult.Accepted -> ActionResult.Accepted(result.state.copy(version = state.version + 1))
            is ActionResult.Rejected -> result
        }
    }

    private fun pass(state: HjGameState, player: HjPlayerState, cards: List<Card>): ActionResult<HjGameState> {
        if (state.phase != HjPhase.PASSING) return reject(HjRejectReason.WRONG_PHASE)
        if (player.passing != null) return reject(HjRejectReason.ALREADY_PASSED)
        if (cards.size != state.rules.passCount) return reject(HjRejectReason.WRONG_PASS_COUNT)
        if (cards.toSet().size != cards.size) return reject(HjRejectReason.DUPLICATE_CARDS)
        if (!player.hand.containsAll(cards)) return reject(HjRejectReason.CARD_NOT_AVAILABLE)
        var s = state.withPlayer(player.copy(hand = player.hand - cards.toSet(), passing = cards)).log(HjEvent.CardsPassed(player.id))
        if (s.players.all { it.passing != null }) {
            // Everybody has chosen: hand the cards over at once.
            val incoming = s.players.associate { p -> HjRules.passTarget(s, p.id)!! to p.passing!! }
            s = s.copy(players = s.players.map { p -> p.copy(hand = (p.hand + incoming.getValue(p.id)).sortedWith(HjRules.handOrder), passing = null) })
            s = startPlay(s)
        }
        return accept(s)
    }

    private fun play(state: HjGameState, player: HjPlayerState, card: Card): ActionResult<HjGameState> {
        HjRules.whyNot(state, player, card)?.let { return reject(it) }
        var s = state.withPlayer(player.copy(hand = player.hand - card))
            .copy(trick = state.trick + HjPlay(player.id, card))
            .log(HjEvent.Played(player.id, card))
        if (card.suit == Suit.HEARTS && !s.heartsBroken) s = s.copy(heartsBroken = true).log(HjEvent.HeartsBroken(player.id))
        if (s.trick.size < s.players.size) {
            val n = s.players.size
            return accept(s.copy(currentPlayerId = s.players[(s.indexOf(player.id) + 1) % n].id))
        }
        return accept(finishTrick(s))
    }

    private fun finishTrick(state: HjGameState): HjGameState {
        val lead = requireNotNull(state.leadSuit)
        val winning = state.trick.filter { it.card.suit == lead }.maxBy { it.card.rank.value }
        val penalty = state.trick.map { it.card }.filter { HjRules.isPenalty(it, state.rules) }
        val points = penalty.sumOf { HjRules.points(it, state.rules) }
        val winner = requireNotNull(state.player(winning.playerId))
        var s = state.withPlayer(winner.copy(taken = winner.taken + penalty, tricks = winner.tricks + 1))
            .copy(
                discards = state.discards + state.trick.map { it.card }.filter { it !in penalty },
                lastTrick = state.trick,
                lastTrickWinnerId = winner.id,
                trick = emptyList(),
                trickNumber = state.trickNumber + 1,
                currentPlayerId = winner.id,
            )
            .log(HjEvent.TrickWon(winner.id, points))
        if (s.players.all { it.hand.isEmpty() }) s = scoreDeal(s)
        return s
    }

    private fun scoreDeal(state: HjGameState): HjGameState {
        val rules = state.rules
        val total = HjRules.totalPoints(rules)
        var points = state.players.associate { p -> p.id to p.taken.sumOf { HjRules.points(it, rules) } }
        var s = state
        val moon = points.entries.firstOrNull { it.value == total && total > 0 }?.key
        if (rules.shootTheMoon && moon != null) {
            points = state.players.associate { it.id to if (it.id == moon) 0 else total }
            s = s.log(HjEvent.MoonShot(moon, total))
        }
        s = s.copy(
            players = s.players.map { it.copy(score = it.score + points.getValue(it.id)) },
            lastDealPoints = points,
        )
        s = s.log(HjEvent.DealScored(s.deal, points, s.players.associate { it.id to it.score }))
        val over = rules.targetScore <= 0 || s.players.any { it.score >= rules.targetScore }
        return if (over) finish(s) else deal(s.copy(discards = emptyList()), s.deal + 1)
    }

    private fun finish(state: HjGameState): HjGameState {
        val ranking = ranked(state)
        return state.copy(phase = HjPhase.FINISHED, currentPlayerId = null)
            .log(HjEvent.GameOver(ranking.first().id, ranking.last().id))
    }

    /** Lowest score first; ties: fewer points in the last deal, then seat order. */
    private fun ranked(state: HjGameState): List<HjPlayerState> =
        state.players.sortedWith(compareBy<HjPlayerState>({ it.score }, { state.lastDealPoints[it.id] ?: 0 }, { state.indexOf(it.id) }))

    /** Giving up ends the game with that player last. */
    fun concede(state: HjGameState, playerId: String): HjGameState {
        if (state.phase == HjPhase.FINISHED || state.player(playerId) == null) return state
        val s = state.log(HjEvent.Resigned(playerId)).copy(resignedId = playerId)
        val ranking = ranked(s).filter { it.id != playerId } + s.player(playerId)!!
        return s.copy(phase = HjPhase.FINISHED, currentPlayerId = null, version = state.version + 1)
            .log(HjEvent.GameOver(ranking.first().id, playerId))
    }

    fun result(state: HjGameState): GameResult? {
        if (state.phase != HjPhase.FINISHED) return null
        val ranking = ranked(state).let { r -> state.resignedId?.let { id -> r.filter { it.id != id } + r.first { it.id == id } } ?: r }
        return GameResult(ranking.mapIndexed { i, p -> RankingEntry(p.id, p.name, i + 1, p.hand.size, score = p.score) }, stats = state.stats)
    }

    private fun HjGameState.withPlayer(updated: HjPlayerState): HjGameState =
        copy(players = players.map { if (it.id == updated.id) updated else it })

    private fun HjGameState.log(event: HjEvent): HjGameState =
        copy(log = (log + HjLogEntry(nextLogSeq, event)).takeLast(MAX_LOG_ENTRIES), nextLogSeq = nextLogSeq + 1, stats = count(stats, event))

    /** Keeps the counters for the summary up to date; every event passes through here. */
    private fun count(stats: Map<String, PlayerStats>, event: HjEvent): Map<String, PlayerStats> = when (event) {
        is HjEvent.Played -> stats.bump(event.playerId) { copy(cardsPlayed = cardsPlayed + 1) }
        is HjEvent.TrickWon -> stats.bump(event.playerId) { copy(tricksWon = tricksWon + 1, pointsTaken = pointsTaken + event.points) }
        is HjEvent.MoonShot -> stats.bump(event.playerId) { copy(moonShots = moonShots + 1) }
        else -> stats
    }

    private fun accept(state: HjGameState): ActionResult<HjGameState> = ActionResult.Accepted(state)

    private fun reject(reason: HjRejectReason): ActionResult<Nothing> = ActionResult.Rejected(reason.name)
}
