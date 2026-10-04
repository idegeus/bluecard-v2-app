package nl.bluecard.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import nl.bluecard.app.ui.components.FxAnchor
import nl.bluecard.app.ui.components.TableFx
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.hartenjagen.HjEvent
import nl.bluecard.engine.hartenjagen.HjPassDirection
import nl.bluecard.engine.hartenjagen.HjPhase
import nl.bluecard.engine.hartenjagen.HjPlayerView
import nl.bluecard.engine.pesten.PsEvent
import nl.bluecard.engine.presidenten.PrEvent
import nl.bluecard.engine.presidenten.PrPlayerView
import nl.bluecard.engine.pesten.PsPlayerView
import nl.bluecard.engine.zweedspesten.CardSource
import nl.bluecard.engine.zweedspesten.ZpEvent
import nl.bluecard.engine.zweedspesten.ZpPlayerView
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.components.REVEAL_MS
import nl.bluecard.app.platform.Sound

/*
 * Turns what happened between two views into table animations: cards flying from players to the pile, drawn cards
 * flying from the draw pile (and turning over when they are yours), the pile flying to whoever takes it, blind cards
 * flipping, burning piles and the deal at the start. Only new log entries are animated, never the history.
 */

/** Cards that are in [new] but not (as often) in [old]. */
internal fun addedCards(old: List<Card>, new: List<Card>): List<Card> {
    val left = old.toMutableList()
    return new.filter { !left.remove(it) }
}

private fun anchorOf(playerId: String, viewerId: String) = if (playerId == viewerId) FxAnchor.HAND else FxAnchor.player(playerId)

/** Up to [max] cards as face-down flights. */
private fun backs(count: Int, max: Int = 8): List<Card?> = List(count.coerceIn(0, max)) { null }

@Composable
fun ZweedsTableEffects(view: ZpPlayerView, fx: TableFx) {
    val sounds = appContainer().sounds
    val previous = remember { mutableStateOf<ZpPlayerView?>(null) }
    LaunchedEffect(view.stateVersion, view.viewerId) {
        val old = previous.value
        previous.value = view
        val me = view.viewerId
        if (old == null) {
            // A brand-new game: deal the hands.
            if (view.log.all { it.event is ZpEvent.SwapPhaseStarted || it.event is ZpEvent.GameStarted }) {
                deal(fx, view.players.map { it.id }, view.myHand.size, me)
            }
            return@LaunchedEffect
        }
        val lastSeen = old.log.lastOrNull()?.seq ?: 0
        val fresh = view.log.filter { it.seq > lastSeen }.map { it.event }
        var pileBefore = old.discardTop
        var refillFor: String? = null
        // What follows a blind reveal waits until the card has landed.
        var waitMs = 0L
        for (event in fresh) {
            when (event) {
                is ZpEvent.CardsPlayed -> {
                    refillFor = event.playerId
                    val from = when {
                        event.playerId != me -> FxAnchor.player(event.playerId)
                        event.source == CardSource.FACE_UP -> FxAnchor.TABLE
                        else -> FxAnchor.HAND
                    }
                    if (event.playerId != me || !fx.consumeSelfPlayed(event.cards)) fx.fly(event.cards, from, FxAnchor.PILE)
                    pileBefore = pileBefore + event.cards
                }
                is ZpEvent.BlindRevealed -> {
                    // One of the last cards: the big moment. It turns over in the middle of the table, then lands.
                    val from = when {
                        event.playerId != me -> FxAnchor.player(event.playerId)
                        fx.consumeSelfPlayedBlind() -> FxAnchor.PILE // dragged there already
                        else -> FxAnchor.TABLE
                    }
                    fx.reveal(event.card, from, event.success, delayMs = waitMs)
                    sounds.play(Sound.DRUMROLL)
                    waitMs += REVEAL_MS
                    pileBefore = pileBefore + event.card
                    if (event.success) refillFor = event.playerId
                }
                is ZpEvent.GambleRevealed -> {
                    fx.fly(listOf(event.card), FxAnchor.DRAW, FxAnchor.PILE, flip = true, durationMs = 560)
                    pileBefore = pileBefore + event.card
                    if (event.success) refillFor = event.playerId
                }
                is ZpEvent.PileTaken -> {
                    // The pile empties into the player's hand.
                    fx.fly(pileBefore.takeLast(8).reversed(), FxAnchor.PILE, anchorOf(event.playerId, me), staggerMs = 55, startDelayMs = 300 + waitMs)
                    pileBefore = emptyList()
                }
                is ZpEvent.PileBurned -> {
                    fx.burn(pileBefore, delayMs = 330 + waitMs)
                    pileBefore = emptyList()
                }
                is ZpEvent.CheatCaught -> fx.fly(pileBefore.takeLast(8).reversed(), FxAnchor.PILE, anchorOf(event.cheaterId, me), staggerMs = 55)
                is ZpEvent.FalseAccusation -> fx.fly(pileBefore.takeLast(8).reversed(), FxAnchor.PILE, anchorOf(event.accuserId, me), staggerMs = 55)
                is ZpEvent.CardsExchanged -> exchange(fx, event.winnerId, event.loserId, me, old.myHand, view.myHand)
                else -> Unit
            }
        }
        // Hands are refilled from the draw pile after a play: those cards fly in (yours turn face up).
        val drawn = old.drawPileCount - view.drawPileCount - fresh.count { it is ZpEvent.GambleRevealed }
        val player = refillFor
        if (drawn > 0 && player != null) {
            if (player == me) {
                fx.fly(addedCards(old.myHand, view.myHand).takeLast(drawn), FxAnchor.DRAW, FxAnchor.HAND, flip = true, startDelayMs = 250 + waitMs)
            } else {
                fx.fly(backs(drawn), FxAnchor.DRAW, FxAnchor.player(player), startDelayMs = 250 + waitMs)
            }
        }
    }
}

