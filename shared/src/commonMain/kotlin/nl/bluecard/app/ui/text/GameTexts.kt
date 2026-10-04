package nl.bluecard.app.ui.text

import nl.bluecard.app.res.Resources
import nl.bluecard.app.R
import nl.bluecard.app.session.GameKind
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.model.Suit
import nl.bluecard.engine.zweedspesten.BurnReason
import nl.bluecard.engine.zweedspesten.CardSource
import nl.bluecard.engine.zweedspesten.StartRule
import nl.bluecard.engine.zweedspesten.ZpEffect
import nl.bluecard.engine.zweedspesten.ZpEvent
import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.engine.zweedspesten.ZpPreset
import nl.bluecard.multiplayer.protocol.JoinRejectReason
import nl.bluecard.multiplayer.session.ConnectionStatus
import nl.bluecard.multiplayer.session.LostReason
import nl.bluecard.multiplayer.session.SessionNotice

/**
 * Turns engine/protocol codes into Dutch text. Plain functions on [Resources] so they can be used from
 * composables as well as from effects (snackbars).
 */
object GameTexts {

    /** How the viewer appears in event lines: "Jij" in Dutch (same verb form as a name), otherwise their own name. */
    fun selfName(res: Resources, ownName: String): String =
        if (Resources.language in ADDRESS_VIEWER_LANGUAGES) res.getString(R.string.you).replaceFirstChar { it.uppercase() } else ownName

    /** Languages where "you" takes the same verb form as a name ("Jij speelt" / "Anna speelt"). */
    private val ADDRESS_VIEWER_LANGUAGES = setOf("nl")

    /** U+FE0E forces the text (not emoji) presentation of the suit symbols in running text. */
    private fun suitSymbol(suit: Suit): String = suit.symbol + "︎"

    fun card(card: Card): String =
        if (card.rank.isJoker) CardLabels.label(card.rank) else CardLabels.label(card.rank) + suitSymbol(card.suit)

    fun suitSymbolText(suit: Suit): String = suitSymbol(suit)

    fun suitName(res: Resources, suit: Suit): String = res.getString(
        when (suit) {
            Suit.CLUBS -> R.string.suit_CLUBS
            Suit.DIAMONDS -> R.string.suit_DIAMONDS
            Suit.HEARTS -> R.string.suit_HEARTS
            Suit.SPADES -> R.string.suit_SPADES
        },
    )

    fun gameName(res: Resources, game: GameKind): String = res.getString(
        when (game) {
            GameKind.ZWEEDS_PESTEN -> R.string.game_zweeds_pesten
            GameKind.PESTEN -> R.string.game_pesten
            GameKind.PRESIDENTEN -> R.string.game_presidenten
            GameKind.HARTENJAGEN -> R.string.game_hartenjagen
        },
    )

    fun cards(cards: List<Card>): String = cards.joinToString(" ") { card(it) }

    fun rankName(rank: Rank): String = CardLabels.label(rank)

