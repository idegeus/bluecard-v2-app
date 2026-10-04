package nl.bluecard.engine.zweedspesten

import kotlinx.serialization.Serializable
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.model.Card

/** Public information about a player at the table. Hand cards and face-down cards are only counted. */
@Serializable
data class ZpPublicPlayer(
    val id: String,
    val name: String,
    val handCount: Int,
    val faceUp: List<Card>,
    val faceDownCount: Int,
    val ready: Boolean,
    val finishedPosition: Int? = null,
) {
    val totalCards: Int get() = handCount + faceUp.size + faceDownCount
}

/**
 * Legal moves for the viewing player, computed by the engine. The UI only enables what is listed here;
 * the host validates every submitted action again regardless.
 */
@Serializable
data class ZpLegalMoves(
    val source: CardSource? = null,
    /** Cards the engine will accept now. Without rule enforcement this is every card of the current source. */
    val playableCards: List<Card> = emptyList(),
    /** Cards that follow the rules (used for hints and by bots, which never cheat). */
    val fittingCards: List<Card> = emptyList(),
    val canPlayMultiple: Boolean = false,
    val blindIndices: List<Int> = emptyList(),
    val canPickUp: Boolean = false,
    val canGamble: Boolean = false,
    val canSwap: Boolean = false,
    val canReady: Boolean = false,
    /** "Vals!" may be called on a recent play (rules not enforced, someone else laid the cards). */
    val canChallenge: Boolean = false,
    /** House rule "winnaar ruilt": the cards the winner may give to the loser. */
    val giveCards: List<Card> = emptyList(),
) {
    val hasAnyMove: Boolean
        get() = playableCards.isNotEmpty() || blindIndices.isNotEmpty() || canPickUp || canGamble || canSwap || canReady ||
            canChallenge || giveCards.isNotEmpty()

    companion object {
        val NONE = ZpLegalMoves()
    }
}

/** Everything one player is allowed to know. This is what travels over Bluetooth to a client. */
@Serializable
data class ZpPlayerView(
    val viewerId: String,
    val stateVersion: Long,
    val phase: ZpPhase,
    val rules: ZpHouseRules,
    /** All players in seating order, including the viewer. */
    val players: List<ZpPublicPlayer>,
    val myHand: List<Card>,
    val currentPlayerId: String?,
    val direction: Int,
    val drawPileCount: Int,
    /** Top cards of the discard pile, bottom to top (at most [DISCARD_PREVIEW]). */
    val discardTop: List<Card>,
    val discardCount: Int,
    val burnedCount: Int,
    val requirement: ZpRequirement,
    val legal: ZpLegalMoves,
    val log: List<ZpLogEntry>,
    val result: GameResult? = null,
    /** The newest play that can still be called out as cheating, if any. */
    val challenge: ZpChallengeView? = null,
    /** All plays that can still be called out (the last [nl.bluecard.engine.core.CheatWindow.MS]), oldest first. */
    val challenges: List<ZpChallengeView> = emptyList(),
    /** Winner and loser of the last round while their card exchange is pending. */
    val exchange: nl.bluecard.engine.core.WinnerExchange? = null,
) {
    val me: ZpPublicPlayer? get() = players.firstOrNull { it.id == viewerId }
    val isMyTurn: Boolean get() = phase == ZpPhase.PLAYING && currentPlayerId == viewerId
    fun player(id: String): ZpPublicPlayer? = players.firstOrNull { it.id == id }

    companion object {
        const val DISCARD_PREVIEW = 6
        const val LOG_PREVIEW = 60
    }
}

/**
 * Public facts about the last play: who, which cards, and what was required at that moment (everyone saw the
 * pile). Whether it was legal is not included — players have to judge that themselves.
 */
@Serializable
data class ZpChallengeView(
    val playerId: String,
    val cards: List<Card>,
    val requirementBefore: ZpRequirement,
    /** The play to name in [ZpAction.Challenge]. */
    val id: Long = 0,
)

object ZpViews {

