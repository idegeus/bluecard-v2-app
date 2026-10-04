package nl.bluecard.app.ui.screens

import nl.bluecard.app.res.Resources
import nl.bluecard.app.ui.components.LogItem
import nl.bluecard.app.ui.components.LogTone
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.text.HeartsTexts
import nl.bluecard.app.ui.text.PestenTexts
import nl.bluecard.app.ui.text.PresidentTexts
import nl.bluecard.engine.hartenjagen.HjEvent
import nl.bluecard.engine.hartenjagen.HjLogEntry
import nl.bluecard.engine.presidenten.PrEvent
import nl.bluecard.engine.presidenten.PrLogEntry
import nl.bluecard.engine.pesten.PsEvent
import nl.bluecard.engine.pesten.PsLogEntry
import nl.bluecard.engine.zweedspesten.ZpEvent
import nl.bluecard.engine.zweedspesten.ZpLogEntry

/** Who acted, which cards and how it should stand out, for the history of Zweeds Pesten. */
internal fun zweedsLogItem(res: Resources, entry: ZpLogEntry, viewerId: String, nameOf: (String) -> String, isBot: (String) -> Boolean, avatarOf: (String) -> String? = { null }): LogItem {
    val e = entry.event
    val text = GameTexts.event(res, e, viewerId, nameOf)
    fun item(actor: String?, cards: List<nl.bluecard.engine.model.Card> = emptyList(), tone: LogTone = LogTone.NORMAL) =
        LogItem(entry.seq, text, actor?.let(nameOf), actor?.let(isBot) ?: false, actor?.let(avatarOf), cards, tone)
    return when (e) {
        is ZpEvent.CardsPlayed -> item(e.playerId, e.cards)
        is ZpEvent.BlindRevealed -> item(e.playerId, listOf(e.card), if (e.success) LogTone.NORMAL else LogTone.BAD)
        is ZpEvent.GambleRevealed -> item(e.playerId, listOf(e.card), if (e.success) LogTone.GOOD else LogTone.BAD)
        is ZpEvent.PileTaken -> item(e.playerId, tone = LogTone.BAD)
        is ZpEvent.PileBurned -> item(e.playerId, tone = LogTone.FIRE)
        is ZpEvent.PlayersSkipped -> item(e.byPlayerId)
        is ZpEvent.DirectionReversed -> item(e.playerId)
        is ZpEvent.ExtraTurn -> item(e.playerId, tone = LogTone.GOOD)
        is ZpEvent.StartsAfterPickUp -> item(e.playerId)
        is ZpEvent.Swapped -> item(e.playerId)
        is ZpEvent.PlayerReady -> item(e.playerId)
        is ZpEvent.GameStarted -> item(e.startingPlayerId)
        is ZpEvent.PlayerFinished -> item(e.playerId, tone = LogTone.GOLD)
        is ZpEvent.GameOver -> item(e.winnerId, tone = LogTone.GOLD)
        is ZpEvent.CheatCaught -> item(e.cheaterId, e.cards, LogTone.CHEAT)
        is ZpEvent.FalseAccusation -> item(e.accuserId, tone = LogTone.ALERT)
        is ZpEvent.Resigned -> item(e.playerId, tone = LogTone.BAD)
        is ZpEvent.CardsExchanged -> item(e.winnerId, tone = LogTone.GOLD)
        else -> item(null)
    }
}

