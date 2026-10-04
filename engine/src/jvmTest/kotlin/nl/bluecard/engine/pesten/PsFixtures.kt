package nl.bluecard.engine.pesten

import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Deck
import nl.bluecard.engine.model.DiscardPile
import nl.bluecard.engine.model.Suit
import org.junit.Assert.fail

/** Small DSL to build hand-crafted Pesten states. Card lists are written as "3H 4C XS" (X = joker). */
object PsFx {
    fun cards(spec: String): List<Card> = spec.split(" ").filter { it.isNotBlank() }.map(Card::of)

    fun player(id: String, hand: String = "", announced: Boolean = false, finished: Int? = null) =
        PsPlayerState(id = id, name = id, hand = cards(hand), announced = announced, finishedPosition = finished)

    /** [draw] and [discard] are listed bottom-to-top: the LAST card is on top. */
    fun state(
        vararg players: PsPlayerState,
        discard: String = "5H",
        draw: String = "3C 4C 5C 6C 9C 10C",
        rules: PsHouseRules = PsHouseRules(),
        current: String = players.first().id,
        direction: Int = 1,
        pendingDraw: Int = 0,
        wishedSuit: Suit? = null,
        /** Rule tests run with enforcement on; cheating mode has its own tests. */
        enforce: Boolean = true,
    ) = PsGameState(
        rules = rules.copy(enforceRules = enforce),
        players = players.toList(),
        drawPile = Deck(cards(draw)),
        discardPile = DiscardPile(cards(discard)),
        currentPlayerId = current,
        direction = direction,
        pendingDraw = pendingDraw,
        wishedSuit = wishedSuit,
        finishOrder = players.filter { it.finishedPosition != null }.sortedBy { it.finishedPosition }.map { it.id },
    )
}

fun PsGameState.act(playerId: String, action: PsAction): PsGameState =
    when (val r = PsEngine.apply(this, playerId, action)) {
        is ActionResult.Accepted -> r.state
        is ActionResult.Rejected -> {
            fail("Expected $action by $playerId to be accepted but got ${r.reason}")
            error("unreachable")
        }
    }

fun PsGameState.rejection(playerId: String, action: PsAction): String =
    when (val r = PsEngine.apply(this, playerId, action)) {
        is ActionResult.Accepted -> {
            fail("Expected $action by $playerId to be rejected")
            error("unreachable")
        }
        is ActionResult.Rejected -> r.reason
    }

fun ps(spec: String, suit: Suit? = null) = PsAction.Play(PsFx.cards(spec), suit)

fun PsGameState.p(id: String): PsPlayerState = requireNotNull(player(id))

fun PsGameState.hand(id: String): List<Card> = p(id).hand

inline fun <reified T : PsEvent> PsGameState.events(): List<T> = log.map { it.event }.filterIsInstance<T>()
