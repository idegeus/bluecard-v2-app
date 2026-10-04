package nl.bluecard.engine.zweedspesten

import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.PlayerInfo
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.zweedspesten.Fx.cards
import nl.bluecard.engine.zweedspesten.Fx.player
import nl.bluecard.engine.zweedspesten.Fx.state
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZpEngineTest {

    private val players3 = listOf(PlayerInfo("a", "Anna"), PlayerInfo("b", "Bram"), PlayerInfo("c", "Cor"))

    // ------------------------------------------------------------------ setup

    @Test
    fun `new game deals three blind, three open and three hand cards`() {
        val s = ZpEngine.newGame(players3, ZpHouseRules(), seed = 42)
        for (p in s.players) {
            assertEquals(3, p.faceDown.size)
            assertEquals(3, p.faceUp.size)
            assertEquals(3, p.hand.size)
        }
        assertEquals(52 - 27, s.drawPile.size)
        assertEquals(52, s.allCards().toSet().size)
        assertEquals(ZpPhase.SWAPPING, s.phase)
        assertNull(s.turn.currentPlayerId)
    }

    @Test
    fun `dealing is deterministic per seed`() {
        assertEquals(ZpEngine.newGame(players3, ZpHouseRules(), 7), ZpEngine.newGame(players3, ZpHouseRules(), 7))
    }

    @Test
    fun `hand size house rule deals more hand cards`() {
        val s = ZpEngine.newGame(players3, ZpHouseRules(handSize = 5), seed = 1)
        assertTrue(s.players.all { it.hand.size == 5 })
    }

    @Test
    fun `without swap phase the player with the lowest ordinary card starts`() {
        val s = ZpEngine.newGame(players3, ZpHouseRules(swapPhase = false), seed = 3)
        assertEquals(ZpPhase.PLAYING, s.phase)
        val rules = s.rules
        val lowest = s.players.mapNotNull { p -> ZpRules.startCandidate(p.hand, rules)?.let { p.id to it } }
            .minBy { it.second }
        assertEquals(lowest.first, s.turn.currentPlayerId)
        assertNotNull(s.events<ZpEvent.GameStarted>().single())
    }

    @Test
    fun `setup validation`() {
        assertEquals(ZpRejectReason.TOO_FEW_PLAYERS, ZpRules.validateSetup(1, ZpHouseRules()))
        assertEquals(ZpRejectReason.TOO_MANY_PLAYERS, ZpRules.validateSetup(6, ZpHouseRules()))
        assertEquals(ZpRejectReason.NOT_ENOUGH_CARDS, ZpRules.validateSetup(5, ZpHouseRules(handSize = 5)))
        assertEquals(ZpRejectReason.INVALID_HAND_SIZE, ZpRules.validateSetup(2, ZpHouseRules(handSize = 2)))
        assertNull(ZpRules.validateSetup(5, ZpHouseRules()))
        assertEquals(4, ZpRules.maxPlayersFor(ZpHouseRules(handSize = 5)))
    }

    // ------------------------------------------------------------------ swap phase

    @Test
    fun `swap exchanges a hand card with a face-up card`() {
        val s = state(
            player("a", hand = "3C 4C 5C", up = "AH KH QH", down = "2C 2D 2H", ready = false),
            player("b", hand = "6C 7C 8C", up = "JH 10C 9H", down = "3D 4D 5D", ready = false),
            phase = ZpPhase.SWAPPING,
        )
        val after = s.act("a", ZpAction.Swap(Card.of("3C"), Card.of("AH")))
        assertEquals(cards("4C 5C AH"), after.p("a").hand)
        assertEquals(cards("3C KH QH"), after.p("a").faceUp)
        assertEquals(s.version + 1, after.version)
    }

    @Test
    fun `swap with cards you do not own is rejected`() {
        val s = state(
            player("a", hand = "3C 4C 5C", up = "AH KH QH", ready = false),
            player("b", hand = "6C 7C 8C", up = "JH 10C 9H", ready = false),
            phase = ZpPhase.SWAPPING,
        )
        assertEquals("CARD_NOT_AVAILABLE", s.rejection("a", ZpAction.Swap(Card.of("6C"), Card.of("AH"))))
        assertEquals("CARD_NOT_AVAILABLE", s.rejection("a", ZpAction.Swap(Card.of("3C"), Card.of("JH"))))
    }

    @Test
    fun `game starts when everybody is ready and ready twice is rejected`() {
        val s = state(
            player("a", hand = "5C 6C 8C", up = "AH KH QH", ready = false),
            player("b", hand = "3S 4C JC", up = "JH 10C 9H", ready = false),
            phase = ZpPhase.SWAPPING,
        )
        val aReady = s.act("a", ZpAction.Ready)
        assertEquals(ZpPhase.SWAPPING, aReady.phase)
        assertEquals("ALREADY_READY", aReady.rejection("a", ZpAction.Ready))
        assertEquals("ALREADY_READY", aReady.rejection("a", ZpAction.Swap(Card.of("5C"), Card.of("AH"))))
        val started = aReady.act("b", ZpAction.Ready)
        assertEquals(ZpPhase.PLAYING, started.phase)
        assertEquals("b", started.turn.currentPlayerId) // b holds the 3
    }

    @Test
    fun `playing during swap phase is rejected`() {
        val s = state(
            player("a", hand = "5C", up = "AH", ready = false),
            player("b", hand = "3S", up = "JH", ready = false),
            phase = ZpPhase.SWAPPING,
        )
        assertEquals("WRONG_PHASE", s.rejection("a", play("5C")))
        assertEquals("WRONG_PHASE", s.rejection("a", ZpAction.PickUp))
    }

    @Test
    fun `swapping after the game started is rejected`() {
        val s = state(player("a", hand = "5C", up = "AH"), player("b", hand = "3S"))
        assertEquals("WRONG_PHASE", s.rejection("a", ZpAction.Swap(Card.of("5C"), Card.of("AH"))))
        assertEquals("WRONG_PHASE", s.rejection("a", ZpAction.Ready))
    }

    // ------------------------------------------------------------------ basic play

    @Test
    fun `playing an equal or higher card is accepted and the hand is refilled`() {
        val s = state(
            player("a", hand = "6C 8D KS", up = "4H", down = "5H"),
            player("b", hand = "3C 4C 5C"),
            discard = "6H",
            draw = "QD JD",
        )
        val after = s.act("a", play("6C"))
        assertEquals(cards("6H 6C"), after.discardPile.cards)
        assertEquals(cards("8D JD KS"), after.p("a").hand)
        assertEquals(cards("QD"), after.drawPile.cards)
        assertEquals("b", after.turn.currentPlayerId)
        val played = after.events<ZpEvent.CardsPlayed>().single()
        assertEquals(CardSource.HAND, played.source)
    }

    @Test
    fun `playing a lower card is rejected`() {
        val s = state(player("a", hand = "4C 8D"), player("b", hand = "3C"), discard = "6H")
        assertEquals("CARD_TOO_LOW", s.rejection("a", play("4C")))
    }

    @Test
    fun `playing a card you do not have is rejected`() {
        val s = state(player("a", hand = "4C 8D"), player("b", hand = "9C"), discard = "6H")
        assertEquals("CARD_NOT_AVAILABLE", s.rejection("a", play("9C")))
        assertEquals("EMPTY_SELECTION", s.rejection("a", ZpAction.Play(emptyList())))
    }

    @Test
    fun `action of the wrong player is rejected`() {
        val s = state(player("a", hand = "8D"), player("b", hand = "9C"), discard = "6H")
        assertEquals("NOT_YOUR_TURN", s.rejection("b", play("9C")))
        assertEquals("NOT_YOUR_TURN", s.rejection("b", ZpAction.PickUp))
        assertEquals("UNKNOWN_PLAYER", s.rejection("zz", play("9C")))
    }

    @Test
    fun `duplicate action is rejected the second time`() {
        val s = state(player("a", hand = "8D 9D"), player("b", hand = "9C JC"), discard = "6H")
        val once = s.act("a", play("8D"))
        assertEquals("NOT_YOUR_TURN", once.rejection("a", play("8D")))
        val bPlayed = once.act("b", play("9C"))
        // a's stale action replayed on a later state: the card is no longer in a's hand.
        assertEquals("CARD_NOT_AVAILABLE", bPlayed.rejection("a", play("8D")))
    }

    @Test
    fun `turns rotate through all players in seat order`() {
        var s = state(
            player("a", hand = "3C 4C 5C"),
            player("b", hand = "3D 4D 5D"),
            player("c", hand = "3H 4H 5H"),
            player("d", hand = "3S 4S 5S"),
        )
        val order = mutableListOf<String>()
        for (card in listOf("3C", "4D", "4H", "5S", "5C")) {
            order += s.turn.currentPlayerId!!
            s = s.act(s.turn.currentPlayerId!!, play(card))
        }
        assertEquals(listOf("a", "b", "c", "d", "a"), order)
    }

    @Test
    fun `multiple cards of the same rank can be played together`() {
        val s = state(player("a", hand = "8C 8D 8H KS"), player("b", hand = "3C"), discard = "6H")
        val after = s.act("a", play("8C 8D"))
        assertEquals(cards("8H KS"), after.p("a").hand)
        assertEquals(3, after.discardPile.size)
    }

    @Test
    fun `mixed ranks and duplicates are rejected`() {
        val s = state(player("a", hand = "8C 9D KS"), player("b", hand = "3C"), discard = "6H")
        assertEquals("MIXED_RANKS", s.rejection("a", play("8C 9D")))
        assertEquals("DUPLICATE_CARDS", s.rejection("a", ZpAction.Play(listOf(Card.of("8C"), Card.of("8C")))))
    }

    @Test
    fun `multiple cards rejected when house rule disables it`() {
        val s = state(
            player("a", hand = "8C 8D"),
            player("b", hand = "3C"),
            discard = "6H",
            rules = ZpHouseRules(allowMultiple = false),
        )
        assertEquals("MULTIPLE_NOT_ALLOWED", s.rejection("a", play("8C 8D")))
        s.act("a", play("8C"))
    }

    // ------------------------------------------------------------------ special cards

    @Test
    fun `two resets the pile and can be played on anything`() {
        val s = state(player("a", hand = "2C KD"), player("b", hand = "3C KH"), discard = "AH")
        val after = s.act("a", play("2C"))
        assertEquals(ZpRequirement(minValue = 2), ZpRules.requirement(after.discardPile, after.rules))
        after.act("b", play("3C"))
    }

    @Test
    fun `seven forces the next player to play seven or lower`() {
        val s = state(player("a", hand = "7C KD"), player("b", hand = "8C 5C 7D"), discard = "4H")
        val after = s.act("a", play("7C"))
        assertEquals("CARD_TOO_HIGH", after.rejection("b", play("8C")))
        after.act("b", play("5C"))
        after.act("b", play("7D"))
    }

    @Test
    fun `strict lower rule forbids playing an equal card on a seven`() {
        val s = state(
            player("a", hand = "7C KD"),
            player("b", hand = "7D 6D"),
            discard = "4H",
            rules = ZpHouseRules(lowerIncludesEqual = false),
        )
        val after = s.act("a", play("7C"))
        assertEquals("CARD_TOO_HIGH", after.rejection("b", play("7D")))
        after.act("b", play("6D"))
    }

    @Test
    fun `after a seven nothing higher may be played, not even a ten`() {
        // Regression: special cards used to skip the "7 or lower" check.
        val s = state(player("a", hand = "7C KD"), player("b", hand = "2D 5C 8C 9C 10C AC"), discard = "4H")
        val after = s.act("a", play("7C"))
        for (card in listOf("8C", "9C", "10C", "AC")) {
            assertEquals(card, "CARD_TOO_HIGH", after.rejection("b", play(card)))
        }
        assertEquals(Fx.cards("2D 5C"), ZpViews.create(after, "b").legal.playableCards)
        after.act("b", play("2D"))
        after.act("b", play("5C"))
    }

    @Test
    fun `a player holding only high cards after a seven must pick up`() {
        val s = state(player("a", hand = "7C KD"), player("b", hand = "8C 10C AC"), discard = "4H")
        val after = s.act("a", play("7C"))
        val legal = ZpViews.create(after, "b").legal
        assertTrue(legal.playableCards.isEmpty())
        assertTrue(legal.canPickUp)
        assertEquals(Fx.cards("4H 7C 8C 10C AC"), after.act("b", ZpAction.PickUp).p("b").hand)
    }

    @Test
    fun `a nine is an ordinary card`() {
        val onKing = state(player("a", hand = "9C 3D"), player("b", hand = "QC AC"), discard = "KH")
        assertEquals("CARD_TOO_LOW", onKing.rejection("a", play("9C")))
        val onSeven = state(player("a", hand = "9C 3D"), player("b", hand = "QC AC"), discard = "7H")
        assertEquals("CARD_TOO_HIGH", onSeven.rejection("a", play("9C")))
        val onEight = state(player("a", hand = "9C 3D"), player("b", hand = "8D AC"), discard = "8H")
        val after = onEight.act("a", play("9C"))
        assertEquals("CARD_TOO_LOW", after.rejection("b", play("8D")))
    }

    @Test
    fun `ten burns on anything except a seven`() {
        for (top in listOf("3H", "KH", "AH", "2H")) {
            val s = state(player("a", hand = "10C 4D"), player("b", hand = "5C"), discard = top)
            assertTrue(s.act("a", play("10C")).discardPile.isEmpty)
        }
    }

    @Test
    fun `ten burns the pile and the same player goes again`() {
        val s = state(player("a", hand = "10C 3D 4D"), player("b", hand = "5C"), discard = "KH AH", draw = "6S")
        val after = s.act("a", play("10C"))
        assertTrue(after.discardPile.isEmpty)
        assertEquals(cards("KH AH 10C"), after.burned)
        assertEquals("a", after.turn.currentPlayerId)
        assertEquals(BurnReason.BURN_CARD, after.events<ZpEvent.PileBurned>().single().reason)
        assertEquals(1, after.events<ZpEvent.ExtraTurn>().size)
        // After the burn anything may be played.
        after.act("a", play("3D"))
    }

    @Test
    fun `burn without extra turn passes the turn`() {
        val s = state(
            player("a", hand = "10C 3D"),
            player("b", hand = "5C"),
            discard = "KH",
            rules = ZpHouseRules(burnGivesExtraTurn = false),
        )
        assertEquals("b", s.act("a", play("10C")).turn.currentPlayerId)
    }

    @Test
    fun `four of a kind burns the pile`() {
        val s = state(player("a", hand = "8C 8D 3S"), player("b", hand = "5C"), discard = "4H 8H 8S")
        val after = s.act("a", play("8C 8D"))
        assertTrue(after.discardPile.isEmpty)
        assertEquals(BurnReason.FOUR_OF_A_KIND, after.events<ZpEvent.PileBurned>().single().reason)
        assertEquals("a", after.turn.currentPlayerId)
    }

    @Test
    fun `four of a kind does nothing when the rule is off`() {
        val s = state(
            player("a", hand = "8C 8D 3S"),
            player("b", hand = "5C"),
            discard = "4H 8H 8S",
            rules = ZpHouseRules(fourOfAKindBurns = false),
        )
        val after = s.act("a", play("8C 8D"))
        assertEquals(5, after.discardPile.size)
        assertEquals("b", after.turn.currentPlayerId)
    }

    @Test
    fun `skip card skips the next player, one per card`() {
        val rules = ZpHouseRules().withEffect(Rank.EIGHT, ZpEffect.SKIP)
        val base = state(
            player("a", hand = "8C 8D 3S"),
            player("b", hand = "5C"),
            player("c", hand = "5D"),
            player("d", hand = "5H"),
            discard = "4H",
            rules = rules,
        )
        val one = base.act("a", play("8C"))
        assertEquals("c", one.turn.currentPlayerId)
        assertEquals(listOf("b"), one.events<ZpEvent.PlayersSkipped>().single().skippedIds)

        val two = base.act("a", play("8C 8D"))
        assertEquals("d", two.turn.currentPlayerId)
        assertEquals(listOf("b", "c"), two.events<ZpEvent.PlayersSkipped>().single().skippedIds)
    }

    @Test
    fun `skip with two players gives the same player another turn`() {
        val rules = ZpHouseRules().withEffect(Rank.EIGHT, ZpEffect.SKIP)
        val s = state(player("a", hand = "8C 3S"), player("b", hand = "5C"), discard = "4H", rules = rules)
        assertEquals("a", s.act("a", play("8C")).turn.currentPlayerId)
    }

    @Test
    fun `reverse card changes the direction of play`() {
        val rules = ZpHouseRules().withEffect(Rank.JACK, ZpEffect.REVERSE)
        val s = state(
            player("a", hand = "3S"),
            player("b", hand = "JC 4D"),
            player("c", hand = "5D"),
            player("d", hand = "5H"),
            discard = "4H",
            rules = rules,
            current = "b",
        )
        val after = s.act("b", play("JC"))
        assertEquals(-1, after.turn.direction)
        assertEquals("a", after.turn.currentPlayerId)
        // Continue counter-clockwise: a -> d.
        val next = after.act("a", ZpAction.PickUp)
        assertEquals("d", next.turn.currentPlayerId)
    }

    @Test
    fun `two reverse cards cancel each other`() {
        val rules = ZpHouseRules().withEffect(Rank.JACK, ZpEffect.REVERSE)
        val s = state(
            player("a", hand = "JC JD 3C"),
            player("b", hand = "5C"),
            player("c", hand = "5D"),
            discard = "4H",
            rules = rules,
        )
        val after = s.act("a", play("JC JD"))
        assertEquals(1, after.turn.direction)
        assertEquals("b", after.turn.currentPlayerId)
    }

    // ------------------------------------------------------------------ penalty: picking up

    @Test
    fun `player who cannot play picks up the whole pile`() {
        val s = state(player("a", hand = "3C 4C"), player("b", hand = "5C"), discard = "5H KH")
        val after = s.act("a", ZpAction.PickUp)
        assertEquals(cards("3C 4C 5H KH"), after.p("a").hand)
        assertTrue(after.discardPile.isEmpty)
        assertEquals("b", after.turn.currentPlayerId)
        assertEquals(2, after.events<ZpEvent.PileTaken>().single().count)
    }

    @Test
    fun `picking up is always allowed, also when a card would fit`() {
        val s = state(player("a", hand = "3C AC"), player("b", hand = "5C"), discard = "KH")
        assertTrue(ZpViews.create(s, "a").legal.canPickUp)
        val after = s.act("a", ZpAction.PickUp)
        assertEquals(Fx.cards("3C KH AC"), after.p("a").hand)
        assertEquals("b", after.turn.currentPlayerId)
    }

    @Test
    fun `picking up is also allowed from the face-up and blind phase`() {
        val faceUp = state(player("a", up = "AC 3D"), player("b", hand = "5C"), discard = "KH")
        assertEquals(Fx.cards("KH"), faceUp.act("a", ZpAction.PickUp).p("a").hand)
        val blind = state(player("a", down = "3C"), player("b", hand = "5C"), discard = "KH")
        assertTrue(ZpViews.create(blind, "a").legal.canPickUp)
        assertEquals(Fx.cards("KH"), blind.act("a", ZpAction.PickUp).p("a").hand)
    }

    @Test
    fun `picking up an empty pile is rejected`() {
        val s = state(player("a", hand = "3C"), player("b", hand = "5C"))
        assertEquals("PILE_EMPTY", s.rejection("a", ZpAction.PickUp))
    }

    // ------------------------------------------------------------------ face-up / blind cards

    @Test
    fun `face-up cards are played once the hand is empty and the draw pile is gone`() {
        val s = state(player("a", hand = "", up = "8C QD", down = "3C"), player("b", hand = "5C"), discard = "6H")
        val after = s.act("a", play("QD"))
        assertEquals(cards("8C"), after.p("a").faceUp)
        assertEquals(CardSource.FACE_UP, after.events<ZpEvent.CardsPlayed>().single().source)
    }

    @Test
    fun `face-up cards cannot be played while you still have hand cards`() {
        val s = state(player("a", hand = "8D", up = "QD"), player("b", hand = "5C"), discard = "6H")
        assertEquals("WRONG_SOURCE", s.rejection("a", play("QD")))
    }

    @Test
    fun `blind card that fits is played`() {
        val s = state(player("a", down = "3C KD"), player("b", hand = "5C"), discard = "6H")
        assertEquals("MUST_PLAY_FACE_DOWN", s.rejection("a", play("KD")))
        val after = s.act("a", ZpAction.PlayBlind(1))
        assertEquals(cards("3C"), after.p("a").faceDown)
        assertEquals(Card.of("KD"), after.discardPile.top)
        assertTrue(after.events<ZpEvent.BlindRevealed>().single().success)
    }

    @Test
    fun `blind card that does not fit means picking up the pile plus that card`() {
        val s = state(player("a", down = "3C KD"), player("b", hand = "5C"), discard = "6H")
        val after = s.act("a", ZpAction.PlayBlind(0))
        assertEquals(cards("3C 6H"), after.p("a").hand)
        assertEquals(cards("KD"), after.p("a").faceDown)
        assertTrue(after.discardPile.isEmpty)
        assertFalse(after.events<ZpEvent.BlindRevealed>().single().success)
        assertEquals("b", after.turn.currentPlayerId)
    }

    @Test
    fun `blind play is validated`() {
        val withHand = state(player("a", hand = "5D", down = "3C"), player("b", hand = "5C"))
        assertEquals("WRONG_SOURCE", withHand.rejection("a", ZpAction.PlayBlind(0)))
        val blind = state(player("a", down = "3C"), player("b", hand = "5C"))
        assertEquals("INVALID_BLIND_INDEX", blind.rejection("a", ZpAction.PlayBlind(3)))
        assertEquals("INVALID_BLIND_INDEX", blind.rejection("a", ZpAction.PlayBlind(-1)))
    }

    // ------------------------------------------------------------------ draw pile

    @Test
    fun `empty draw pile means no refill`() {
        val s = state(player("a", hand = "8C 9D KS"), player("b", hand = "5C"), discard = "6H")
        val after = s.act("a", play("8C"))
        assertEquals(cards("9D KS"), after.p("a").hand)
        assertTrue(after.drawPile.isEmpty)
    }

    @Test
    fun `burned cards are reshuffled into a new draw pile when the rule is on`() {
        val rules = ZpHouseRules(reshuffleBurned = true)
        val s = state(
            player("a", hand = "8C 9D KS"),
            player("b", hand = "5C"),
            discard = "6H",
            burned = "2C 3C 4C 5D",
            rules = rules,
        )
        val after = s.act("a", play("8C"))
        assertEquals(3, after.p("a").hand.size)
        assertEquals(3, after.drawPile.size)
        assertTrue(after.burned.isEmpty())
        assertEquals(4, after.events<ZpEvent.DrawPileReshuffled>().single().count)
        assertEquals(s.allCards().toSet(), after.allCards().toSet())
    }

    @Test
    fun `burned cards stay burned without the reshuffle rule`() {
        val s = state(player("a", hand = "8C 9D KS"), player("b", hand = "5C"), discard = "6H", burned = "2C 3C")
        val after = s.act("a", play("8C"))
        assertEquals(2, after.burned.size)
        assertEquals(2, after.p("a").hand.size)
    }

    @Test
    fun `gamble plays the drawn card when it fits`() {
        val rules = ZpHouseRules(drawGamble = true)
        val s = state(player("a", hand = "3C 4C 5C"), player("b", hand = "5D"), discard = "8H", draw = "4S KD", rules = rules)
        val after = s.act("a", ZpAction.Gamble)
        assertEquals(Card.of("KD"), after.discardPile.top)
        assertEquals(cards("3C 4C 5C"), after.p("a").hand)
        assertTrue(after.events<ZpEvent.GambleRevealed>().single().success)
        assertEquals("b", after.turn.currentPlayerId)
    }

    @Test
    fun `failed gamble means picking up the pile plus the drawn card`() {
        val rules = ZpHouseRules(drawGamble = true)
        val s = state(player("a", hand = "3C 4C 5C"), player("b", hand = "5D"), discard = "8H", draw = "KD 4S", rules = rules)
        val after = s.act("a", ZpAction.Gamble)
        assertEquals(cards("3C 4C 4S 5C 8H"), after.p("a").hand)
        assertTrue(after.discardPile.isEmpty)
        assertFalse(after.events<ZpEvent.GambleRevealed>().single().success)
    }

    @Test
    fun `gamble is validated`() {
        val off = state(player("a", hand = "3C"), player("b", hand = "5D"), discard = "8H", draw = "KD")
        assertEquals("GAMBLE_NOT_ALLOWED", off.rejection("a", ZpAction.Gamble))
        val empty = off.copy(rules = ZpHouseRules(drawGamble = true), drawPile = off.drawPile.copy(cards = emptyList()))
        assertEquals("DRAW_PILE_EMPTY", empty.rejection("a", ZpAction.Gamble))
    }

    // ------------------------------------------------------------------ winning

    @Test
    fun `playing your last card finishes you and the game goes on`() {
        val s = state(
            player("a", down = "KD"),
            player("b", hand = "5C 6C"),
            player("c", hand = "5D 6D"),
            discard = "6H",
        )
        val after = s.act("a", ZpAction.PlayBlind(0))
        assertEquals(1, after.p("a").finishedPosition)
        assertEquals(ZpPhase.PLAYING, after.phase)
        assertEquals("b", after.turn.currentPlayerId)
        assertEquals(1, after.events<ZpEvent.PlayerFinished>().single().position)
        // Finished players are skipped from now on.
        val b = after.act("b", ZpAction.PickUp)
        val c = b.act("c", play("5D"))
        assertEquals("b", c.turn.currentPlayerId)
    }

    @Test
    fun `game ends when only one player has cards left`() {
        val s = state(
            player("a", up = "QD"),
            player("b", hand = "5C 6C"),
            player("c", finished = 1),
            discard = "6H",
        )
        val after = s.act("a", play("QD"))
        assertEquals(ZpPhase.FINISHED, after.phase)
        assertNull(after.turn.currentPlayerId)
        val result = ZpEngine.result(after)!!
        assertEquals(listOf("c", "a", "b"), result.ranking.map { it.playerId })
        assertEquals("c", result.winner?.playerId)
        assertEquals("b", result.loser?.playerId)
        assertEquals(2, result.ranking.last().cardsLeft)
        val over = after.events<ZpEvent.GameOver>().single()
        assertEquals("c", over.winnerId)
        assertEquals("b", over.loserId)
    }

    @Test
    fun `stop at first winner ranks the rest by cards left`() {
        val s = state(
            player("a", up = "QD"),
            player("b", hand = "5C 6C 7C"),
            player("c", hand = "5D"),
            discard = "6H",
            rules = ZpHouseRules(playUntilLast = false),
        )
        val after = s.act("a", play("QD"))
        assertEquals(ZpPhase.FINISHED, after.phase)
        assertEquals(listOf("a", "c", "b"), ZpEngine.result(after)!!.ranking.map { it.playerId })
    }

    @Test
    fun `finishing with a burn card passes the turn instead of an extra turn`() {
        val s = state(
            player("a", up = "10C"),
            player("b", hand = "5C"),
            player("c", hand = "5D"),
            discard = "6H",
        )
        val after = s.act("a", play("10C"))
        assertEquals(1, after.p("a").finishedPosition)
        assertEquals("b", after.turn.currentPlayerId)
        assertTrue(after.events<ZpEvent.ExtraTurn>().isEmpty())
    }

    @Test
    fun `finishing with a skip card skips over finished players correctly`() {
        val rules = ZpHouseRules().withEffect(Rank.EIGHT, ZpEffect.SKIP)
        val s = state(
            player("a", up = "8C"),
            player("b", hand = "5C"),
            player("c", hand = "5D"),
            discard = "6H",
            rules = rules,
        )
        val after = s.act("a", play("8C"))
        assertEquals("c", after.turn.currentPlayerId)
    }

    @Test
    fun `actions after the game finished are rejected`() {
        val s = state(player("a", up = "QD"), player("b", hand = "5C"), discard = "6H")
        val after = s.act("a", play("QD"))
        assertEquals(ZpPhase.FINISHED, after.phase)
        assertEquals("GAME_FINISHED", after.rejection("b", play("5C")))
    }

    @Test
    fun `version increases only on accepted actions`() {
        val s = state(player("a", hand = "8D 3C"), player("b", hand = "9C"), discard = "6H")
        assertEquals(0, s.version)
        s.rejection("a", play("3C"))
        assertEquals(1, s.act("a", play("8D")).version)
    }
}
