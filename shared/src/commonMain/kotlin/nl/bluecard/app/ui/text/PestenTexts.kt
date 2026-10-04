package nl.bluecard.app.ui.text

import nl.bluecard.app.res.Resources
import nl.bluecard.app.R
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.pesten.PsEffect
import nl.bluecard.engine.pesten.PsEvent
import nl.bluecard.engine.pesten.PsHouseRules
import nl.bluecard.engine.pesten.PsPreset

/** Dutch texts for Pesten events and options. Reject codes go through [GameTexts.reject]. */
object PestenTexts {

    fun event(res: Resources, event: PsEvent, viewerId: String? = null, nameOf: (String) -> String): String = when (event) {
        is PsEvent.GameStarted -> res.getString(R.string.ev_started, nameOf(event.startingPlayerId))
        is PsEvent.CardsPlayed -> event.suit?.let {
            res.getString(R.string.ev_ps_played_suit, nameOf(event.playerId), GameTexts.cards(event.cards), GameTexts.suitName(res, it))
        } ?: res.getString(R.string.ev_played, nameOf(event.playerId), GameTexts.cards(event.cards))
        is PsEvent.CardsDrawn -> res.getQuantityString(
            if (event.penalty) R.plurals.ev_ps_drew_penalty else R.plurals.ev_ps_drew,
            event.count,
            nameOf(event.playerId),
            event.count,
        )
        is PsEvent.Passed -> res.getString(R.string.ev_ps_passed, nameOf(event.playerId))
        is PsEvent.PlaysAfterPenalty -> res.getString(R.string.ev_ps_plays_after_penalty, nameOf(event.playerId))
        is PsEvent.MustDraw -> res.getQuantityString(R.plurals.ev_ps_must_draw, event.count, nameOf(event.playerId), event.count)
        is PsEvent.PlaysAgain -> res.getString(R.string.ev_extra_turn, nameOf(event.playerId))
        is PsEvent.PlayersSkipped -> res.getString(R.string.ev_skipped, event.skippedIds.joinToString(", ") { nameOf(it) })
        is PsEvent.DirectionReversed -> res.getString(R.string.ev_reversed)
        is PsEvent.LastCardCalled -> res.getString(R.string.ev_ps_last_card, nameOf(event.playerId))
        is PsEvent.LastCardForgotten -> if (event.playerId == viewerId) {
            res.getQuantityString(R.plurals.ev_ps_forgotten_you, event.penaltyCount, nameOf(event.catcherId), event.penaltyCount)
        } else {
            res.getQuantityString(R.plurals.ev_ps_forgotten, event.penaltyCount, nameOf(event.catcherId), nameOf(event.playerId), event.penaltyCount)
        }
        is PsEvent.SpecialFinishPenalty -> res.getString(R.string.ev_ps_special_finish, nameOf(event.playerId))
        is PsEvent.DrawPileReshuffled -> res.getString(R.string.ev_ps_reshuffled, event.count)
        is PsEvent.PlayerFinished -> res.getString(R.string.ev_player_out, nameOf(event.playerId), event.position)
        is PsEvent.CardsExchanged -> res.getString(R.string.ev_exchanged, nameOf(event.winnerId), nameOf(event.loserId))
        is PsEvent.Resigned -> res.getString(R.string.ev_resigned, nameOf(event.playerId))
        is PsEvent.GameOver -> res.getString(R.string.ev_game_over, nameOf(event.winnerId))
        is PsEvent.CheatCaught -> if (event.cheaterId == viewerId) {
            res.getQuantityString(R.plurals.ev_cheat_caught_you, event.penaltyCount, nameOf(event.accuserId), GameTexts.cards(event.cards), event.penaltyCount)
        } else {
            res.getQuantityString(
                R.plurals.ev_cheat_caught,
                event.penaltyCount,
                nameOf(event.accuserId),
                nameOf(event.cheaterId),
                GameTexts.cards(event.cards),
                event.penaltyCount,
            )
        }
        is PsEvent.FalseAccusation -> res.getQuantityString(R.plurals.ev_ps_false_accusation, event.penaltyCount, nameOf(event.accuserId), event.penaltyCount)
    }

    fun effectLong(res: Resources, effect: PsEffect): String = res.getString(
        when (effect) {
            PsEffect.NONE -> R.string.ps_effect_NONE
            PsEffect.DRAW_TWO -> R.string.ps_effect_DRAW_TWO
            PsEffect.PLAY_AGAIN -> R.string.ps_effect_PLAY_AGAIN
            PsEffect.SKIP -> R.string.ps_effect_SKIP
            PsEffect.CHOOSE_SUIT -> R.string.ps_effect_CHOOSE_SUIT
            PsEffect.REVERSE -> R.string.ps_effect_REVERSE
        },
    )

    fun effectShort(res: Resources, effect: PsEffect): String = res.getString(
        when (effect) {
            PsEffect.NONE -> R.string.ps_effect_short_NONE
            PsEffect.DRAW_TWO -> R.string.ps_effect_short_DRAW_TWO
            PsEffect.PLAY_AGAIN -> R.string.ps_effect_short_PLAY_AGAIN
            PsEffect.SKIP -> R.string.ps_effect_short_SKIP
            PsEffect.CHOOSE_SUIT -> R.string.ps_effect_short_CHOOSE_SUIT
            PsEffect.REVERSE -> R.string.ps_effect_short_REVERSE
        },
    )

    fun jokerLong(res: Resources, rules: PsHouseRules): String = res.getQuantityString(R.plurals.ps_effect_joker, rules.jokerDraw, rules.jokerDraw)

    fun jokerShort(res: Resources, rules: PsHouseRules): String = res.getString(R.string.ps_effect_short_joker, rules.jokerDraw)

    fun preset(res: Resources, preset: PsPreset?): String = res.getString(
        when (preset) {
            PsPreset.CLASSIC -> R.string.ps_preset_CLASSIC
            PsPreset.SIMPLE -> R.string.ps_preset_SIMPLE
            PsPreset.NO_JOKERS -> R.string.ps_preset_NO_JOKERS
            null -> R.string.preset_custom
        },
    )

    /** The special cards of these rules, jokers last, for summaries and the rules screen. */
    fun specials(rules: PsHouseRules): List<Pair<Rank, PsEffect?>> =
        rules.specialRanks().map { (rank, effect) -> rank to effect } + if (rules.jokers > 0) listOf(Rank.JOKER to null) else emptyList()
}
