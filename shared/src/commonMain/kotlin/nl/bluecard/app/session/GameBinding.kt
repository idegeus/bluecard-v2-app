package nl.bluecard.app.session

import kotlinx.serialization.json.JsonElement
import nl.bluecard.app.settings.AppSettings
import nl.bluecard.engine.core.GameModule
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.hartenjagen.HjAction
import nl.bluecard.engine.hartenjagen.HjGameState
import nl.bluecard.engine.hartenjagen.HjHouseRules
import nl.bluecard.engine.hartenjagen.HjModule
import nl.bluecard.engine.hartenjagen.HjPhase
import nl.bluecard.engine.hartenjagen.HjPlayerView
import nl.bluecard.engine.pesten.PsPhase
import nl.bluecard.engine.presidenten.PrAction
import nl.bluecard.engine.presidenten.PrGameState
import nl.bluecard.engine.presidenten.PrHouseRules
import nl.bluecard.engine.presidenten.PrModule
import nl.bluecard.engine.presidenten.PrPhase
import nl.bluecard.engine.presidenten.PrPlayerView
import nl.bluecard.engine.zweedspesten.ZpPhase
import nl.bluecard.engine.pesten.PsAction
import nl.bluecard.engine.pesten.PsGameState
import nl.bluecard.engine.pesten.PsHouseRules
import nl.bluecard.engine.pesten.PsModule
import nl.bluecard.engine.pesten.PsPlayerView
import nl.bluecard.engine.zweedspesten.ZpAction
import nl.bluecard.engine.zweedspesten.ZpGameState
import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.engine.zweedspesten.ZpModule
import nl.bluecard.engine.zweedspesten.ZpPlayerView
import nl.bluecard.multiplayer.protocol.ProtocolJson
import nl.bluecard.multiplayer.session.ClientSession
import nl.bluecard.multiplayer.session.HostSession
import nl.bluecard.multiplayer.session.PlayerPort

/** The card games this app offers. [id] is the engine's game id, also used on the wire. */
enum class GameKind(val id: String) {
    ZWEEDS_PESTEN(ZpModule.GAME_ID),
    PESTEN(PsModule.GAME_ID),
    PRESIDENTEN(PrModule.GAME_ID),
    HARTENJAGEN(HjModule.GAME_ID),
    ;

    val binding: GameBinding<*, *, *, *>
        get() = when (this) {
            ZWEEDS_PESTEN -> GameBinding.Zweeds
            PESTEN -> GameBinding.Pesten
            PRESIDENTEN -> GameBinding.Presidenten
            HARTENJAGEN -> GameBinding.Hartenjagen
        }

    val minPlayers: Int get() = binding.module.info.minPlayers
    val maxPlayers: Int get() = binding.module.info.maxPlayers

    /** Whether the game has the "Vals!" mechanic (rules not enforced). */
    val hasCheating: Boolean get() = this == ZWEEDS_PESTEN || this == PESTEN

