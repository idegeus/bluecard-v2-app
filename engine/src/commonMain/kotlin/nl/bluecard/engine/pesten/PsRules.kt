package nl.bluecard.engine.pesten

import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Deck
import nl.bluecard.engine.model.Rank

/** Pure rule checks for Pesten, shared by the engine, the views and the bots. */
object PsRules {

    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 6

    /** Cards that must remain for the draw pile after dealing (plus the start card). */
    private const val MIN_DRAW_PILE = 8

    fun validateSetup(playerCount: Int, rules: PsHouseRules): PsRejectReason? = when {
        playerCount < MIN_PLAYERS -> PsRejectReason.TOO_FEW_PLAYERS
        playerCount > MAX_PLAYERS -> PsRejectReason.TOO_MANY_PLAYERS
        rules.handSize !in PsHouseRules.MIN_HAND_SIZE..PsHouseRules.MAX_HAND_SIZE -> PsRejectReason.INVALID_HAND_SIZE
        rules.jokers !in 0..PsHouseRules.MAX_JOKERS -> PsRejectReason.INVALID_JOKERS
        playerCount * rules.handSize + 1 + MIN_DRAW_PILE > deckSize(rules) -> PsRejectReason.NOT_ENOUGH_CARDS
        else -> null
    }

    fun deckSize(rules: PsHouseRules): Int = Deck.standard52().size + rules.jokers

    /** Most players this hand size allows. */
    fun maxPlayersFor(rules: PsHouseRules): Int =
        (MIN_PLAYERS..MAX_PLAYERS).last { validateSetup(it, rules) == null || it == MIN_PLAYERS }

    fun effectOf(card: Card, rules: PsHouseRules): PsEffect =
        if (card.rank.isJoker) PsEffect.NONE else rules.effectOf(card.rank)

    /** Cards that make the next player draw: the "2" (if it is a pak-card in these rules) and jokers. */
    fun drawAmount(card: Card, rules: PsHouseRules): Int = when {
        card.rank.isJoker -> rules.jokerDraw
        rules.effectOf(card.rank) == PsEffect.DRAW_TWO -> 2
        else -> 0
    }

    fun isDrawCard(card: Card, rules: PsHouseRules): Boolean = drawAmount(card, rules) > 0

    /** Fits on anything: jokers and the suit-choosing jack. */
    fun isWild(card: Card, rules: PsHouseRules): Boolean =
        card.rank.isJoker || rules.effectOf(card.rank) == PsEffect.CHOOSE_SUIT

    fun isSpecial(card: Card, rules: PsHouseRules): Boolean = card.rank.isJoker || rules.effectOf(card.rank) != PsEffect.NONE

    /** Whether [card] may be laid on the pile in this state (ignoring the "only the drawn card" rule). */
    fun fits(card: Card, state: PsGameState): Boolean {
        val rules = state.rules
        if (state.pendingDraw > 0) return rules.stackDraws && isDrawCard(card, rules)
        if (isWild(card, rules)) return true
        state.wishedSuit?.let { return card.suit == it }
        val top = state.discardPile.top ?: return true
        if (top.rank.isJoker) return true
        return card.suit == top.suit || card.rank == top.rank
    }

    /** Whether this play follows the rules; the engine refuses illegal plays only when rules are enforced. */
    fun whyIllegal(state: PsGameState, cards: List<Card>): PsRejectReason? {
        val first = cards.first()
        val drawn = state.drawnCard
        if (drawn != null && cards != listOf(drawn)) return PsRejectReason.ONLY_DRAWN_CARD
        if (fits(first, state)) return null
        return when {
            state.pendingDraw > 0 -> PsRejectReason.MUST_DRAW_OR_STACK
            state.wishedSuit != null -> PsRejectReason.WRONG_SUIT
            else -> PsRejectReason.DOES_NOT_FIT
        }
    }

    /** Hand cards that follow the rules right now. */
    fun fittingCards(state: PsGameState, player: PsPlayerState): List<Card> {
        state.drawnCard?.let { drawn -> return if (drawn in player.hand && fits(drawn, state)) listOf(drawn) else emptyList() }
        return player.hand.filter { fits(it, state) }
    }

    /** Hand cards the engine accepts now: the fitting ones when rules are enforced, otherwise all of them. */
    fun playableCards(state: PsGameState, player: PsPlayerState): List<Card> =
        if (state.rules.enforceRules) fittingCards(state, player) else player.hand

    /** How valuable a card is to hold: jokers, then other special cards, then the highest value. */
    fun keepValue(card: Card, rules: PsHouseRules): Int = when {
        card.rank.isJoker -> 200
        isSpecial(card, rules) -> 100 + card.rank.value
        else -> card.rank.value
    }

    /** Ranks that need a suit choice when played. */
    fun needsSuit(rank: Rank, rules: PsHouseRules): Boolean = !rank.isJoker && rules.effectOf(rank) == PsEffect.CHOOSE_SUIT
}
