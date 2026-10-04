package nl.bluecard.engine.presidenten

import kotlinx.serialization.KSerializer
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.core.GameInfo
import nl.bluecard.engine.core.GameModule
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.model.PlayerInfo
import kotlin.random.Random

/** Plugs Presidenten into the generic [GameModule] contract. */
object PrModule : GameModule<PrHouseRules, PrGameState, PrAction, PrPlayerView> {

    const val GAME_ID = "presidenten"

    override val info = GameInfo(
        id = GAME_ID,
        displayName = "Presidenten",
        minPlayers = PrRules.MIN_PLAYERS,
        maxPlayers = PrRules.MAX_PLAYERS,
        rulesVersion = 1,
    )

    override val configSerializer: KSerializer<PrHouseRules> = PrHouseRules.serializer()
    override val stateSerializer: KSerializer<PrGameState> = PrGameState.serializer()
    override val actionSerializer: KSerializer<PrAction> = PrAction.serializer()
    override val viewSerializer: KSerializer<PrPlayerView> = PrPlayerView.serializer()

    override fun defaultConfig(): PrHouseRules = PrHouseRules()

    override fun validateSetup(playerCount: Int, config: PrHouseRules): String? = PrRules.validateSetup(playerCount, config)?.name

    override fun newGame(players: List<PlayerInfo>, config: PrHouseRules, seed: Long): PrGameState =
        PrEngine.newGame(players, config, seed)

    override fun newGame(players: List<PlayerInfo>, config: PrHouseRules, seed: Long, previous: GameResult?): PrGameState =
        PrEngine.newGame(players, config, seed, previous)

    override fun apply(state: PrGameState, playerId: String, action: PrAction): ActionResult<PrGameState> =
        PrEngine.apply(state, playerId, action)

    override fun view(state: PrGameState, viewerId: String): PrPlayerView = PrViews.create(state, viewerId)

    override fun pendingActors(state: PrGameState): Set<String> = PrViews.pendingActors(state)

    override fun stateVersion(state: PrGameState): Long = state.version

    override fun result(state: PrGameState): GameResult? = PrEngine.result(state)

    override fun chooseBotAction(view: PrPlayerView, difficulty: BotDifficulty, random: Random): PrAction =
        PrBot.chooseAction(view, difficulty, random)

    override fun concede(state: PrGameState, playerId: String): PrGameState = PrEngine.concede(state, playerId)

    override fun players(state: PrGameState): List<PlayerInfo> = state.players.map { PlayerInfo(it.id, it.name) }

    /** A finished trick stays visible a moment longer before the next lead. */
    override fun botDelayMs(state: PrGameState, baseMs: Long): Long =
        if (state.plays.isEmpty() && state.lastTrick.isNotEmpty()) baseMs * 2 else baseMs
}
