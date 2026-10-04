package nl.bluecard.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.stats.Progress
import nl.bluecard.app.stats.Requirement

/** How the back of a card is decorated. */
enum class BackPattern { LATTICE, DOTS, STRIPES, SUNBURST, HOLO, RAINBOW }

/**
 * Card backs. Most are unlocked by playing (see [Requirement]); [premiumPriceCents] marks a paid skin (not for sale yet: it stays
 * locked and only shows its price).
 */
enum class CardBackSkin(
    val id: String,
    val requirement: Requirement,
    val base: Color,
    val dark: Color,
    val emblem: Color,
    val pattern: BackPattern,
    val premiumPriceCents: Int? = null,
) {
    CLASSIC("classic", Requirement.Free, Color(0xFF1558B0), Color(0xFF0D3C7A), Color.White, BackPattern.LATTICE),
    CHERRY("cherry", Requirement.Wins(3), Color(0xFFC62828), Color(0xFF7F1414), Color.White, BackPattern.LATTICE),
    EMERALD("emerald", Requirement.GamePlayed(GameKind.PESTEN, 10), Color(0xFF1B8A5A), Color(0xFF0E5235), Color(0xFFFFF3C4), BackPattern.DOTS),
    MIDNIGHT_GOLD("midnight_gold", Requirement.GameWins(GameKind.PRESIDENTEN, 5), Color(0xFF1A1F3A), Color(0xFF0B0E20), Color(0xFFFFC94D), BackPattern.STRIPES),
    ORANGE_LION("orange_lion", Requirement.GameWins(GameKind.ZWEEDS_PESTEN, 10), Color(0xFFFF7A00), Color(0xFFB84800), Color.White, BackPattern.SUNBURST),
    DIAMOND_HOLO("diamond_holo", Requirement.Free, Color(0xFF2B1055), Color(0xFF120526), Color.White, BackPattern.HOLO, premiumPriceCents = 99_00),
    RAINBOW_ROAD("rainbow_road", Requirement.Wins(20), Color(0xFF1B0B4A), Color(0xFF0B0420), Color.White, BackPattern.RAINBOW),
    ;

    val isPremium: Boolean get() = premiumPriceCents != null

    companion object {
        fun fromId(id: String?): CardBackSkin? = entries.firstOrNull { it.id == id }
    }
}

/** How a table cloth is drawn. */
enum class TableLook {
    /** Woollen felt with light and grain. */
    FELT,

    /** Pure black, nothing else (saves power on OLED screens). */
    OLED,

    /** Space with a moving rainbow road. */
    RAINBOW,
}

/** Table cloths: the felt colour of every screen. */
enum class TableSkin(
    val id: String,
    val requirement: Requirement,
    val felt: Color,
    val feltDark: Color,
    val feltLight: Color,
    val onFeltMuted: Color,
    val look: TableLook = TableLook.FELT,
) {
    GREEN("green", Requirement.Free, Color(0xFF0F5A43), Color(0xFF0A3D2E), Color(0xFF1B7358), Color(0xFFB9D6CA)),
    BLUE("blue", Requirement.GamePlayed(GameKind.HARTENJAGEN, 5), Color(0xFF0F3F6E), Color(0xFF0A2A4A), Color(0xFF1D5A96), Color(0xFFB7CCE4)),
    BORDEAUX("bordeaux", Requirement.RightCalls(10), Color(0xFF6B1A2A), Color(0xFF45101B), Color(0xFF8C2A3D), Color(0xFFE3BCC4)),
    SLATE("slate", Requirement.GameWins(GameKind.ZWEEDS_PESTEN, 3), Color(0xFF37474F), Color(0xFF232E33), Color(0xFF50646E), Color(0xFFC5D1D6)),
    NIGHT("night", Requirement.Played(50), Color(0xFF1E1B3A), Color(0xFF120F26), Color(0xFF2F2A5C), Color(0xFFC9C3EC)),
    OLED("oled", Requirement.Free, Color.Black, Color.Black, Color(0xFF1C1C1E), Color(0xFFA0A4A8), TableLook.OLED),
    RAINBOW_ROAD("rainbow_road", Requirement.Ninja(5), Color(0xFF150A3A), Color(0xFF0A0520), Color(0xFF2B1D66), Color(0xFFD0C8F5), TableLook.RAINBOW),
    ;

    companion object {
        fun fromId(id: String?): TableSkin? = entries.firstOrNull { it.id == id }
    }
}

/** The skins in use. Compose state, so every screen recolours as soon as a skin is chosen. */
object Skins {
    var cardBack: CardBackSkin by mutableStateOf(CardBackSkin.CLASSIC)

    var table: TableSkin by mutableStateOf(TableSkin.GREEN)

    fun isUnlocked(skin: CardBackSkin, progress: Progress): Boolean = !skin.isPremium && skin.requirement.met(progress)

    fun isUnlocked(skin: TableSkin, progress: Progress): Boolean = skin.requirement.met(progress)

    /** The host's look at a table you joined (whatever the host unlocked). */
    fun applyHost(cardBackId: String, tableId: String) {
        cardBack = CardBackSkin.fromId(cardBackId)?.takeIf { !it.isPremium } ?: CardBackSkin.CLASSIC
        table = TableSkin.fromId(tableId) ?: TableSkin.GREEN
    }

    /** Applies the chosen skins, falling back to the defaults for anything not (or no longer) unlocked. */
    fun apply(cardBackId: String, tableId: String, progress: Progress) {
        cardBack = CardBackSkin.fromId(cardBackId)?.takeIf { isUnlocked(it, progress) } ?: CardBackSkin.CLASSIC
        table = TableSkin.fromId(tableId)?.takeIf { isUnlocked(it, progress) } ?: TableSkin.GREEN
    }
}
