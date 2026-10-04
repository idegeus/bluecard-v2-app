package nl.bluecard.app.settings

import nl.bluecard.app.session.GameKind
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.hartenjagen.HjHouseRules
import nl.bluecard.engine.pesten.PsHouseRules
import nl.bluecard.engine.presidenten.PrHouseRules
import nl.bluecard.engine.zweedspesten.ZpHouseRules

/** Pause before a bot moves; right after cards were laid it is doubled (see the game modules' botDelayMs). */
enum class BotSpeed(val delayMs: Long) {
    SLOW(2_000),
    NORMAL(1_250),
    FAST(700),
}

/** All user preferences. Stored locally with DataStore; nothing leaves the device. */
data class AppSettings(
    val playerName: String = "",
    /** Random id that lets a player reclaim their seat after a Bluetooth reconnect. */
    val playerToken: String = "",
    val botSpeed: BotSpeed = BotSpeed.NORMAL,
    val defaultDifficulty: BotDifficulty = BotDifficulty.NORMAL,
    val botCount: Int = 2,
    val showHints: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Sound effects at the table (cheat caught etc.). */
    val soundEffects: Boolean = true,
    /** Before every round one player shuffles by shaking their phone (the host's choice counts for the table). */
    val shuffleRitual: Boolean = true,
    /** A notification when somebody nearby opens a table (BLE background scan on Android). */
    val nearbyAlerts: Boolean = true,
    /** House rules for Zweeds Pesten. */
    val houseRules: ZpHouseRules = ZpHouseRules(),
    /** House rules for (normal) Pesten. */
    val pestenRules: PsHouseRules = PsHouseRules(),
    /** House rules for Presidenten. */
    val presidentRules: PrHouseRules = PrHouseRules(),
    /** House rules for Hartenjagen. */
    val heartsRules: HjHouseRules = HjHouseRules(),
    /** The game chosen last for a new game (against bots or over Bluetooth). */
    val game: GameKind = GameKind.ZWEEDS_PESTEN,
    /** Public random id of this phone, shared with other phones for the leaderboard (not secret, unlike the token). */
    val deviceId: String = "",
    /** Chosen skins (ids of [nl.bluecard.app.ui.theme.CardBackSkin] / [nl.bluecard.app.ui.theme.TableSkin]). */
    val cardBackSkin: String = "",
    val tableSkin: String = "",
    /** Your avatar (see [nl.bluecard.app.ui.components.AvatarSpec]); empty = your initial. */
    val avatar: String = "",
    /** The emoji in your reaction bar at the table (from the ones you unlocked, see EmojiUnlocks). */
    val reactionEmojis: List<String> = DEFAULT_REACTIONS,
) {
    val displayName: String get() = playerName.ifBlank { DEFAULT_NAME }

    companion object {
        const val DEFAULT_NAME = "Speler"
        val DEFAULT_REACTIONS = listOf("❤️", "👍", "😢", "😂")
        const val MAX_NAME_LENGTH = 20
    }
}
