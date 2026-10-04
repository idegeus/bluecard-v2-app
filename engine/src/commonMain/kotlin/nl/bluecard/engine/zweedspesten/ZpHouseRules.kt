package nl.bluecard.engine.zweedspesten

import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Rank

/** Effect a rank can have. Assigned per rank in [ZpHouseRules.effects]. */
@Serializable
enum class ZpEffect {
    /** Ordinary card: must be equal to or higher than the top card. */
    NONE,

    /** Always playable; afterwards anything may be played (the "2"). */
    RESET,

    /** Next player must play this value or lower (the "7"). */
    LOWER,

    /** Playable on anything except a LOWER card; burns the discard pile (the "10"). */
    BURN,

    /** Next player is skipped, one per card played. */
    SKIP,

    /** Reverses the direction of play, once per card played. */
    REVERSE;

    /**
     * Cards with these effects may be played regardless of the top card's value — except after a LOWER
     * card: "7 or lower" applies to every card (see [ZpRules.canPlayRank]).
     */
    val alwaysPlayable: Boolean get() = this == RESET || this == BURN
}

@Serializable
enum class StartRule {
    /** The player holding the lowest ordinary card (3♣ before 3♦ …) starts. */
    LOWEST_CARD,

    /** A random player starts. */
    RANDOM,
}

/**
 * Configurable house rules. Everything that differs between "Zweeds Pesten" variants lives here,
 * so the engine itself contains no hard-coded variant logic.
 */
@Serializable
data class ZpHouseRules(
    val effects: Map<Rank, ZpEffect> = CLASSIC_EFFECTS,
    /** Number of hand cards dealt and refilled to (3–5). Face-up and face-down are always 3. */
    val handSize: Int = 3,
    /** Multiple cards of the same rank may be played together. */
    val allowMultiple: Boolean = true,
    /** Four consecutive cards of the same rank on the pile burn it. */
    val fourOfAKindBurns: Boolean = true,
    /** After burning the pile the same player plays again. */
    val burnGivesExtraTurn: Boolean = true,
    /** After a LOWER card the next card may also be equal ("7 or lower") instead of strictly lower. */
    val lowerIncludesEqual: Boolean = true,
    /** Players may swap hand cards with their face-up cards before the game starts. */
    val swapPhase: Boolean = true,
    /** A player may turn over the top card of the draw pile and play it if it fits. */
    val drawGamble: Boolean = false,
    /** When the draw pile is empty, burned cards are shuffled into a new draw pile. */
    val reshuffleBurned: Boolean = false,
    /** Keep playing until one player is left (the "pestkop"); otherwise stop at the first winner. */
    val playUntilLast: Boolean = true,
    val startRule: StartRule = StartRule.LOWEST_CARD,
    /**
     * When false (the default) the app behaves like a real card table: any card may be laid down and the
     * other players have to watch out for cheating. When true, the engine refuses cards that do not fit.
     * Turn order, card ownership and "same rank" for multiple cards are always enforced.
     */
    val enforceRules: Boolean = false,
    /**
     * Whoever takes the pile (also after a blind card or gamble that did not fit) may start the new pile
     * right away instead of passing the turn. Not after a "Vals!" penalty.
     */
    val playAfterPickUp: Boolean = false,
    /** In the next round the winner gives the loser a card of their choice and gets the loser's best card. */
    val winnerSwap: Boolean = false,
    /** Each time the same player is caught cheating again, one more card from the draw pile on top of the pile. */
    val escalatingPenalty: Boolean = true,
) {
    fun effectOf(rank: Rank): ZpEffect = effects[rank] ?: ZpEffect.NONE

    fun withEffect(rank: Rank, effect: ZpEffect): ZpHouseRules {
        val updated = if (effect == ZpEffect.NONE) effects - rank else effects + (rank to effect)
        return copy(effects = updated)
    }

    /** Ranks that currently have a non-NONE effect, ascending. */
    fun specialRanks(): List<Pair<Rank, ZpEffect>> =
        Rank.STANDARD.mapNotNull { rank -> effectOf(rank).takeIf { it != ZpEffect.NONE }?.let { rank to it } }

    companion object {
        const val FACE_UP_COUNT = 3
        const val FACE_DOWN_COUNT = 3
        const val MIN_HAND_SIZE = 3
        const val MAX_HAND_SIZE = 5

        val CLASSIC_EFFECTS: Map<Rank, ZpEffect> = mapOf(
            Rank.TWO to ZpEffect.RESET,
            Rank.SEVEN to ZpEffect.LOWER,
            Rank.TEN to ZpEffect.BURN,
        )
    }
}

/** Named rule sets offered in the settings. */
enum class ZpPreset(val rules: ZpHouseRules) {
    /** Most common Dutch rules: 2 reset, 7 lower, 10 burns. */
    CLASSIC(ZpHouseRules()),

    /** Classic, but whoever takes the pile starts the new one ("TIS-modus"). */
    TIS(ZpHouseRules(playAfterPickUp = true)),

    /** Extra "pest" cards: 8 skips, jack reverses, gambling on the draw pile allowed. */
    PESTKOP(
        ZpHouseRules(
            effects = ZpHouseRules.CLASSIC_EFFECTS + mapOf(
                Rank.EIGHT to ZpEffect.SKIP,
                Rank.JACK to ZpEffect.REVERSE,
            ),
            drawGamble = true,
        ),
    );

    companion object {
        /** The preset with these rules. Rule enforcement is chosen separately and ignored here. */
        fun matching(rules: ZpHouseRules): ZpPreset? = entries.firstOrNull {
            it.rules == rules.copy(enforceRules = it.rules.enforceRules)
        }
    }
}