/** The same for Pesten. */
internal fun pestenLogItem(res: Resources, entry: PsLogEntry, viewerId: String, nameOf: (String) -> String, isBot: (String) -> Boolean, avatarOf: (String) -> String? = { null }): LogItem {
    val e = entry.event
    val text = PestenTexts.event(res, e, viewerId, nameOf)
    fun item(actor: String?, cards: List<nl.bluecard.engine.model.Card> = emptyList(), tone: LogTone = LogTone.NORMAL) =
        LogItem(entry.seq, text, actor?.let(nameOf), actor?.let(isBot) ?: false, actor?.let(avatarOf), cards, tone)
    return when (e) {
        is PsEvent.GameStarted -> item(e.startingPlayerId, listOf(e.startCard))
        is PsEvent.CardsPlayed -> item(e.playerId, e.cards)
        is PsEvent.CardsDrawn -> item(e.playerId, tone = if (e.penalty) LogTone.BAD else LogTone.NORMAL)
        is PsEvent.Passed -> item(e.playerId)
        is PsEvent.MustDraw -> item(e.playerId, tone = LogTone.ALERT)
        is PsEvent.PlaysAgain -> item(e.playerId, tone = LogTone.GOOD)
        is PsEvent.PlaysAfterPenalty -> item(e.playerId, tone = LogTone.GOOD)
        is PsEvent.PlayersSkipped -> item(e.byPlayerId)
        is PsEvent.DirectionReversed -> item(e.playerId)
        is PsEvent.LastCardCalled -> item(e.playerId, tone = LogTone.GOOD)
        is PsEvent.LastCardForgotten -> item(e.playerId, tone = LogTone.ALERT)
        is PsEvent.SpecialFinishPenalty -> item(e.playerId, tone = LogTone.BAD)
        is PsEvent.PlayerFinished -> item(e.playerId, tone = LogTone.GOLD)
        is PsEvent.GameOver -> item(e.winnerId, tone = LogTone.GOLD)
        is PsEvent.CheatCaught -> item(e.cheaterId, e.cards, LogTone.CHEAT)
        is PsEvent.FalseAccusation -> item(e.accuserId, tone = LogTone.ALERT)
        is PsEvent.Resigned -> item(e.playerId, tone = LogTone.BAD)
        is PsEvent.CardsExchanged -> item(e.winnerId, tone = LogTone.GOLD)
        is PsEvent.DrawPileReshuffled -> item(null)
    }
}

/** The same for Presidenten. */
internal fun presidentLogItem(res: Resources, entry: PrLogEntry, nameOf: (String) -> String, isBot: (String) -> Boolean, avatarOf: (String) -> String? = { null }): LogItem {
    val e = entry.event
    val text = PresidentTexts.event(res, e, nameOf)
    fun item(actor: String?, cards: List<nl.bluecard.engine.model.Card> = emptyList(), tone: LogTone = LogTone.NORMAL) =
        LogItem(entry.seq, text, actor?.let(nameOf), actor?.let(isBot) ?: false, actor?.let(avatarOf), cards, tone)
    return when (e) {
        is PrEvent.GameStarted -> item(e.startingPlayerId)
        is PrEvent.TributeGiven -> item(e.fromId, tone = LogTone.BAD)
        is PrEvent.CardsReturned -> item(e.fromId, tone = LogTone.GOLD)
        is PrEvent.CardsPlayed -> item(e.playerId, e.cards)
        is PrEvent.Passed -> item(e.playerId, tone = LogTone.BAD)
        is PrEvent.PlayerSkipped -> item(e.skippedId, tone = LogTone.ALERT)
        is PrEvent.TrickWon -> item(e.playerId, tone = LogTone.GOOD)
        is PrEvent.PlayerFinished -> item(e.playerId, tone = LogTone.GOLD)
        is PrEvent.Resigned -> item(e.playerId, tone = LogTone.BAD)
        is PrEvent.GameOver -> item(e.winnerId, tone = LogTone.GOLD)
    }
}

/** The same for Hartenjagen. */
internal fun heartsLogItem(res: Resources, entry: HjLogEntry, nameOf: (String) -> String, isBot: (String) -> Boolean, avatarOf: (String) -> String? = { null }): LogItem {
    val e = entry.event
    val text = HeartsTexts.event(res, e, nameOf)
    fun item(actor: String?, cards: List<nl.bluecard.engine.model.Card> = emptyList(), tone: LogTone = LogTone.NORMAL) =
        LogItem(entry.seq, text, actor?.let(nameOf), actor?.let(isBot) ?: false, actor?.let(avatarOf), cards, tone)
    return when (e) {
        is HjEvent.DealStarted -> item(null, tone = LogTone.GOLD)
        is HjEvent.CardsPassed -> item(e.playerId)
        is HjEvent.Leads -> item(e.playerId)
        is HjEvent.Played -> item(e.playerId, listOf(e.card))
        is HjEvent.HeartsBroken -> item(e.playerId, tone = LogTone.FIRE)
        is HjEvent.TrickWon -> item(e.playerId, tone = if (e.points > 0) LogTone.BAD else LogTone.NORMAL)
        is HjEvent.MoonShot -> item(e.playerId, tone = LogTone.GOLD)
        is HjEvent.DealScored -> item(null, tone = LogTone.GOLD)
        is HjEvent.Resigned -> item(e.playerId, tone = LogTone.BAD)
        is HjEvent.GameOver -> item(e.winnerId, tone = LogTone.GOLD)
    }
}