    fun reject(res: Resources, code: String): String {
        val id = when (code) {
            "UNKNOWN_PLAYER" -> R.string.err_UNKNOWN_PLAYER
            "GAME_FINISHED" -> R.string.err_GAME_FINISHED
            "WRONG_PHASE" -> R.string.err_WRONG_PHASE
            "NOT_YOUR_TURN" -> R.string.err_NOT_YOUR_TURN
            "NOT_PLAYING" -> R.string.err_NOT_PLAYING
            "REPLAY_LIMIT" -> R.string.err_REPLAY_LIMIT
            "ALREADY_READY" -> R.string.err_ALREADY_READY
            "CARD_NOT_AVAILABLE" -> R.string.err_CARD_NOT_AVAILABLE
            "EMPTY_SELECTION" -> R.string.err_EMPTY_SELECTION
            "DUPLICATE_CARDS" -> R.string.err_DUPLICATE_CARDS
            "MIXED_RANKS" -> R.string.err_MIXED_RANKS
            "MULTIPLE_NOT_ALLOWED" -> R.string.err_MULTIPLE_NOT_ALLOWED
            "MUST_PLAY_FACE_DOWN" -> R.string.err_MUST_PLAY_FACE_DOWN
            "WRONG_SOURCE" -> R.string.err_WRONG_SOURCE
            "CARD_TOO_LOW" -> R.string.err_CARD_TOO_LOW
            "CARD_TOO_HIGH" -> R.string.err_CARD_TOO_HIGH
            "INVALID_BLIND_INDEX" -> R.string.err_INVALID_BLIND_INDEX
            "PILE_EMPTY" -> R.string.err_PILE_EMPTY
            "GAMBLE_NOT_ALLOWED" -> R.string.err_GAMBLE_NOT_ALLOWED
            "DRAW_PILE_EMPTY" -> R.string.err_DRAW_PILE_EMPTY
            "TOO_FEW_PLAYERS" -> R.string.err_TOO_FEW_PLAYERS
            "TOO_MANY_PLAYERS" -> R.string.err_TOO_MANY_PLAYERS
            "NOT_ENOUGH_CARDS" -> R.string.err_NOT_ENOUGH_CARDS
            "INVALID_HAND_SIZE" -> R.string.err_INVALID_HAND_SIZE
            "NOTHING_TO_CHALLENGE" -> R.string.err_NOTHING_TO_CHALLENGE
            "CANNOT_CHALLENGE_SELF" -> R.string.err_CANNOT_CHALLENGE_SELF
            "LAST_CARD_MUST_FIT" -> R.string.err_LAST_CARD_MUST_FIT
            "DOES_NOT_FIT" -> R.string.err_DOES_NOT_FIT
            "WRONG_SUIT" -> R.string.err_WRONG_SUIT
            "MUST_DRAW_OR_STACK" -> R.string.err_MUST_DRAW_OR_STACK
            "ONLY_DRAWN_CARD" -> R.string.err_ONLY_DRAWN_CARD
            "MUST_CHOOSE_SUIT" -> R.string.err_MUST_CHOOSE_SUIT
            "ALREADY_DRAWN" -> R.string.err_ALREADY_DRAWN
            "NOTHING_TO_DRAW" -> R.string.err_NOTHING_TO_DRAW
            "NOT_DRAWN_YET" -> R.string.err_NOT_DRAWN_YET
            "LAST_CARD_NOT_ALLOWED" -> R.string.err_LAST_CARD_NOT_ALLOWED
            "NOTHING_TO_CATCH" -> R.string.err_NOTHING_TO_CATCH
            "INVALID_JOKERS" -> R.string.err_INVALID_JOKERS
            "EXCHANGE_PENDING" -> R.string.err_EXCHANGE_PENDING
            "NOT_THE_WINNER" -> R.string.err_NOT_THE_WINNER
            "WRONG_COUNT" -> R.string.err_WRONG_COUNT
            "TOO_LOW" -> R.string.err_TOO_LOW
            "CANNOT_PASS_LEAD" -> R.string.err_CANNOT_PASS_LEAD
            "NOTHING_TO_GIVE" -> R.string.err_NOTHING_TO_GIVE
            "WRONG_GIVE_COUNT" -> R.string.err_WRONG_GIVE_COUNT
            "WRONG_PASS_COUNT" -> R.string.err_WRONG_PASS_COUNT
            "ALREADY_PASSED" -> R.string.err_ALREADY_PASSED
            "MUST_FOLLOW_SUIT" -> R.string.err_MUST_FOLLOW_SUIT
            "MUST_LEAD_CLUB" -> R.string.err_MUST_LEAD_CLUB
            "HEARTS_NOT_BROKEN" -> R.string.err_HEARTS_NOT_BROKEN
            "NO_POINTS_FIRST_TRICK" -> R.string.err_NO_POINTS_FIRST_TRICK
            "DUPLICATE_ACTION" -> R.string.err_DUPLICATE_ACTION
            "GAME_NOT_RUNNING" -> R.string.err_GAME_NOT_RUNNING
            "MALFORMED_ACTION" -> R.string.err_MALFORMED_ACTION
            "NO_GAME" -> R.string.err_NO_GAME
            "NOT_CONNECTED" -> R.string.err_NOT_CONNECTED
            "TIMEOUT" -> R.string.err_TIMEOUT
            "CONNECTION_LOST" -> R.string.err_CONNECTION_LOST
            "LEFT" -> R.string.conn_left
            else -> return res.getString(R.string.err_generic, code)
        }
        return res.getString(id)
    }

