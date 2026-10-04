package nl.bluecard.app.ui.text

import nl.bluecard.app.R
import nl.bluecard.app.res.Resources
import nl.bluecard.engine.presidenten.PrEvent
import nl.bluecard.engine.presidenten.PrPreset
import nl.bluecard.engine.presidenten.PrTitle

/** Texts for Presidenten events, titles and options. */
object PresidentTexts {

    fun event(res: Resources, event: PrEvent, nameOf: (String) -> String): String = when (event) {
        is PrEvent.GameStarted -> res.getString(R.string.ev_started, nameOf(event.startingPlayerId))
        is PrEvent.TributeGiven -> res.getQuantityString(R.plurals.pr_ev_tribute, event.count, nameOf(event.fromId), nameOf(event.toId), event.count)
        is PrEvent.CardsReturned -> res.getQuantityString(R.plurals.pr_ev_returned, event.count, nameOf(event.fromId), nameOf(event.toId), event.count)
        is PrEvent.CardsPlayed -> res.getString(R.string.ev_played, nameOf(event.playerId), GameTexts.cards(event.cards))
        is PrEvent.Passed -> res.getString(R.string.ev_ps_passed, nameOf(event.playerId))
        is PrEvent.PlayerSkipped -> res.getString(R.string.ev_skipped, nameOf(event.skippedId))
        is PrEvent.TrickWon -> res.getString(R.string.pr_ev_trick, nameOf(event.playerId))
        is PrEvent.PlayerFinished -> res.getString(R.string.ev_player_out, nameOf(event.playerId), event.position)
        is PrEvent.Resigned -> res.getString(R.string.ev_resigned, nameOf(event.playerId))
        is PrEvent.GameOver -> res.getString(R.string.ev_game_over, nameOf(event.winnerId))
    }

    fun title(res: Resources, title: PrTitle): String = res.getString(
        when (title) {
            PrTitle.PRESIDENT -> R.string.pr_title_PRESIDENT
            PrTitle.VICE_PRESIDENT -> R.string.pr_title_VICE_PRESIDENT
            PrTitle.CITIZEN -> R.string.pr_title_CITIZEN
            PrTitle.VICE_SCUM -> R.string.pr_title_VICE_SCUM
            PrTitle.SCUM -> R.string.pr_title_SCUM
        },
    )

    /** The title that goes with finishing at [position] (1-based) of [players]. */
    fun titleFor(position: Int, players: Int): PrTitle = when {
        position == 1 -> PrTitle.PRESIDENT
        position == players -> PrTitle.SCUM
        players >= 4 && position == 2 -> PrTitle.VICE_PRESIDENT
        players >= 4 && position == players - 1 -> PrTitle.VICE_SCUM
        else -> PrTitle.CITIZEN
    }

    fun preset(res: Resources, preset: PrPreset?): String = res.getString(
        when (preset) {
            PrPreset.CLASSIC -> R.string.pr_preset_CLASSIC
            PrPreset.WITH_JOKERS -> R.string.pr_preset_WITH_JOKERS
            null -> R.string.preset_custom
        },
    )
}
