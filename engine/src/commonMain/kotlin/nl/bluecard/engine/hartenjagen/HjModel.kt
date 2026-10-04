package nl.bluecard.engine.hartenjagen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit

/**
 * House rules for Hartenjagen (Hearts). Each heart is a penalty point, the queen of spades and (in the Dutch game)
 * the jack of clubs cost extra. Deals are played until someone reaches [targetScore]; the lowest score wins.
 */
@Serializable
data class HjHouseRules(
    val queenPoints: Int = 5,
    val jackPoints: Int = 2,
    /** Cards passed to a neighbour before each deal (0 or 3). */
    val passCount: Int = 0,
    /** Hearts may only be led once a heart has been played (or when you have nothing else). */
    val heartsBroken: Boolean = true,
    /** No hearts or queen of spades on the first trick (unless you have nothing else). */
    val noPointsFirstTrick: Boolean = false,
    /** Taking every penalty card gives everyone else those points instead. */
    val shootTheMoon: Boolean = false,
    /** The game ends when someone reaches this score; 0 = one deal. */
    val targetScore: Int = 50,
    /** The lowest club (normally the 2) leads the first trick of every deal. Otherwise the lead rotates. */
    val clubsLead: Boolean = false,
) {
    companion object {
        val TARGETS = listOf(0, 30, 50, 100)
    }
}

@Serializable
enum class HjPreset {
    CLASSIC,
    INTERNATIONAL,
    ;

    val rules: HjHouseRules
        get() = when (this) {
            CLASSIC -> HjHouseRules()
            INTERNATIONAL -> HjHouseRules(
                queenPoints = 13,
                jackPoints = 0,
                passCount = 3,
                heartsBroken = true,
                noPointsFirstTrick = true,
                shootTheMoon = true,
                targetScore = 100,
                clubsLead = true,
            )
        }

    companion object {
        fun matching(rules: HjHouseRules): HjPreset? = entries.firstOrNull { it.rules == rules }
    }
}

@Serializable
enum class HjPhase { PASSING, PLAYING, FINISHED }

@Serializable
enum class HjPassDirection { LEFT, RIGHT, ACROSS, NONE }

@Serializable
data class HjPlayerState(
    val id: String,
    val name: String,
    val hand: List<Card> = emptyList(),
    /** Penalty cards won this deal. */
    val taken: List<Card> = emptyList(),
    val tricks: Int = 0,
    /** Total over the whole game. */
    val score: Int = 0,
    /** Cards chosen to pass (hidden from others) during the passing phase. */
    val passing: List<Card>? = null,
)

@Serializable
data class HjPlay(val playerId: String, val card: Card)

@Serializable
data class HjGameState(
    val rules: HjHouseRules,
    val players: List<HjPlayerState>,
    val phase: HjPhase = HjPhase.PLAYING,
    val deal: Int = 0,
    val passDirection: HjPassDirection = HjPassDirection.NONE,
    val currentPlayerId: String? = null,
    val trick: List<HjPlay> = emptyList(),
    val lastTrick: List<HjPlay> = emptyList(),
    val lastTrickWinnerId: String? = null,
    /** Tricks played in this deal. */
    val trickNumber: Int = 0,
    val heartsBroken: Boolean = false,
    /** Cards left out so everybody gets the same number (3, 5 or 6 players). */
    val removed: List<Card> = emptyList(),
    /** Penalty points of the last finished deal, per player. */
    val lastDealPoints: Map<String, Int> = emptyMap(),
    /** The player who gave up (ranked last). */
    val resignedId: String? = null,
    /** Cards without penalty points from finished tricks of this deal. */
    val discards: List<Card> = emptyList(),
    val log: List<HjLogEntry> = emptyList(),
    val nextLogSeq: Long = 1,
    /** Running counters per player for the summary after the game (see [nl.bluecard.engine.core.PlayerStats]). */
    val stats: Map<String, nl.bluecard.engine.core.PlayerStats> = emptyMap(),
    val version: Long = 0,
    val seed: Long = 0,
) {
    fun player(id: String): HjPlayerState? = players.firstOrNull { it.id == id }
    fun indexOf(id: String): Int = players.indexOfFirst { it.id == id }
    val leadSuit: Suit? get() = trick.firstOrNull()?.card?.suit

    fun allCards(): List<Card> =
        players.flatMap { it.hand + (it.passing ?: emptyList()) } + trick.map { it.card } + removed + outOfPlay

    /** Cards of finished tricks in this deal. */
    val outOfPlay: List<Card> get() = players.flatMap { it.taken } + discards
}

@Serializable
sealed interface HjAction {
    @Serializable
    @SerialName("PASS")
    data class PassCards(val cards: List<Card>) : HjAction

    @Serializable
    @SerialName("PLAY")
    data class Play(val card: Card) : HjAction
}

@Serializable
sealed interface HjEvent {
    @Serializable
    @SerialName("deal")
    data class DealStarted(val deal: Int, val direction: HjPassDirection) : HjEvent

    @Serializable
    @SerialName("passed")
    data class CardsPassed(val playerId: String) : HjEvent

    @Serializable
    @SerialName("lead")
    data class Leads(val playerId: String) : HjEvent

    @Serializable
    @SerialName("played")
    data class Played(val playerId: String, val card: Card) : HjEvent

    @Serializable
    @SerialName("hearts_broken")
    data class HeartsBroken(val playerId: String) : HjEvent

    @Serializable
    @SerialName("trick")
    data class TrickWon(val playerId: String, val points: Int) : HjEvent

    @Serializable
    @SerialName("moon")
    data class MoonShot(val playerId: String, val points: Int) : HjEvent

    @Serializable
    @SerialName("scored")
    data class DealScored(val deal: Int, val points: Map<String, Int>, val totals: Map<String, Int>) : HjEvent

    @Serializable
    @SerialName("resigned")
    data class Resigned(val playerId: String) : HjEvent

    @Serializable
    @SerialName("game_over")
    data class GameOver(val winnerId: String, val loserId: String?) : HjEvent
}

@Serializable
data class HjLogEntry(val seq: Long, val event: HjEvent)

enum class HjRejectReason {
    UNKNOWN_PLAYER,
    GAME_FINISHED,
    NOT_YOUR_TURN,
    WRONG_PHASE,
    CARD_NOT_AVAILABLE,
    DUPLICATE_CARDS,
    WRONG_PASS_COUNT,
    ALREADY_PASSED,
    MUST_FOLLOW_SUIT,
    MUST_LEAD_CLUB,
    HEARTS_NOT_BROKEN,
    NO_POINTS_FIRST_TRICK,
    TOO_FEW_PLAYERS,
    TOO_MANY_PLAYERS,
}
