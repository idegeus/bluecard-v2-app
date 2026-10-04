package nl.bluecard.engine.pesten

import kotlinx.serialization.Serializable
import nl.bluecard.engine.model.Rank

/** Effect a rank can have in Pesten. Assigned per rank in [PsHouseRules.effects]; jokers are always "pak 5". */
@Serializable
enum class PsEffect {
    /** Ordinary card: same suit or same value. */
    NONE,

    /** "Pak 2": the next player draws two cards (unless they stack another draw card). */
    DRAW_TWO,

    /** "Blijft" / "nog een keer": the same player plays again (the 7 and the king). */
    PLAY_AGAIN,

    /** "Wacht": the next player is skipped, one per card played (the 8). */
    SKIP,

    /** Wild card: fits on anything and the player chooses the suit that must follow (the jack). */
    CHOOSE_SUIT,

    /** "Keer": reverses the direction of play (the ace). With two players it acts as "play again". */
    REVERSE,
}

/**
 * Configurable house rules for Pesten. Everything that differs between variants lives here, so the engine
 * itself contains no hard-coded variant logic.
 */
@Serializable
data class PsHouseRules(
    val effects: Map<Rank, PsEffect> = CLASSIC_EFFECTS,
    /** Jokers in the deck (0–2). A joker fits on anything and makes the next player draw [jokerDraw]. */
    val jokers: Int = 2,
    val jokerDraw: Int = 5,
    /** Cards dealt to every player (5–8). */
    val handSize: Int = 7,
    /** A "pak"-card may be answered with another one; the next player then draws the total. */
    val stackDraws: Boolean = true,
    /** Several cards of the same value may be played at once. */
    val allowMultiple: Boolean = false,
    /** "Laatste kaart!" must be called; forgetting it and getting caught costs [lastCardPenalty] cards. */
    val lastCardCall: Boolean = true,
    val lastCardPenalty: Int = 2,
    /** Going out on a special card is allowed. When false, doing so costs one card. */
    val finishOnSpecial: Boolean = false,
    /** Keep playing until one player is left (the "pestkop"); otherwise the game ends at the first winner. */
    val playUntilLast: Boolean = false,
    /** After drawing the cards of a "pak"-card (2 or joker) you may lay a card yourself right away. */
    val playAfterPenalty: Boolean = true,
    /** Same meaning as in Zweeds Pesten: false = any card may be laid and others can call "Vals!". */
    val enforceRules: Boolean = false,
    /** Cards a caught cheater (on top of the cards taken back) or a false accuser has to draw. */
    val challengePenalty: Int = 2,
    /** In the next round the winner gives the loser a card of their choice and gets the loser's best card. */
    val winnerSwap: Boolean = false,
    /** Each time the same player is caught cheating again, one more penalty card. */
    val escalatingPenalty: Boolean = true,
) {
    fun effectOf(rank: Rank): PsEffect = effects[rank] ?: PsEffect.NONE

    fun withEffect(rank: Rank, effect: PsEffect): PsHouseRules {
        val updated = if (effect == PsEffect.NONE) effects - rank else effects + (rank to effect)
        return copy(effects = updated)
    }

    /** Ranks that currently have a non-NONE effect, ascending. */
    fun specialRanks(): List<Pair<Rank, PsEffect>> =
        Rank.STANDARD.mapNotNull { rank -> effectOf(rank).takeIf { it != PsEffect.NONE }?.let { rank to it } }

    companion object {
        const val MIN_HAND_SIZE = 5
        const val MAX_HAND_SIZE = 8
        const val MAX_JOKERS = 2

        /** Most common Dutch rules. */
        val CLASSIC_EFFECTS: Map<Rank, PsEffect> = mapOf(
            Rank.TWO to PsEffect.DRAW_TWO,
            Rank.SEVEN to PsEffect.PLAY_AGAIN,
            Rank.EIGHT to PsEffect.SKIP,
            Rank.JACK to PsEffect.CHOOSE_SUIT,
            Rank.KING to PsEffect.PLAY_AGAIN,
            Rank.ACE to PsEffect.REVERSE,
        )
    }
}

/** Named rule sets offered in the settings. */
enum class PsPreset(val rules: PsHouseRules) {
    /** 2 pak 2, joker pak 5, 7 en heer nog een keer, 8 wacht, boer kleur kiezen, aas keer. */
    CLASSIC(PsHouseRules()),

    /** Only the classic attack cards: 2, joker, 8 and the jack. */
    SIMPLE(
        PsHouseRules(
            effects = mapOf(Rank.TWO to PsEffect.DRAW_TWO, Rank.EIGHT to PsEffect.SKIP, Rank.JACK to PsEffect.CHOOSE_SUIT),
        ),
    ),

    /** Classic, without jokers. */
    NO_JOKERS(PsHouseRules(jokers = 0)),
    ;

    companion object {
        /** The preset with these rules. Rule enforcement is chosen separately and ignored here. */
        fun matching(rules: PsHouseRules): PsPreset? = entries.firstOrNull {
            it.rules == rules.copy(enforceRules = it.rules.enforceRules)
        }
    }
}
