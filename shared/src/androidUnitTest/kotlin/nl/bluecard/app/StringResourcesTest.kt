package nl.bluecard.app

import nl.bluecard.app.settings.BotSpeed
import nl.bluecard.engine.model.Suit
import nl.bluecard.engine.pesten.PsEffect
import nl.bluecard.engine.pesten.PsPreset
import nl.bluecard.engine.hartenjagen.HjRejectReason
import nl.bluecard.engine.pesten.PsRejectReason
import nl.bluecard.engine.presidenten.PrRejectReason
import nl.bluecard.engine.zweedspesten.StartRule
import nl.bluecard.engine.zweedspesten.ZpEffect
import nl.bluecard.engine.zweedspesten.ZpPreset
import nl.bluecard.engine.zweedspesten.ZpRejectReason
import nl.bluecard.multiplayer.protocol.JoinRejectReason
import nl.bluecard.multiplayer.session.ClientSession
import nl.bluecard.multiplayer.session.GameHost
import nl.bluecard.multiplayer.session.HostSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the contract between engine/protocol codes and the UI texts: every code the engine or the sessions can
 * produce must have a matching string resource, every format string must be well-formed, and every translation
 * must cover the same names with the same placeholders as the default (English) texts.
 */
class StringResourcesTest {

    private val names: Set<String> by lazy {
        val file = File("src/commonMain/strings/values/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = doc.getElementsByTagName("string")
        val plurals = doc.getElementsByTagName("plurals")
        (0 until strings.length).map { strings.item(it).attributes.getNamedItem("name").nodeValue }.toSet() +
            (0 until plurals.length).map { plurals.item(it).attributes.getNamedItem("name").nodeValue }
    }

    private fun assertHas(name: String) = assertTrue("missing string resource '$name'", name in names)

    @Test
    fun `every engine reject reason has a message`() {
        ZpRejectReason.entries.forEach { assertHas("err_${it.name}") }
        PsRejectReason.entries.forEach { assertHas("err_${it.name}") }
        PrRejectReason.entries.forEach { assertHas("err_${it.name}") }
        HjRejectReason.entries.forEach { assertHas("err_${it.name}") }
    }

    @Test
    fun `every session level reason has a message`() {
        listOf(
            HostSession.REASON_DUPLICATE,
            HostSession.REASON_NOT_RUNNING,
            HostSession.REASON_MALFORMED,
            GameHost.NO_GAME,
            ClientSession.NOT_CONNECTED,
            ClientSession.TIMEOUT,
            ClientSession.CONNECTION_LOST,
        ).forEach { assertHas("err_$it") }
        JoinRejectReason.entries.forEach { assertHas("reject_${it.name}") }
    }

    @Test
    fun `every option shown in the settings has a label`() {
        ZpEffect.entries.forEach {
            assertHas("effect_${it.name}")
            assertHas("effect_short_${it.name}")
        }
        ZpPreset.entries.forEach { assertHas("preset_${it.name}") }
        PsEffect.entries.forEach {
            assertHas("ps_effect_${it.name}")
            assertHas("ps_effect_short_${it.name}")
        }
        PsPreset.entries.forEach { assertHas("ps_preset_${it.name}") }
        Suit.entries.forEach { assertHas("suit_${it.name}") }
        StartRule.entries.forEach { assertHas("start_${it.name}") }
        BotSpeed.entries.forEach { assertHas("speed_${it.name}") }
    }

    @Test
    fun `format strings use positional arguments`() {
        (listOf("values") + LOCALES.map { "values-$it" }).forEach { dir ->
            val text = File("src/commonMain/strings/$dir/strings.xml").readText()
            val unpositioned = Regex("%[sd]").findAll(text).map { it.value }.toList()
            assertTrue("$dir: use %1\$s style placeholders: $unpositioned", unpositioned.isEmpty())
        }
    }

    @Test
    fun `every translation is complete and keeps the placeholders`() {
        val base = placeholders("values")
        LOCALES.forEach { locale ->
            val translated = placeholders("values-$locale")
            assertEquals("names in values-$locale", base.keys, translated.keys)
            base.forEach { (name, expected) ->
                assertEquals("placeholders of '$name' in values-$locale", expected, translated[name])
            }
        }
    }

    /** Name → the positional placeholders used (for plurals: those of the "other" form). */
    private fun placeholders(dir: String): Map<String, Set<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/commonMain/strings/$dir/strings.xml"))
        val result = mutableMapOf<String, Set<String>>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val node = strings.item(i)
            result[node.attributes.getNamedItem("name").nodeValue] = PLACEHOLDER.findAll(node.textContent).map { it.value }.toSet()
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val node = plurals.item(i)
            val items = node.childNodes
            val other = (0 until items.length).map { items.item(it) }
                .first { it.nodeName == "item" && it.attributes.getNamedItem("quantity").nodeValue == "other" }
            result[node.attributes.getNamedItem("name").nodeValue] = PLACEHOLDER.findAll(other.textContent).map { it.value }.toSet()
        }
        return result
    }

    private companion object {
        val LOCALES = listOf("nl", "fr", "de", "ca", "eu")
        val PLACEHOLDER = Regex("%\\d\\$[sd]")
    }
}