    fun create(state: ZpGameState, viewerId: String): ZpPlayerView {
        val viewer = state.player(viewerId)
        return ZpPlayerView(
            viewerId = viewerId,
            stateVersion = state.version,
            phase = state.phase,
            rules = state.rules,
            players = state.players.map { p ->
                ZpPublicPlayer(
                    id = p.id,
                    name = p.name,
                    handCount = p.hand.size,
                    faceUp = p.faceUp,
                    faceDownCount = p.faceDown.size,
                    ready = p.ready,
                    finishedPosition = p.finishedPosition,
                )
            },
            myHand = viewer?.hand?.sorted() ?: emptyList(),
            currentPlayerId = state.turn.currentPlayerId,
            direction = state.turn.direction,
            drawPileCount = state.drawPile.size,
            discardTop = state.discardPile.lastCards(ZpPlayerView.DISCARD_PREVIEW),
            discardCount = state.discardPile.size,
            burnedCount = state.burned.size,
            requirement = ZpRules.requirement(state.discardPile, state.rules),
            legal = viewer?.let { legalMoves(state, it) } ?: ZpLegalMoves.NONE,
            log = state.log.takeLast(ZpPlayerView.LOG_PREVIEW),
            result = ZpEngine.result(state),
            challenge = state.lastPlay?.let(::challengeView),
            challenges = state.plays.map(::challengeView),
            exchange = state.exchange,
        )
    }

    private fun challengeView(play: ZpLastPlay) = ZpChallengeView(play.playerId, play.cards, play.requirementBefore, play.id)

    fun legalMoves(state: ZpGameState, player: ZpPlayerState): ZpLegalMoves {
        state.exchange?.let { exchange ->
            return if (exchange.winnerId == player.id) ZpLegalMoves(giveCards = player.hand.sorted()) else ZpLegalMoves.NONE
        }
        val moves = turnMoves(state, player)
        val canChallenge = state.phase == ZpPhase.PLAYING && !player.isFinished && state.plays.any { it.playerId != player.id }
        return if (canChallenge) moves.copy(canChallenge = true) else moves
    }

    private fun turnMoves(state: ZpGameState, player: ZpPlayerState): ZpLegalMoves {
        val rules = state.rules
        return when (state.phase) {
            ZpPhase.FINISHED -> ZpLegalMoves.NONE
            ZpPhase.SWAPPING -> if (player.ready) {
                ZpLegalMoves.NONE
            } else {
                ZpLegalMoves(canSwap = player.hand.isNotEmpty() && player.faceUp.isNotEmpty(), canReady = true)
            }
            ZpPhase.PLAYING -> {
                if (state.turn.currentPlayerId != player.id) return ZpLegalMoves.NONE
                val source = player.activeSource ?: return ZpLegalMoves.NONE
                val pileHasCards = !state.discardPile.isEmpty
                if (source == CardSource.FACE_DOWN) {
                    ZpLegalMoves(
                        source = source,
                        blindIndices = player.faceDown.indices.toList(),
                        canPickUp = pileHasCards,
                        canGamble = rules.drawGamble && !state.drawPile.isEmpty,
                    )
                } else {
                    val playable = ZpRules.playableCards(player, state.discardPile, rules)
                    val fitting = ZpRules.fittingCards(player, state.discardPile, rules)
                    ZpLegalMoves(
                        source = source,
                        playableCards = playable.sorted(),
                        fittingCards = fitting.sorted(),
                        canPlayMultiple = rules.allowMultiple,
                        canPickUp = pileHasCards,
                        canGamble = rules.drawGamble && !state.drawPile.isEmpty,
                    )
                }
            }
        }
    }

    /** Players that may act right now: everyone not yet ready in the swap phase, otherwise the current player. */
    fun pendingActors(state: ZpGameState): Set<String> = state.exchange?.let { setOf(it.winnerId) } ?: when (state.phase) {
        ZpPhase.SWAPPING -> state.players.filter { !it.ready }.map { it.id }.toSet()
        ZpPhase.PLAYING -> setOfNotNull(state.turn.currentPlayerId)
        ZpPhase.FINISHED -> emptySet()
    }
}