@Composable
fun PestenTableEffects(view: PsPlayerView, fx: TableFx) {
    val previous = remember { mutableStateOf<PsPlayerView?>(null) }
    LaunchedEffect(view.stateVersion, view.viewerId) {
        val old = previous.value
        previous.value = view
        val me = view.viewerId
        if (old == null) {
            if (view.log.all { it.event is PsEvent.GameStarted }) deal(fx, view.players.map { it.id }, view.myHand.size, me)
            return@LaunchedEffect
        }
        val lastSeen = old.log.lastOrNull()?.seq ?: 0
        val fresh = view.log.filter { it.seq > lastSeen }.map { it.event }
        // My new cards, handed out to the draws in order, so they turn face up as they arrive.
        val myNew = addedCards(old.myHand, view.myHand).toMutableList()
        fun drawTo(playerId: String, count: Int, delay: Long = 0) {
            if (count <= 0) return
            if (playerId == me) {
                val cards = myNew.take(count).also { myNew.removeAll(it.toSet()) }
                fx.fly(cards, FxAnchor.DRAW, FxAnchor.HAND, flip = true, startDelayMs = delay)
            } else {
                fx.fly(backs(count), FxAnchor.DRAW, FxAnchor.player(playerId), startDelayMs = delay)
            }
        }
        for (event in fresh) {
            when (event) {
                is PsEvent.CardsPlayed ->
                    if (event.playerId != me || !fx.consumeSelfPlayed(event.cards)) {
                        fx.fly(event.cards, anchorOf(event.playerId, me), FxAnchor.PILE)
                    }
                is PsEvent.CardsDrawn -> drawTo(event.playerId, event.count)
                is PsEvent.LastCardForgotten -> drawTo(event.playerId, event.penaltyCount, delay = 200)
                is PsEvent.FalseAccusation -> drawTo(event.accuserId, event.penaltyCount, delay = 200)
                is PsEvent.SpecialFinishPenalty -> drawTo(event.playerId, 1, delay = 300)
                is PsEvent.CheatCaught -> {
                    // The cheated cards come back from the pile, then the penalty.
                    fx.fly(event.cards, FxAnchor.PILE, anchorOf(event.cheaterId, me))
                    myNew.removeAll(event.cards.toSet())
                    drawTo(event.cheaterId, event.penaltyCount, delay = 350)
                }
                is PsEvent.CardsExchanged -> exchange(fx, event.winnerId, event.loserId, me, old.myHand, view.myHand)
                is PsEvent.DrawPileReshuffled ->
                    // The discard pile empties into a new draw pile.
                    fx.fly(backs(event.count, max = 10), FxAnchor.PILE, FxAnchor.DRAW, staggerMs = 40, durationMs = 320)
                else -> Unit
            }
        }
    }
}

