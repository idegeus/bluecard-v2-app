package nl.bluecard.engine.model

/**
 * Card ranks in ascending order (ace high). [value] is used for comparisons,
 * [code] for the compact notation and [label] for the Dutch on-card label
 * (B = boer, V = vrouw, H = heer, A = aas). [JOKER] only exists in games that use jokers (Pesten); its
 * "suit" just tells the red joker (hearts) from the black one (spades).
 */
enum class Rank(val value: Int, val code: String, val label: String) {
    TWO(2, "2", "2"),
    THREE(3, "3", "3"),
    FOUR(4, "4", "4"),
    FIVE(5, "5", "5"),
    SIX(6, "6", "6"),
    SEVEN(7, "7", "7"),
    EIGHT(8, "8", "8"),
    NINE(9, "9", "9"),
    TEN(10, "10", "10"),
    JACK(11, "J", "B"),
    QUEEN(12, "Q", "V"),
    KING(13, "K", "H"),
    ACE(14, "A", "A"),
    JOKER(15, "X", "Joker");

    val isJoker: Boolean get() = this == JOKER

    companion object {
        /** The thirteen ranks of a standard deck (no joker). */
        val STANDARD: List<Rank> = entries.filter { !it.isJoker }

        fun fromCode(code: String): Rank? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        fun fromValue(value: Int): Rank? = entries.firstOrNull { it.value == value }
    }
}
