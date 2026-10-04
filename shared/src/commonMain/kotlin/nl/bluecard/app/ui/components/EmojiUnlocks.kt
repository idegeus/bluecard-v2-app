package nl.bluecard.app.ui.components

import nl.bluecard.app.session.GameKind
import nl.bluecard.app.settings.AppSettings
import nl.bluecard.app.stats.Progress
import nl.bluecard.app.stats.Requirement
import nl.bluecard.multiplayer.session.EmojiCatalog

/**
 * Reaction emoji are unlocked in sets, each by its own [Requirement] (win, play a game often, be a ninja …). The top
 * set unlocks every emoji in [EmojiCatalog]: free choice.
 */
object EmojiUnlocks {
    /** [emojis] null = free choice of all. */
    data class Tier(val requirement: Requirement, val emojis: List<String>?)

    val TIERS = listOf(
        Tier(Requirement.Free, AppSettings.DEFAULT_REACTIONS),
        Tier(Requirement.Wins(3), listOf("😮", "👏", "🔥", "😡")),
        Tier(Requirement.Played(10), listOf("🤣", "😎", "🙈", "💀")),
        Tier(Requirement.GameWins(GameKind.ZWEEDS_PESTEN, 5), listOf("🐟", "🐡", "🦈")),
        Tier(Requirement.GameWins(GameKind.PRESIDENTEN, 3), listOf("👑", "💎", "🏆")),
        Tier(Requirement.Ninja(3), listOf("🥷", "🤫", "😈")),
        Tier(Requirement.GamePlayed(GameKind.PESTEN, 10), listOf("🃏", "🤡", "🍀")),
        Tier(Requirement.RightCalls(5), listOf("👀", "🧐", "🚨")),
        Tier(Requirement.Wins(50), null),
    )

    /** Most emoji in the reaction bar. */
    const val BAR_SIZE = 6

    fun unlocked(progress: Progress): Set<String> {
        val open = TIERS.filter { it.requirement.met(progress) }
        return if (open.any { it.emojis == null }) EmojiCatalog.ALL else open.flatMap { it.emojis.orEmpty() }.toSet()
    }

    /** Your reaction bar: your choice, limited to what you have unlocked (never empty). */
    fun bar(chosen: List<String>, progress: Progress): List<String> {
        val open = unlocked(progress)
        return chosen.filter { it in open }.distinct().take(BAR_SIZE).ifEmpty { AppSettings.DEFAULT_REACTIONS }
    }
}