@Composable
fun PresidentTableEffects(view: PrPlayerView, fx: TableFx) {
    val previous = remember { mutableStateOf<PrPlayerView?>(null) }
    LaunchedEffect(view.stateVersion, view.viewerId) {
        val old = previous.value
        previous.value = view
        val me = view.viewerId
        if (old == null) {
            if (view.log.size <= 1 + view.exchanges.size) deal(fx, view.players.map { it.id }, view.myHand.size, me)
            return@LaunchedEffect
        }
        val lastSeen = old.log.lastOrNull()?.seq ?: 0
        val fresh = view.log.filter { it.seq > lastSeen }.map { it.event }
        var trick = old.plays.flatMap { it.cards }
        for (event in fresh) {
            when (event) {
                is PrEvent.CardsPlayed -> {
                    if (event.playerId != me || !fx.consumeSelfPlayed(event.cards)) fx.fly(event.cards, anchorOf(event.playerId, me), FxAnchor.PILE)
                    trick = trick + event.cards
                }
                // The won trick is swept towards its winner.
                is PrEvent.TrickWon -> {
                    fx.fly(backs(trick.size.coerceAtMost(4)), FxAnchor.PILE, anchorOf(event.playerId, me), staggerMs = 40, startDelayMs = 650, durationMs = 300)
                    trick = emptyList()
                }
                is PrEvent.TributeGiven -> fx.fly(backs(event.count), anchorOf(event.fromId, me), anchorOf(event.toId, me), durationMs = 600, startDelayMs = 400)
                is PrEvent.CardsReturned -> fx.fly(backs(event.count), anchorOf(event.fromId, me), anchorOf(event.toId, me), durationMs = 600)
                else -> Unit
            }
        }
    }
}

@Composable
fun HeartsTableEffects(view: HjPlayerView, fx: TableFx) {
    val previous = remember { mutableStateOf<HjPlayerView?>(null) }
    LaunchedEffect(view.stateVersion, view.viewerId) {
        val old = previous.value
        previous.value = view
        val me = view.viewerId
        if (old == null) {
            if (view.trickNumber == 0 && view.trick.isEmpty()) deal(fx, view.players.map { it.id }, view.myHand.size, me)
            return@LaunchedEffect
        }
        val lastSeen = old.log.lastOrNull()?.seq ?: 0
        val fresh = view.log.filter { it.seq > lastSeen }.map { it.event }
        for (event in fresh) {
            when (event) {
                is HjEvent.Played -> if (event.playerId != me || !fx.consumeSelfPlayed(listOf(event.card))) {
                    fx.fly(listOf(event.card), anchorOf(event.playerId, me), FxAnchor.PILE)
                }
                is HjEvent.TrickWon ->
                    fx.fly(backs(view.players.size), FxAnchor.PILE, anchorOf(event.playerId, me), staggerMs = 35, startDelayMs = 900, durationMs = 320)
                is HjEvent.DealStarted -> deal(fx, view.players.map { it.id }, view.myHand.size, me)
                else -> Unit
            }
        }
        // Passing done: the chosen cards cross the table to their neighbours.
        if (old.phase == HjPhase.PASSING && view.phase != HjPhase.PASSING) {
            val ids = view.players.map { it.id }
            val step = when (old.passDirection) {
                HjPassDirection.LEFT -> 1
                HjPassDirection.RIGHT -> ids.size - 1
                HjPassDirection.ACROSS -> 2
                HjPassDirection.NONE -> 0
            }
            if (step != 0) {
                ids.forEachIndexed { i, id ->
                    fx.fly(backs(view.rules.passCount), anchorOf(id, me), anchorOf(ids[(i + step) % ids.size], me), durationMs = 520, staggerMs = 60)
                }
            }
        }
    }
}

/** Winner and loser swap a card: two cards cross the table, face up only for the two players involved. */
private fun exchange(fx: TableFx, winnerId: String, loserId: String, me: String, oldHand: List<Card>, newHand: List<Card>) {
    val given = addedCards(newHand, oldHand).firstOrNull() // left my hand
    val received = addedCards(oldHand, newHand).firstOrNull() // came into my hand
    val winnerToLoser = when (me) {
        winnerId -> given
        loserId -> received
        else -> null
    }
    val loserToWinner = when (me) {
        loserId -> given
        winnerId -> received
        else -> null
    }
    fx.fly(listOf(winnerToLoser), anchorOf(winnerId, me), anchorOf(loserId, me), durationMs = 600)
    fx.fly(listOf(loserToWinner), anchorOf(loserId, me), anchorOf(winnerId, me), durationMs = 600)
}

/** The deal at the start of a game: one card at a time around the table. */
private fun deal(fx: TableFx, playerIds: List<String>, handSize: Int, me: String) {
    var delay = 0L
    repeat(handSize.coerceAtMost(8)) {
        for (id in playerIds) {
            fx.fly(listOf(null), FxAnchor.DRAW, anchorOf(id, me), durationMs = 300, startDelayMs = delay)
            delay += 45
        }
    }
}