    fun joinRejected(res: Resources, reason: String): String = when (reason) {
        JoinRejectReason.LOBBY_FULL.name -> res.getString(R.string.reject_LOBBY_FULL)
        JoinRejectReason.GAME_IN_PROGRESS.name -> res.getString(R.string.reject_GAME_IN_PROGRESS)
        JoinRejectReason.VERSION_MISMATCH.name -> res.getString(R.string.reject_VERSION_MISMATCH)
        JoinRejectReason.HOST_CLOSING.name -> res.getString(R.string.reject_HOST_CLOSING)
        else -> res.getString(R.string.reject_generic, reason)
    }

    /** Text for a connection problem, or null when everything is fine. */
    fun connection(res: Resources, status: ConnectionStatus): String? = when (status) {
        ConnectionStatus.Local, ConnectionStatus.Connected, ConnectionStatus.Connecting -> null
        is ConnectionStatus.Reconnecting -> res.getString(R.string.conn_reconnecting, status.attempt, status.maxAttempts)
        is ConnectionStatus.Rejected -> joinRejected(res, status.reason)
        is ConnectionStatus.Lost -> when (status.reason) {
            LostReason.CONNECTION_LOST -> res.getString(R.string.conn_lost)
            LostReason.HOST_CLOSED -> res.getString(R.string.conn_host_closed)
            LostReason.KICKED -> res.getString(R.string.conn_kicked)
            LostReason.VERSION_MISMATCH -> res.getString(R.string.reject_VERSION_MISMATCH)
            LostReason.LEFT -> res.getString(R.string.conn_left)
            LostReason.GAME_SWITCHED -> res.getString(R.string.conn_game_switched)
            LostReason.HOST_MOVED -> res.getString(R.string.conn_host_moved)
        }
    }

    fun notice(res: Resources, notice: SessionNotice): String? = when (notice) {
        is SessionNotice.PlayerJoined -> res.getString(R.string.notice_joined, notice.name)
        is SessionNotice.SpectatorJoined -> res.getString(R.string.notice_spectator_joined, notice.name)
        is SessionNotice.PlayerLeft -> res.getString(R.string.notice_left, notice.name)
        is SessionNotice.PlayerDisconnected -> res.getString(R.string.notice_disconnected, notice.name)
        is SessionNotice.PlayerReconnected -> res.getString(R.string.notice_reconnected, notice.name)
        is SessionNotice.BotTookOver -> res.getString(R.string.notice_bot_took_over, notice.name)
        is SessionNotice.ActionRejected -> reject(res, notice.reason)
        is SessionNotice.ProtocolProblem -> res.getString(R.string.notice_protocol)
        is SessionNotice.AcceptingStopped -> res.getString(R.string.notice_accepting_stopped, notice.detail)
        is SessionNotice.GameSwitched -> res.getString(R.string.conn_game_switched)
    }

