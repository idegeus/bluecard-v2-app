package nl.bluecard.app.res

import nl.bluecard.app.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ResourcesTest {

    @After
    fun reset() = Resources.useLanguage("en")

    @Test
    fun `positional and plain placeholders are filled in`() {
        assertEquals("b a 3 %", Resources.format("%2\$s %1\$s %3\$d %%", arrayOf("a", "b", 3)))
        assertEquals("x 7", Resources.format("%s %d", arrayOf("x", 7)))
        assertEquals("100%", Resources.format("%1\$d%%", arrayOf(100)))
    }

    @Test
    fun `texts follow the language and fall back to English`() {
        Resources.useLanguage("nl")
        assertEquals("Instellingen", Resources.getString(R.string.menu_settings))
        Resources.useLanguage("de-DE")
        assertEquals("Einstellungen", Resources.getString(R.string.menu_settings))
        Resources.useLanguage("it")
        assertEquals("Settings", Resources.getString(R.string.menu_settings))
    }

    @Test
    fun `plural rules per language`() {
        assertEquals("one", PluralRules.category("en", 1))
        assertEquals("other", PluralRules.category("en", 0))
        assertEquals("one", PluralRules.category("fr", 0))
        assertEquals("many", PluralRules.category("ca", 1_000_000))
        assertEquals("one", PluralRules.category("ca", 1))
        assertEquals("other", PluralRules.category("eu", 2))
        Resources.useLanguage("ca")
        assertEquals("5 cartes", Resources.getQuantityString(R.plurals.game_pile_count, 5, 5))
        Resources.useLanguage("eu")
        assertEquals("Zure txanda!", Resources.getString(R.string.game_your_turn))
        Resources.useLanguage("nl")
        assertEquals("1 kaart", Resources.getQuantityString(R.plurals.game_pile_count, 1, 1))
        assertEquals("5 kaarten", Resources.getQuantityString(R.plurals.game_pile_count, 5, 5))
    }

    @Test
    fun `dutch verbs agree with jij`() {
        Resources.useLanguage("nl")
        assertEquals("Jij bent klaar", Resources.getString(R.string.ev_ready, "Jij"))
        assertEquals("Jij bent uit! Plaats 1", Resources.getString(R.string.ev_player_out, "Jij", 1))
        assertEquals("Bot Bas is klaar", Resources.getString(R.string.ev_ready, "Bot Bas"))
    }

    @Test
    fun `android escapes are resolved`() {
        Resources.useLanguage("en")
        // values/strings.xml has "Don\'t see the table …"
        assertEquals(true, Resources.getString(R.string.join_other_devices_hint).startsWith("Don't"))
    }
}
