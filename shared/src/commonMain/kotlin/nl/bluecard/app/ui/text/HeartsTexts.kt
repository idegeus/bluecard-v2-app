package nl.bluecard.app.ui.text

import nl.bluecard.app.R
import nl.bluecard.app.res.Resources
import nl.bluecard.engine.hartenjagen.HjEvent
import nl.bluecard.engine.hartenjagen.HjPreset

/** Texts for Hartenjagen events and options. */
object HeartsTexts {

    fun event(res: Resources, event: HjEvent, nameOf: (String) -> String): String = when (event) {
        is HjEvent.DealStarted -> res.getString(R.string.hj_round, event.deal + 1)
        is HjEvent.CardsPassed -> res.getString(R.string.hj_ev_passed, nameOf(event.playerId))
        is HjEvent.Leads -> res.getString(R.string.hj_ev_lead, nameOf(event.playerId))
        is HjEvent.Played -> res.getString(R.string.ev_played, nameOf(event.playerId), GameTexts.card(event.card))
        is HjEvent.HeartsBroken -> res.getString(R.string.hj_ev_hearts_broken)
        is HjEvent.TrickWon -> if (event.points == 0) {
            res.getString(R.string.pr_ev_trick, nameOf(event.playerId))
        } else {
            res.getQuantityString(R.plurals.hj_ev_trick, event.points, nameOf(event.playerId), event.points)
        }
        is HjEvent.MoonShot -> res.getString(R.string.hj_ev_moon, nameOf(event.playerId), event.points)
        is HjEvent.DealScored -> res.getString(R.string.hj_ev_scored, event.deal + 1)
        is HjEvent.Resigned -> res.getString(R.string.ev_resigned, nameOf(event.playerId))
        is HjEvent.GameOver -> res.getString(R.string.ev_game_over, nameOf(event.winnerId))
    }

    fun points(res: Resources, points: Int): String = res.getQuantityString(R.plurals.hj_points, points, points)

    fun target(res: Resources, target: Int): String =
        if (target <= 0) res.getString(R.string.hj_target_one) else res.getString(R.string.hj_target_value, target)

    fun preset(res: Resources, preset: HjPreset?): String = res.getString(
        when (preset) {
            HjPreset.CLASSIC -> R.string.hj_preset_CLASSIC
            HjPreset.INTERNATIONAL -> R.string.hj_preset_INTERNATIONAL
            null -> R.string.preset_custom
        },
    )
}