    /** [viewerId] lets sentences about the viewer read naturally ("betrapt jou" instead of "betrapt Jij"). */
    fun event(res: Resources, event: ZpEvent, viewerId: String? = null, nameOf: (String) -> String): String = when (event) {
        ZpEvent.SwapPhaseStarted -> res.getString(R.string.ev_swap_phase)
        is ZpEvent.Swapped -> res.getString(R.string.ev_swapped, nameOf(event.playerId))
        is ZpEvent.PlayerReady -> res.getString(R.string.ev_ready, nameOf(event.playerId))
        is ZpEvent.GameStarted -> event.startCard?.let {
            res.getString(R.string.ev_started_card, nameOf(event.startingPlayerId), card(it))
        } ?: res.getString(R.string.ev_started, nameOf(event.startingPlayerId))
        is ZpEvent.CardsPlayed -> res.getString(
            if (event.source == CardSource.FACE_UP) R.string.ev_played_open else R.string.ev_played,
            nameOf(event.playerId),
            cards(event.cards),
        )
        is ZpEvent.BlindRevealed -> res.getString(
            if (event.success) R.string.ev_blind_ok else R.string.ev_blind_fail,
            nameOf(event.playerId),
            card(event.card),
        )
        is ZpEvent.GambleRevealed -> res.getString(
            if (event.success) R.string.ev_gamble_ok else R.string.ev_gamble_fail,
            nameOf(event.playerId),
            card(event.card),
        )
        is ZpEvent.PileTaken -> res.getString(R.string.ev_picked_up, nameOf(event.playerId), event.count)
        is ZpEvent.PileBurned -> res.getQuantityString(
            if (event.reason == BurnReason.FOUR_OF_A_KIND) R.plurals.ev_burned_four else R.plurals.ev_burned,
            event.count,
            event.count,
        )
        is ZpEvent.PlayersSkipped -> res.getString(R.string.ev_skipped, event.skippedIds.joinToString(", ") { nameOf(it) })
        is ZpEvent.DirectionReversed -> res.getString(R.string.ev_reversed)
        is ZpEvent.ExtraTurn -> res.getString(R.string.ev_extra_turn, nameOf(event.playerId))
        is ZpEvent.StartsAfterPickUp -> res.getString(R.string.ev_starts_after_pickup, nameOf(event.playerId))
        is ZpEvent.DrawPileReshuffled -> res.getString(R.string.ev_reshuffled, event.count)
        is ZpEvent.PlayerFinished -> res.getString(R.string.ev_player_out, nameOf(event.playerId), event.position)
        is ZpEvent.CardsExchanged -> res.getString(R.string.ev_exchanged, nameOf(event.winnerId), nameOf(event.loserId))
        is ZpEvent.Resigned -> res.getString(R.string.ev_resigned, nameOf(event.playerId))
        is ZpEvent.GameOver -> res.getString(R.string.ev_game_over, nameOf(event.winnerId))
        is ZpEvent.CheatCaught -> if (event.cheaterId == viewerId) {
            res.getQuantityString(
                R.plurals.ev_cheat_caught_you,
                event.penaltyCount,
                nameOf(event.accuserId),
                cards(event.cards),
                event.penaltyCount,
            )
        } else res.getQuantityString(
            R.plurals.ev_cheat_caught,
            event.penaltyCount,
            nameOf(event.accuserId),
            nameOf(event.cheaterId),
            cards(event.cards),
            event.penaltyCount,
        )
        is ZpEvent.FalseAccusation -> res.getString(R.string.ev_false_accusation, nameOf(event.accuserId), event.penaltyCount)
    }

    fun effectLong(res: Resources, effect: ZpEffect, rules: ZpHouseRules): String = when (effect) {
        ZpEffect.NONE -> res.getString(R.string.effect_NONE)
        ZpEffect.RESET -> res.getString(R.string.effect_RESET)
        ZpEffect.LOWER -> res.getString(if (rules.lowerIncludesEqual) R.string.effect_LOWER else R.string.effect_LOWER_strict)
        ZpEffect.BURN -> res.getString(R.string.effect_BURN)
        ZpEffect.SKIP -> res.getString(R.string.effect_SKIP)
        ZpEffect.REVERSE -> res.getString(R.string.effect_REVERSE)
    }

    fun effectShort(res: Resources, effect: ZpEffect): String = res.getString(
        when (effect) {
            ZpEffect.NONE -> R.string.effect_short_NONE
            ZpEffect.RESET -> R.string.effect_short_RESET
            ZpEffect.LOWER -> R.string.effect_short_LOWER
            ZpEffect.BURN -> R.string.effect_short_BURN
            ZpEffect.SKIP -> R.string.effect_short_SKIP
            ZpEffect.REVERSE -> R.string.effect_short_REVERSE
        },
    )

    fun difficulty(res: Resources, difficulty: BotDifficulty): String = res.getString(
        when (difficulty) {
            BotDifficulty.EASY -> R.string.difficulty_easy
            BotDifficulty.NORMAL -> R.string.difficulty_normal
        },
    )

    fun preset(res: Resources, preset: ZpPreset?): String = res.getString(
        when (preset) {
            ZpPreset.CLASSIC -> R.string.preset_CLASSIC
            ZpPreset.TIS -> R.string.preset_TIS
            ZpPreset.PESTKOP -> R.string.preset_PESTKOP
            null -> R.string.preset_custom
        },
    )

    fun startRule(res: Resources, rule: StartRule): String = res.getString(
        when (rule) {
            StartRule.LOWEST_CARD -> R.string.start_LOWEST_CARD
            StartRule.RANDOM -> R.string.start_RANDOM
        },
    )
}
