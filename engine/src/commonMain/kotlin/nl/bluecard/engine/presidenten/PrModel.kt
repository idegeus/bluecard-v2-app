package nl.bluecard.engine.presidenten

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Card

/**
 * House rules for Presidenten (also known as Kees / President / Arschloch). Cards are played in sets of equal
 * value; the next player must lay as many cards of a higher value, or pass. Whoever is out first is president.
 */
@Serializable
data class PrHouseRules(
    /** The 2 is the highest card (above the ace). Otherwise it is the lowest. */
    val twoHigh: Boolean = true,
    /** Jokers in the deck (0–2): the highest card, and they can stand in for any value in a set. */
    val jokers: Int = 0,
    /** Next round: the sloeber gives the president their best cards and gets cards of the president's choice back. */
    val exchange: Boolean = true,
    /** Once you pass you sit out the rest of the trick. Otherwise you may join in again on your next turn. */
    val passIsFinal: Boolean = true,
    /** The same value may be laid on top as well; the next player is then skipped. */
    val equalSkips: Boolean = false,
) {
    companion object {
        const val MAX_JOKERS = 2
    }
}

@Serializable
enum class PrPreset {
    CLASSIC,
    WITH_JOKERS,
    ;

    val rules: PrHouseRules
        get() = when (this) {
            CLASSIC -> PrHouseRules()
            WITH_JOKERS -> PrHouseRules(jokers = 2, equalSkips = true)
        }

    companion object {
        fun matching(rules: PrHouseRules): PrPreset? = entries.firstOrNull { it.rules == rules }
    }
}

/** Rank at the table, from the previous round (drives the card exchange). */
@Serializable
enum class PrTitle { PRESIDENT, VICE_PRESIDENT, CITIZEN, VICE_SCUM, SCUM }

@Serializable
enum class PrPhase { EXCHANGING, PLAYING, FINISHED }

@Serializable
data class PrPlayerState(
    val id: String,
    val name: String,
    val hand: List<Card> = emptyList(),
    val finishedPosition: Int? = null,
    /** Title from the previous round, if any. */
    val title: PrTitle? = null,
) {
    val isFinished: Boolean get() = finishedPosition != null
}

/** One set laid on the trick. */
@Serializable
data class PrPlay(val playerId: String, val cards: List<Card>)

/**
 * Card exchange at the start of a round: [lowId] already handed over their [count] best cards to [highId]
 * ([received]); [highId] still has to give [count] cards of their choice back.
 */
@Serializable
data class PrExchange(val highId: String, val lowId: String, val count: Int, val received: List<Card>, val done: Boolean = false)

@Serializable
data class PrGameState(
    val rules: PrHouseRules,
    val players: List<PrPlayerState>,
    val phase: PrPhase = PrPhase.PLAYING,
    val currentPlayerId: String? = null,
    /** Sets laid on the current trick, oldest first; the last one is the set to beat. */
    val plays: List<PrPlay> = emptyList(),
    /** Players who passed (this trick when passes are final, otherwise since the last play). */
    val passed: Set<String> = emptySet(),
    val lastPlayerId: String? = null,
    /** The trick that was just won (shown briefly while the next one starts). */
    val lastTrick: List<PrPlay> = emptyList(),
    val lastTrickWinnerId: String? = null,
    val exchanges: List<PrExchange> = emptyList(),
    /** Cards of finished tricks. */
    val outOfPlay: List<Card> = emptyList(),
    val finishOrder: List<String> = emptyList(),
    val log: List<PrLogEntry> = emptyList(),
    val nextLogSeq: Long = 1,
    /** Running counters per player for the summary after the game (see [nl.bluecard.engine.core.PlayerStats]). */
    val stats: Map<String, nl.bluecard.engine.core.PlayerStats> = emptyMap(),
    val version: Long = 0,
    val seed: Long = 0,
) {
    fun player(id: String): PrPlayerState? = players.firstOrNull { it.id == id }
    fun indexOf(id: String): Int = players.indexOfFirst { it.id == id }
    val activePlayers: List<PrPlayerState> get() = players.filter { !it.isFinished }
    val top: PrPlay? get() = plays.lastOrNull()

    /** Every card in the game (tests check that none appears or disappears). */
    fun allCards(): List<Card> = players.flatMap { it.hand } + plays.flatMap { it.cards } + outOfPlay
}

@Serializable
sealed interface PrAction {
    /** Lay a set: one or more cards of the same value (jokers may join any set). */
    @Serializable
    @SerialName("PLAY")
    data class Play(val cards: List<Card>) : PrAction

    @Serializable
    @SerialName("PASS")
    data object Pass : PrAction

    /** During the exchange: the cards the president / vice-president gives back. */
    @Serializable
    @SerialName("GIVE")
    data class GiveCards(val cards: List<Card>) : PrAction
}

@Serializable
sealed interface PrEvent {
    @Serializable
    @SerialName("started")
    data class GameStarted(val startingPlayerId: String) : PrEvent

    /** [fromId] (the sloeber) handed their best cards to [toId]. */
    @Serializable
    @SerialName("tribute")
    data class TributeGiven(val fromId: String, val toId: String, val count: Int) : PrEvent

    @Serializable
    @SerialName("returned")
    data class CardsReturned(val fromId: String, val toId: String, val count: Int) : PrEvent

    @Serializable
    @SerialName("played")
    data class CardsPlayed(val playerId: String, val cards: List<Card>) : PrEvent

    @Serializable
    @SerialName("passed")
    data class Passed(val playerId: String) : PrEvent

    @Serializable
    @SerialName("skipped")
    data class PlayerSkipped(val byPlayerId: String, val skippedId: String) : PrEvent

    @Serializable
    @SerialName("trick")
    data class TrickWon(val playerId: String) : PrEvent

    @Serializable
    @SerialName("player_out")
    data class PlayerFinished(val playerId: String, val position: Int) : PrEvent

    @Serializable
    @SerialName("resigned")
    data class Resigned(val playerId: String) : PrEvent

    @Serializable
    @SerialName("game_over")
    data class GameOver(val winnerId: String, val loserId: String?) : PrEvent
}

@Serializable
data class PrLogEntry(val seq: Long, val event: PrEvent)

enum class PrRejectReason {
    UNKNOWN_PLAYER,
    GAME_FINISHED,
    NOT_YOUR_TURN,
    WRONG_PHASE,
    EMPTY_SELECTION,
    DUPLICATE_CARDS,
    CARD_NOT_AVAILABLE,
    MIXED_RANKS,
    WRONG_COUNT,
    TOO_LOW,
    CANNOT_PASS_LEAD,
    NOTHING_TO_GIVE,
    WRONG_GIVE_COUNT,
    TOO_FEW_PLAYERS,
    TOO_MANY_PLAYERS,
    INVALID_JOKERS,
}
