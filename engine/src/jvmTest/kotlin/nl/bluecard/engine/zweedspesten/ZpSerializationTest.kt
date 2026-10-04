package nl.bluecard.engine.zweedspesten

import kotlinx.serialization.json.Json
import nl.bluecard.engine.model.PlayerInfo
import nl.bluecard.engine.model.Rank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZpSerializationTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `game state round trips through json`() {
        var s = ZpEngine.newGame(listOf(PlayerInfo("a", "A"), PlayerInfo("b", "B")), ZpPreset.PESTKOP.rules, 5)
        s = s.act("a", ZpAction.Ready).act("b", ZpAction.Ready)
        val text = json.encodeToString(ZpGameState.serializer(), s)
        assertEquals(s, json.decodeFromString(ZpGameState.serializer(), text))
    }

    @Test
    fun `every action round trips with a readable type tag`() {
        val actions = listOf(
            ZpAction.Swap(Fx.cards("3C").single(), Fx.cards("AH").single()),
            ZpAction.Ready,
            play("8C 8D"),
            ZpAction.PlayBlind(2),
            ZpAction.PickUp,
            ZpAction.Gamble,
        )
        for (action in actions) {
            val text = json.encodeToString(ZpAction.serializer(), action)
            assertTrue(text, text.contains("\"type\""))
            assertEquals(action, json.decodeFromString(ZpAction.serializer(), text))
        }
        assertEquals("{\"type\":\"PLAY_CARD\",\"cards\":[\"8C\",\"8D\"]}", json.encodeToString(ZpAction.serializer(), play("8C 8D")))
    }

    @Test
    fun `house rules round trip including effect map`() {
        val rules = ZpHouseRules().withEffect(Rank.ACE, ZpEffect.REVERSE).copy(handSize = 4, drawGamble = true)
        val text = json.encodeToString(ZpHouseRules.serializer(), rules)
        assertEquals(rules, json.decodeFromString(ZpHouseRules.serializer(), text))
    }

    @Test
    fun `unknown fields from a newer version are ignored`() {
        val text = "{\"type\":\"PLAY_BLIND\",\"index\":1,\"futureField\":true}"
        assertEquals(ZpAction.PlayBlind(1), json.decodeFromString(ZpAction.serializer(), text))
    }
}
