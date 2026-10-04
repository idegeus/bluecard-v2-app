package nl.bluecard.engine.model

/** The four French suits. [code] is used in the compact card notation ("10H"). */
enum class Suit(val code: Char, val symbol: String, val isRed: Boolean) {
    CLUBS('C', "♣", false),
    DIAMONDS('D', "♦", true),
    HEARTS('H', "♥", true),
    SPADES('S', "♠", false);

    companion object {
        fun fromCode(code: Char): Suit? = entries.firstOrNull { it.code == code.uppercaseChar() }
    }
}
