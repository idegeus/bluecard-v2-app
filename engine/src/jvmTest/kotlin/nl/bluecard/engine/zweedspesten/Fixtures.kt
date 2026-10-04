package nl.bluecard.engine.zweedspesten

import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Deck
import nl.bluecard.engine.model.DiscardPile
import org.junit.Assert.fail

/** Small DSL to build hand-crafted game states. Card lists are written as "3H 4C 10S". */
object Fx {
    fun cards(spec: String): List<Card> = spec.split(" ").filter { it.isNotBlank() }.map(Card::of)

    fun player(
        id: String,
        hand: String = "",
        up: String = "",
        down: String = "",
        finished: Int? = null,
        ready: Boolean = true,
    ) = ZpPlayerState(
        id = id,
        name = id,
        hand = cards(hand),
        faceUp = cards(up),
        faceDown = cards(down),
        ready = ready,
        finishedPosition = finished,
    )

    /** [draw] is listed bottom-to-top: the LAST card is drawn first. Same for [discard]. */
    fun state(
        vararg players: ZpPlayerState,
        discard: String = "",
        draw: String = "",
        burned: String = "",
        rules: ZpHouseRules = ZpHouseRules(),
        current: String = players.first().id,
        direction: Int = 1,
        phase: ZpPhase = ZpPhase.PLAYING,
        /** Rule tests run with enforcement on; cheating mode has its own tests. */
        enforce: Boolean = true,
    ) = ZpGameState(
        rules = rules.copy(enforceRules = enforce),
        players = players.toList(),
        drawPile = Deck(cards(draw)),
        discardPile = DiscardPile(cards(discard)),
        burned = cards(burned),
        phase = phase,
        turn = ZpTurnState(if (phase == ZpPhase.PLAYING) current else null, direction),
        finishOrder = players.filter { it.finishedPosition != null }.sortedBy { it.finishedPosition }.map { it.id },
    )
}

fun ZpGameState.act(playerId: String, action: ZpAction): ZpGameState =
    when (val r = ZpEngine.apply(this, playerId, action)) {
        is ActionResult.Accepted -> r.state
        is ActionResult.Rejected -> {
            fail("Expected $action by $playerId to be accepted but got ${r.reason}")
            error("unreachable")
        }
    }

fun ZpGameState.rejection(playerId: String, action: ZpAction): String =
    when (val r = ZpEngine.apply(this, playerId, action)) {
        is ActionResult.Accepted -> {
            fail("Expected $action by $playerId to be rejected")
            error("unreachable")
        }
        is ActionResult.Rejected -> r.reason
    }

fun play(spec: String) = ZpAction.Play(Fx.cards(spec))

fun ZpGameState.p(id: String): ZpPlayerState = requireNotNull(player(id))

inline fun <reified T : ZpEvent> ZpGameState.events(): List<T> = log.map { it.event }.filterIsInstance<T>()
