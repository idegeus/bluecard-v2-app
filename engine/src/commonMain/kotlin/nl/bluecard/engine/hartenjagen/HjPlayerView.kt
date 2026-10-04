package nl.bluecard.engine.hartenjagen

import kotlinx.serialization.Serializable
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit

@Serializable
data class HjPublicPlayer(
    val id: String,
    val name: String,
    val handCount: Int,
    val score: Int,
    /** Penalty points taken so far in this deal. */
    val dealPoints: Int,
    val tricks: Int,
    /** Has chosen the cards to pass. */
    val hasPassed: Boolean,
)

@Serializable
data class HjLegalMoves(
    val playableCards: List<Card> = emptyList(),
    /** During passing: how many cards to choose (0 when done or not passing). */
    val passCount: Int = 0,
) {
    companion object {
        val NONE = HjLegalMoves()
    }
}

@Serializable
data class HjPlayerView(
    val viewerId: String,
    val stateVersion: Long,
    val phase: HjPhase,
    val rules: HjHouseRules,
    val players: List<HjPublicPlayer>,
    val myHand: List<Card>,
    val currentPlayerId: String?,
    val trick: List<HjPlay>,
    val lastTrick: List<HjPlay>,
    val lastTrickWinnerId: String?,
    val deal: Int,
    val trickNumber: Int,
    val heartsBroken: Boolean,
    val passDirection: HjPassDirection,
    /** Who the viewer passes cards to this deal. */
    val passTargetId: String?,
    /** The cards the viewer chose to pass (while waiting for the others). */
    val myPassing: List<Card>?,
    val lastDealPoints: Map<String, Int>,
    val legal: HjLegalMoves,
    val log: List<HjLogEntry>,
    val result: GameResult? = null,
) {
    val me: HjPublicPlayer? get() = players.firstOrNull { it.id == viewerId }
    val isMyTurn: Boolean get() = phase == HjPhase.PLAYING && currentPlayerId == viewerId
    val leadSuit: Suit? get() = trick.firstOrNull()?.card?.suit
    fun player(id: String): HjPublicPlayer? = players.firstOrNull { it.id == id }

    companion object {
        const val LOG_PREVIEW = 60
    }
}

object HjViews {
    fun create(state: HjGameState, viewerId: String): HjPlayerView {
        val viewer = state.player(viewerId)
        return HjPlayerView(
            viewerId = viewerId,
            stateVersion = state.version,
            phase = state.phase,
            rules = state.rules,
            players = state.players.map {
                HjPublicPlayer(it.id, it.name, it.hand.size, it.score, it.taken.sumOf { c -> HjRules.points(c, state.rules) }, it.tricks, it.passing != null)
            },
            myHand = viewer?.hand?.sortedWith(HjRules.handOrder) ?: emptyList(),
            currentPlayerId = state.currentPlayerId,
            trick = state.trick,
            lastTrick = state.lastTrick,
            lastTrickWinnerId = state.lastTrickWinnerId,
            deal = state.deal,
            trickNumber = state.trickNumber,
            heartsBroken = state.heartsBroken,
            passDirection = state.passDirection,
            passTargetId = if (state.phase == HjPhase.PASSING) HjRules.passTarget(state, viewerId) else null,
            myPassing = viewer?.passing,
            lastDealPoints = state.lastDealPoints,
            legal = viewer?.let { legalMoves(state, it) } ?: HjLegalMoves.NONE,
            log = state.log.takeLast(HjPlayerView.LOG_PREVIEW),
            result = HjEngine.result(state),
        )
    }

    fun legalMoves(state: HjGameState, player: HjPlayerState): HjLegalMoves = when (state.phase) {
        HjPhase.PASSING -> if (player.passing == null) HjLegalMoves(player.hand, state.rules.passCount) else HjLegalMoves.NONE
        HjPhase.PLAYING -> if (state.currentPlayerId == player.id) HjLegalMoves(HjRules.playable(state, player)) else HjLegalMoves.NONE
        HjPhase.FINISHED -> HjLegalMoves.NONE
    }

    fun pendingActors(state: HjGameState): Set<String> = when (state.phase) {
        HjPhase.PASSING -> state.players.filter { it.passing == null }.map { it.id }.toSet()
        HjPhase.PLAYING -> setOfNotNull(state.currentPlayerId)
        HjPhase.FINISHED -> emptySet()
    }
}
