package nl.bluecard.engine.pesten

import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Deck
import nl.bluecard.engine.model.DiscardPile
import nl.bluecard.engine.model.Suit

@Serializable
enum class PsPhase { PLAYING, FINISHED }

@Serializable
data class PsPlayerState(
    val id: String,
    val name: String,
    val hand: List<Card> = emptyList(),
    /** Called "Laatste kaart!" (reset as soon as the hand grows beyond two cards again). */
    val announced: Boolean = false,
    /** 1-based finishing position once the player has no cards left. */
    val finishedPosition: Int? = null,
    /** How often this player was caught cheating (for the escalating penalty). */
    val cheatsCaught: Int = 0,
) {
    val isFinished: Boolean get() = finishedPosition != null
}

/**
 * A card play while rules are not enforced. Other players may call "Vals!" on it for
 * [nl.bluecard.engine.core.CheatWindow.MS] after it, also when others have played since.
 */
@Serializable
data class PsLastPlay(
    val playerId: String,
    val cards: List<Card>,
    /** Whether the play followed the rules. Never sent to players: they have to spot it themselves. */
    val legal: Boolean,
    /**
     * The state just before the play, to undo a cheat caught right away completely. Only kept for the newest play
     * (null once another play follows).
     */
    val before: PsGameState? = null,
    /** What lay on the table before the play (public), so players can judge it. */
    val topBefore: Card? = null,
    val wishedSuitBefore: Suit? = null,
    val pendingDrawBefore: Int = 0,
    /** Identifies the play (the log sequence number when it was made). */
    val id: Long = 0,
    /** The state's next log sequence right after the play: while unchanged, nothing else has happened since. */
    val logSeqAfter: Long = 0,
    /** When the host saw the play (wall-clock millis); set by [PsEngine.tick]. */
    val atMs: Long? = null,
)

/** Complete, authoritative game state. Only the host holds this; players receive a [PsPlayerView]. */
@Serializable
data class PsGameState(
    val rules: PsHouseRules,
    val players: List<PsPlayerState>,
    val drawPile: Deck,
    val discardPile: DiscardPile = DiscardPile(),
    val phase: PsPhase = PsPhase.PLAYING,
    val currentPlayerId: String? = null,
    /** +1 clockwise in seat order, -1 reversed. */
    val direction: Int = 1,
    /** Cards the current player has to draw ("pak 2", jokers), unless they stack another draw card. */
    val pendingDraw: Int = 0,
    /** Suit chosen with a jack; the next card has to follow it. */
    val wishedSuit: Suit? = null,
    /** The card the current player drew this turn; only that card may still be played, or the player passes. */
    val drawnCard: Card? = null,
    /** A player who went down to one card without calling "Laatste kaart!" and can still be caught. */
    val forgottenId: String? = null,
    val finishOrder: List<String> = emptyList(),
    val log: List<PsLogEntry> = emptyList(),
    val nextLogSeq: Long = 1,
    /** Running counters per player for the summary after the game (see [nl.bluecard.engine.core.PlayerStats]). */
    val stats: Map<String, nl.bluecard.engine.core.PlayerStats> = emptyMap(),
    val version: Long = 0,
    val seed: Long = 0,
    /** Plays still open to a cheating accusation, oldest first (only when rules are not enforced). */
    val plays: List<PsLastPlay> = emptyList(),
    /** Winner still has to give the loser a card (house rule); nothing else happens until then. */
    val exchange: nl.bluecard.engine.core.WinnerExchange? = null,
) {
    fun player(id: String): PsPlayerState? = players.firstOrNull { it.id == id }

    /** The newest play that can still be called out, if any. */
    val lastPlay: PsLastPlay? get() = plays.lastOrNull()
    fun indexOf(id: String): Int = players.indexOfFirst { it.id == id }
    val currentPlayer: PsPlayerState? get() = currentPlayerId?.let { player(it) }
    val activePlayers: List<PsPlayerState> get() = players.filter { !it.isFinished }

    /** Every card in the game (used by tests to verify that no card ever appears or disappears). */
    fun allCards(): List<Card> = drawPile.cards + discardPile.cards + players.flatMap { it.hand }
}
