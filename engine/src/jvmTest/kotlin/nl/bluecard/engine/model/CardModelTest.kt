package nl.bluecard.engine.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CardModelTest {

    @Test
    fun `standard deck has 52 unique cards`() {
        val deck = Deck.standard52()
        assertEquals(52, deck.size)
        assertEquals(52, deck.cards.toSet().size)
    }

    @Test
    fun `card codes round trip`() {
        for (card in Deck.standard52().cards) {
            assertEquals(card, Card.parse(card.code))
        }
        assertEquals(Card(Rank.TEN, Suit.HEARTS), Card.of("10H"))
        assertEquals(Card(Rank.QUEEN, Suit.SPADES), Card.of("qs"))
    }

    @Test
    fun `invalid card codes are rejected`() {
        assertNull(Card.parse(""))
        assertNull(Card.parse("1H"))
        assertNull(Card.parse("10X"))
        assertNull(Card.parse("Z"))
    }

    @Test
    fun `cards serialize as compact strings`() {
        val cards = listOf(Card.of("2C"), Card.of("10D"), Card.of("AS"))
        val json = Json.encodeToString(ListSerializer(Card.serializer()), cards)
        assertEquals("[\"2C\",\"10D\",\"AS\"]", json)
        assertEquals(cards, Json.decodeFromString(ListSerializer(Card.serializer()), json))
    }

    @Test
    fun `deck draws from the top and shuffling keeps all cards`() {
        val deck = Deck(listOf(Card.of("2C"), Card.of("3C"), Card.of("4C")))
        val (drawn, rest) = deck.draw(2)
        assertEquals(listOf(Card.of("4C"), Card.of("3C")), drawn)
        assertEquals(listOf(Card.of("2C")), rest.cards)
        val (none, same) = rest.draw(5).let { it.first to it.second }
        assertEquals(1, none.size)
        assertTrue(same.isEmpty)

        val shuffled = Deck.standard52().shuffled(Random(1))
        assertEquals(Deck.standard52().cards.toSet(), shuffled.cards.toSet())
    }

    @Test
    fun `discard pile run length counts equal ranks on top`() {
        val pile = DiscardPile(listOf(Card.of("5C"), Card.of("8C"), Card.of("8H"), Card.of("8S")))
        assertEquals(3, pile.topRunLength())
        assertEquals(Card.of("8S"), pile.top)
        assertEquals(0, DiscardPile().topRunLength())
    }

    @Test
    fun `cards order by rank then suit`() {
        val sorted = listOf(Card.of("AS"), Card.of("3D"), Card.of("3C"), Card.of("10H")).sorted()
        assertEquals(listOf(Card.of("3C"), Card.of("3D"), Card.of("10H"), Card.of("AS")), sorted)
    }
}
