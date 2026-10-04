package nl.bluecard.app.ui

import nl.bluecard.app.session.GameKind
import nl.bluecard.app.settings.AppSettings
import nl.bluecard.app.stats.Progress
import nl.bluecard.app.stats.Requirement
import nl.bluecard.app.ui.components.EmojiUnlocks
import nl.bluecard.app.ui.theme.CardBackSkin
import nl.bluecard.app.ui.theme.Skins
import nl.bluecard.app.ui.theme.TableSkin
import nl.bluecard.multiplayer.session.EmojiCatalog
import nl.bluecard.multiplayer.session.MatchPlayer
import nl.bluecard.multiplayer.session.MatchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiUnlocksTest {
    private fun wins(n: Int) = Progress(wins = n, played = n)

    @Test
    fun `sets open with their own requirement and the top one opens everything`() {
        assertEquals(AppSettings.DEFAULT_REACTIONS.toSet(), EmojiUnlocks.unlocked(Progress()))
        assertTrue("🔥" in EmojiUnlocks.unlocked(wins(3)))
        assertFalse("🔥" in EmojiUnlocks.unlocked(wins(2)))
        // Fish only for Kibbeling winners, the ninja set only for ninjas.
        assertFalse("🐟" in EmojiUnlocks.unlocked(wins(20)))
        assertTrue("🐟" in EmojiUnlocks.unlocked(Progress(winsByGame = mapOf(GameKind.ZWEEDS_PESTEN.id to 5))))
        assertTrue("🥷" in EmojiUnlocks.unlocked(Progress(unnoticed = 3)))
        assertEquals(EmojiCatalog.ALL, EmojiUnlocks.unlocked(wins(50)))
        // Every set emoji can be sent.
        EmojiUnlocks.TIERS.flatMap { it.emojis.orEmpty() }.forEach { assertTrue(it, EmojiCatalog.isAllowed(it)) }
    }

    @Test
    fun `the bar keeps only unlocked emoji, at most six, never empty`() {
        assertEquals(listOf("❤️", "🔥"), EmojiUnlocks.bar(listOf("❤️", "🔥", "🦄"), wins(3)))
        assertEquals(AppSettings.DEFAULT_REACTIONS, EmojiUnlocks.bar(listOf("🦄"), Progress()))
        assertEquals(6, EmojiUnlocks.bar(EmojiCatalog.ALL.toList(), wins(60)).size)
    }

    @Test
    fun `progress is counted from your own games`() {
        fun game(id: String, gameId: String, position: Int, right: Int = 0, ninja: Int = 0) = MatchRecord(
            id, gameId, 0, listOf(MatchPlayer("me", "Ik", position, human = true, rightCalls = right, unnoticed = ninja), MatchPlayer(null, "Bot", 3 - position, human = false)),
        )
        val p = Progress.of(
            listOf(game("1", "zweeds-pesten", 1, right = 2), game("2", "zweeds-pesten", 2, ninja = 1), game("3", "pesten", 1)),
            "me",
        )
        assertEquals(2, p.wins)
        assertEquals(3, p.played)
        assertEquals(1, Requirement.GameWins(GameKind.ZWEEDS_PESTEN, 5).current(p))
        assertEquals(2, Requirement.GamePlayed(GameKind.ZWEEDS_PESTEN, 5).current(p))
        assertEquals(2, p.rightCalls)
        assertEquals(1, p.unnoticed)
        // Skins follow the same rules.
        assertTrue(Skins.isUnlocked(TableSkin.OLED, Progress()))
        assertFalse(Skins.isUnlocked(TableSkin.BORDEAUX, p))
        assertTrue(Skins.isUnlocked(CardBackSkin.CHERRY, Progress(wins = 3)))
        assertFalse(Skins.isUnlocked(CardBackSkin.DIAMOND_HOLO, wins(999)))
    }
}
