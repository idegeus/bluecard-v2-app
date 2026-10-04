package nl.bluecard.engine.presidenten

import kotlinx.serialization.Serializable
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.model.Card

@Serializable
data class PrPublicPlayer(
    val id: String,
    val name: String,
    val handCount: Int,
    val finishedPosition: Int? = null,
    val title: PrTitle? = null,
    /** Passed in the current trick. */
    val passed: Boolean = false,
)

@Serializable
data class PrLegalMoves(
    /** Cards that are part of at least one set that may be laid now. */
    val playableCards: List<Card> = emptyList(),
    /** Every set that may be laid now (used by the UI to complete a selection, and by bots). */
    val sets: List<List<Card>> = emptyList(),
    /** How many cards must be laid (null when leading: any number of the same value). */
    val requiredCount: Int? = null,
    val canPass: Boolean = false,
    /** During the exchange: how many cards to give back (0 = nothing to give). */
    val giveCount: Int = 0,
) {
    companion object {
        val NONE = PrLegalMoves()
    }
}

/** What one player may see. Other hands are only counted. */
@Serializable
data class PrPlayerView(
    val viewerId: String,
    val stateVersion: Long,
    val phase: PrPhase,
    val rules: PrHouseRules,
    val players: List<PrPublicPlayer>,
    val myHand: List<Card>,
    val currentPlayerId: String?,
    val plays: List<PrPlay>,
    val lastTrick: List<PrPlay>,
    val lastTrickWinnerId: String?,
    /** Exchanges of this round (who gives whom how many); [PrExchange.received] only for the viewer's own. */
    val exchanges: List<PrExchange>,
    val legal: PrLegalMoves,
    val log: List<PrLogEntry>,
    val result: GameResult? = null,
) {
    val me: PrPublicPlayer? get() = players.firstOrNull { it.id == viewerId }
    val isMyTurn: Boolean get() = phase == PrPhase.PLAYING && currentPlayerId == viewerId
    val top: PrPlay? get() = plays.lastOrNull()
    fun player(id: String): PrPublicPlayer? = players.firstOrNull { it.id == id }

    companion object {
        const val LOG_PREVIEW = 60
    }
}

object PrViews {
    fun create(state: PrGameState, viewerId: String): PrPlayerView {
        val viewer = state.player(viewerId)
        return PrPlayerView(
            viewerId = viewerId,
            stateVersion = state.version,
            phase = state.phase,
            rules = state.rules,
            players = state.players.map {
                PrPublicPlayer(it.id, it.name, it.hand.size, it.finishedPosition, it.title, passed = it.id in state.passed)
            },
            myHand = viewer?.hand?.sortedWith(PrRules.handOrder(state.rules)) ?: emptyList(),
            currentPlayerId = state.currentPlayerId,
            plays = state.plays,
            lastTrick = state.lastTrick,
            lastTrickWinnerId = state.lastTrickWinnerId,
            exchanges = state.exchanges.map { if (it.highId == viewerId || it.lowId == viewerId) it else it.copy(received = emptyList()) },
            legal = viewer?.let { legalMoves(state, it) } ?: PrLegalMoves.NONE,
            log = state.log.takeLast(PrPlayerView.LOG_PREVIEW),
            result = PrEngine.result(state),
        )
    }

    fun legalMoves(state: PrGameState, player: PrPlayerState): PrLegalMoves {
        when (state.phase) {
            PrPhase.FINISHED -> return PrLegalMoves.NONE
            PrPhase.EXCHANGING -> {
                val exchange = state.exchanges.firstOrNull { it.highId == player.id && !it.done } ?: return PrLegalMoves.NONE
                return PrLegalMoves(playableCards = player.hand, giveCount = exchange.count)
            }
            PrPhase.PLAYING -> Unit
        }
        if (state.currentPlayerId != player.id) return PrLegalMoves.NONE
        val sets = PrRules.legalSets(player.hand, state.top, state.rules)
        return PrLegalMoves(
            playableCards = player.hand.filter { card -> sets.any { set -> set.any { it.rank == card.rank } } },
            sets = sets,
            requiredCount = state.top?.cards?.size,
            canPass = state.plays.isNotEmpty(),
        )
    }

    fun pendingActors(state: PrGameState): Set<String> = when (state.phase) {
        PrPhase.EXCHANGING -> state.exchanges.filter { !it.done }.map { it.highId }.toSet()
        PrPhase.PLAYING -> setOfNotNull(state.currentPlayerId)
        PrPhase.FINISHED -> emptySet()
    }
}
