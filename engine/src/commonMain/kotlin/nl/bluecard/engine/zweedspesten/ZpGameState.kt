package nl.bluecard.engine.zweedspesten

import kotlinx.serialization.Serializable
import nl.bluecard.engine.core.CheatWindow
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Deck
import nl.bluecard.engine.model.DiscardPile

@Serializable
enum class ZpPhase { SWAPPING, PLAYING, FINISHED }

/** Where a player currently has to play from. */
@Serializable
enum class CardSource { HAND, FACE_UP, FACE_DOWN }

@Serializable
data class ZpPlayerState(
    val id: String,
    val name: String,
    val hand: List<Card> = emptyList(),
    val faceUp: List<Card> = emptyList(),
    val faceDown: List<Card> = emptyList(),
    val ready: Boolean = false,
    /** 1-based finishing position once the player has no cards left. */
    val finishedPosition: Int? = null,
    /** How often this player was caught cheating (for the escalating penalty). */
    val cheatsCaught: Int = 0,
    /** The card that lay on top of the pile this player last picked up, and how often they laid such a card straight back. */
    val bounceCard: Card? = null,
    val bounces: Int = 0,
) {
    val totalCards: Int get() = hand.size + faceUp.size + faceDown.size
    val isFinished: Boolean get() = finishedPosition != null

    /** Hand first, then face-up cards, then the blind face-down cards. */
    val activeSource: CardSource?
        get() = when {
            hand.isNotEmpty() -> CardSource.HAND
            faceUp.isNotEmpty() -> CardSource.FACE_UP
            faceDown.isNotEmpty() -> CardSource.FACE_DOWN
            else -> null
        }

    fun cardsIn(source: CardSource): List<Card> = when (source) {
        CardSource.HAND -> hand
        CardSource.FACE_UP -> faceUp
        CardSource.FACE_DOWN -> faceDown
    }

    fun without(source: CardSource, cards: List<Card>): ZpPlayerState = when (source) {
        CardSource.HAND -> copy(hand = hand - cards.toSet())
        CardSource.FACE_UP -> copy(faceUp = faceUp - cards.toSet())
        CardSource.FACE_DOWN -> copy(faceDown = faceDown - cards.toSet())
    }
}

/** Whose turn it is and in which direction play proceeds (+1 clockwise in seat order, -1 reversed). */
@Serializable
data class ZpTurnState(
    val currentPlayerId: String? = null,
    val direction: Int = 1,
)

/**
 * A card play while rules are not enforced. Other players may call "Vals!" on it for [CheatWindow.MS] after it,
 * also when others have played since.
 */
@Serializable
data class ZpLastPlay(
    val playerId: String,
    val cards: List<Card>,
    /** Whether the play followed the rules. Never sent to players: they have to spot it themselves. */
    val legal: Boolean,
    val requirementBefore: ZpRequirement,
    /**
     * The state just before the play, to undo a cheat caught right away completely. Only kept for the newest play
     * (null once another play follows).
     */
    val before: ZpGameState? = null,
    /** Identifies the play (the log sequence number when it was made). */
    val id: Long = 0,
    /** The state's next log sequence right after the play: while unchanged, nothing else has happened since. */
    val logSeqAfter: Long = 0,
    /** When the host saw the play (wall-clock millis); set by [ZpEngine.tick]. */
    val atMs: Long? = null,
)

/** Complete, authoritative game state. Only the host holds this; players receive a [ZpPlayerView]. */
@Serializable
data class ZpGameState(
    val rules: ZpHouseRules,
    val players: List<ZpPlayerState>,
    val drawPile: Deck,
    val discardPile: DiscardPile = DiscardPile(),
    val burned: List<Card> = emptyList(),
    val phase: ZpPhase,
    val turn: ZpTurnState = ZpTurnState(),
    val finishOrder: List<String> = emptyList(),
    val log: List<ZpLogEntry> = emptyList(),
    val nextLogSeq: Long = 1,
    /** Running counters per player for the summary after the game (see [nl.bluecard.engine.core.PlayerStats]). */
    val stats: Map<String, nl.bluecard.engine.core.PlayerStats> = emptyMap(),
    val version: Long = 0,
    val seed: Long = 0,
    /** Plays still open to a cheating accusation, oldest first (only when rules are not enforced). */
    val plays: List<ZpLastPlay> = emptyList(),
    /** Winner still has to give the loser a card (house rule); nothing else happens until then. */
    val exchange: nl.bluecard.engine.core.WinnerExchange? = null,
) {
    fun player(id: String): ZpPlayerState? = players.firstOrNull { it.id == id }
    fun indexOf(id: String): Int = players.indexOfFirst { it.id == id }
    val currentPlayer: ZpPlayerState? get() = turn.currentPlayerId?.let { player(it) }
    val activePlayers: List<ZpPlayerState> get() = players.filter { !it.isFinished }

    /** The newest play that can still be called out, if any. */
    val lastPlay: ZpLastPlay? get() = plays.lastOrNull()

    /** Every card in the game; always exactly 52 (used by tests to verify conservation). */
    fun allCards(): List<Card> =
        drawPile.cards + discardPile.cards + burned + players.flatMap { it.hand + it.faceUp + it.faceDown }
}