    companion object {
        fun fromId(id: String?): GameKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Ties a [GameKind] to its engine module and to where its house rules live in the settings. Everything the
 * session layer does for "some game" goes through here, so it stays free of per-game code.
 */
sealed class GameBinding<C : Any, S : Any, A : Any, V : Any>(
    val kind: GameKind,
    val module: GameModule<C, S, A, V>,
) {
    abstract fun rulesFrom(settings: AppSettings): C

    /** The few facts screens outside the game table need from a view. */
    abstract fun summarize(view: V): ViewSummary

    @Suppress("UNCHECKED_CAST")
    fun summarizeAny(view: Any): ViewSummary = summarize(view as V)

    fun encodeState(state: S): JsonElement = ProtocolJson.json.encodeToJsonElement(module.stateSerializer, state)

    fun decodeState(json: JsonElement): S = ProtocolJson.json.decodeFromJsonElement(module.stateSerializer, json)

    /** Sends the house rules from the settings to a lobby of this game. */
    suspend fun applyRules(host: HostSession<*, *, *, *>, settings: AppSettings) {
        @Suppress("UNCHECKED_CAST")
        (host as HostSession<C, S, A, V>).updateConfig(rulesFrom(settings))
    }

    data object Zweeds : GameBinding<ZpHouseRules, ZpGameState, ZpAction, ZpPlayerView>(GameKind.ZWEEDS_PESTEN, ZpModule) {
        override fun rulesFrom(settings: AppSettings): ZpHouseRules = settings.houseRules

        override fun summarize(view: ZpPlayerView) = ViewSummary(
            viewerId = view.viewerId,
            finished = view.phase == ZpPhase.FINISHED,
            result = view.result,
            // In the swap phase everybody has to act at the same time.
            myTurn = view.isMyTurn || (view.phase == ZpPhase.SWAPPING && view.legal.canReady),
            currentPlayerId = view.currentPlayerId.takeIf { view.phase == ZpPhase.PLAYING },
            cardsLeft = view.players.associate { it.id to it.handCount + it.faceUp.size + it.faceDownCount },
        )
    }

    data object Pesten : GameBinding<PsHouseRules, PsGameState, PsAction, PsPlayerView>(GameKind.PESTEN, PsModule) {
        override fun rulesFrom(settings: AppSettings): PsHouseRules = settings.pestenRules

        override fun summarize(view: PsPlayerView) =
            ViewSummary(
                view.viewerId,
                view.phase == PsPhase.FINISHED,
                view.result,
                myTurn = view.isMyTurn,
                currentPlayerId = view.currentPlayerId.takeIf { view.phase == PsPhase.PLAYING },
                cardsLeft = view.players.associate { it.id to it.handCount },
            )
    }

    data object Presidenten : GameBinding<PrHouseRules, PrGameState, PrAction, PrPlayerView>(GameKind.PRESIDENTEN, PrModule) {
        override fun rulesFrom(settings: AppSettings): PrHouseRules = settings.presidentRules

        override fun summarize(view: PrPlayerView) = ViewSummary(
            view.viewerId,
            view.phase == PrPhase.FINISHED,
            view.result,
            myTurn = view.isMyTurn || view.legal.giveCount > 0,
            currentPlayerId = view.currentPlayerId.takeIf { view.phase == PrPhase.PLAYING },
            cardsLeft = view.players.associate { it.id to it.handCount },
        )
    }

    data object Hartenjagen : GameBinding<HjHouseRules, HjGameState, HjAction, HjPlayerView>(GameKind.HARTENJAGEN, HjModule) {
        override fun rulesFrom(settings: AppSettings): HjHouseRules = settings.heartsRules

        override fun summarize(view: HjPlayerView) = ViewSummary(
            view.viewerId,
            view.phase == HjPhase.FINISHED,
            view.result,
            myTurn = view.isMyTurn || view.legal.passCount > 0,
            currentPlayerId = view.currentPlayerId.takeIf { view.phase == HjPhase.PLAYING },
            cardsLeft = view.players.associate { it.id to it.handCount },
        )
    }
}

/** Game-independent facts about a player's view. */
data class ViewSummary(
    val viewerId: String,
    val finished: Boolean,
    val result: GameResult?,
    val myTurn: Boolean = false,
    /** Whose turn it is (null outside the normal playing phase), e.g. for the buzzer. */
    val currentPlayerId: String? = null,
    /** Cards each player still has to get rid of (hand, and in Zweeds Pesten the table cards too). */
    val cardsLeft: Map<String, Int> = emptyMap(),
) {
    /** A new round is running (the host pressed "Opnieuw spelen"). */
    val running: Boolean get() = !finished && result == null
}

typealias ZpPort = PlayerPort<ZpPlayerView, ZpAction>
typealias PsPort = PlayerPort<PsPlayerView, PsAction>
typealias PrPort = PlayerPort<PrPlayerView, PrAction>
typealias HjPort = PlayerPort<HjPlayerView, HjAction>
typealias AnyHost = HostSession<*, *, *, *>
typealias AnyClient = ClientSession<*, *, *, *>
