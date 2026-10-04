package nl.bluecard.engine.pesten

import kotlinx.serialization.Serializable
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit

/** Public information about a player at the table. Hand cards are only counted. */
@Serializable
data class PsPublicPlayer(
    val id: String,
    val name: String,
    val handCount: Int,
    val announced: Boolean,
    val finishedPosition: Int? = null,
)

/**
 * Legal moves for the viewing player, computed by the engine. The UI only enables what is listed here;
 * the host validates every submitted action again regardless.
 */
@Serializable
data class PsLegalMoves(
    /** Cards the engine will accept now. Without rule enforcement this is the whole hand. */
    val playableCards: List<Card> = emptyList(),
    /** Cards that follow the rules (used for hints and by bots, which never cheat). */
    val fittingCards: List<Card> = emptyList(),
    val canPlayMultiple: Boolean = false,
    val canDraw: Boolean = false,
    /** How many cards a draw takes now (1, or the pending "pak"-cards). */
    val drawCount: Int = 0,
    val canPass: Boolean = false,
    val canCallLastCard: Boolean = false,
    /** "Vergeten!": someone else went down to one card without calling "Laatste kaart!". */
    val canCatch: Boolean = false,
    /** "Vals!" may be called on a recent play (rules not enforced, someone else laid the cards). */
    val canChallenge: Boolean = false,
    /** House rule "winnaar ruilt": the cards the winner may give to the loser. */
    val giveCards: List<Card> = emptyList(),
) {
    companion object {
        val NONE = PsLegalMoves()
    }
}

/**
 * Public facts about the last play and what lay on the table before it, so players (and bots) can judge
 * whether it was allowed. Whether it really was legal is not included.
 */
@Serializable
data class PsChallengeView(
    val playerId: String,
    val cards: List<Card>,
    val topBefore: Card?,
    val wishedSuitBefore: Suit?,
    val pendingDrawBefore: Int,
    /** The play to name in [PsAction.Challenge]. */
    val id: Long = 0,
)

/** Everything one player is allowed to know. This is what travels over Bluetooth to a client. */
@Serializable
data class PsPlayerView(
    val viewerId: String,
    val stateVersion: Long,
    val phase: PsPhase,
    val rules: PsHouseRules,
    /** All players in seating order, including the viewer. */
    val players: List<PsPublicPlayer>,
    val myHand: List<Card>,
    val currentPlayerId: String?,
    val direction: Int,
    val drawPileCount: Int,
    /** Top cards of the discard pile, bottom to top (at most [DISCARD_PREVIEW]). */
    val discardTop: List<Card>,
    val discardCount: Int,
    val wishedSuit: Suit?,
    val pendingDraw: Int,
    /** The card the viewer drew this turn (only visible to the viewer). */
    val drawnCard: Card?,
    val forgottenId: String?,
    val legal: PsLegalMoves,
    val log: List<PsLogEntry>,
    val result: GameResult? = null,
    val challenge: PsChallengeView? = null,
    /** All plays that can still be called out (the last [nl.bluecard.engine.core.CheatWindow.MS]), oldest first. */
    val challenges: List<PsChallengeView> = emptyList(),
    /** Winner and loser of the last round while their card exchange is pending. */
    val exchange: nl.bluecard.engine.core.WinnerExchange? = null,
) {
    val me: PsPublicPlayer? get() = players.firstOrNull { it.id == viewerId }
    val isMyTurn: Boolean get() = phase == PsPhase.PLAYING && currentPlayerId == viewerId
    fun player(id: String): PsPublicPlayer? = players.firstOrNull { it.id == id }

    companion object {
        const val DISCARD_PREVIEW = 6
        const val LOG_PREVIEW = 60
    }
}

object PsViews {

    fun create(state: PsGameState, viewerId: String): PsPlayerView {
        val viewer = state.player(viewerId)
        val myTurn = state.phase == PsPhase.PLAYING && state.currentPlayerId == viewerId
        return PsPlayerView(
            viewerId = viewerId,
            stateVersion = state.version,
            phase = state.phase,
            rules = state.rules,
            players = state.players.map { PsPublicPlayer(it.id, it.name, it.hand.size, it.announced, it.finishedPosition) },
            myHand = viewer?.hand?.sorted() ?: emptyList(),
            currentPlayerId = state.currentPlayerId,
            direction = state.direction,
            drawPileCount = state.drawPile.size,
            discardTop = state.discardPile.lastCards(PsPlayerView.DISCARD_PREVIEW),
            discardCount = state.discardPile.size,
            wishedSuit = state.wishedSuit,
            pendingDraw = state.pendingDraw,
            drawnCard = state.drawnCard.takeIf { myTurn },
            forgottenId = state.forgottenId,
            legal = viewer?.let { legalMoves(state, it) } ?: PsLegalMoves.NONE,
            log = state.log.takeLast(PsPlayerView.LOG_PREVIEW),
            result = PsEngine.result(state),
            exchange = state.exchange,
            challenge = state.lastPlay?.let(::challengeView),
            challenges = state.plays.map(::challengeView),
        )
    }

    private fun challengeView(play: PsLastPlay) =
        PsChallengeView(play.playerId, play.cards, play.topBefore, play.wishedSuitBefore, play.pendingDrawBefore, play.id)

    fun legalMoves(state: PsGameState, player: PsPlayerState): PsLegalMoves {
        if (state.phase != PsPhase.PLAYING) return PsLegalMoves.NONE
        state.exchange?.let { exchange ->
            return if (exchange.winnerId == player.id) PsLegalMoves(giveCards = player.hand.sorted()) else PsLegalMoves.NONE
        }
        val rules = state.rules
        val calls = PsLegalMoves(
            canCallLastCard = rules.lastCardCall && !player.isFinished && !player.announced && player.hand.size in 1..2,
            canCatch = !player.isFinished && state.forgottenId != null && state.forgottenId != player.id,
            canChallenge = !player.isFinished && state.plays.any { it.playerId != player.id },
        )
        if (state.currentPlayerId != player.id) return calls
        val drawn = state.drawnCard
        return calls.copy(
            playableCards = PsRules.playableCards(state, player).sorted(),
            fittingCards = PsRules.fittingCards(state, player).sorted(),
            canPlayMultiple = rules.allowMultiple,
            canDraw = drawn == null,
            drawCount = if (state.pendingDraw > 0) state.pendingDraw else 1,
            canPass = drawn != null,
        )
    }

    /** Only the current player has to act; calls out of turn are optional. */
    fun pendingActors(state: PsGameState): Set<String> = when {
        state.phase != PsPhase.PLAYING -> emptySet()
        state.exchange != null -> setOf(state.exchange.winnerId)
        else -> setOfNotNull(state.currentPlayerId)
    }
}
