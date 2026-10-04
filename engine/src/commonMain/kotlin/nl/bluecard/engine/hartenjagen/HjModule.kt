package nl.bluecard.engine.hartenjagen

import kotlinx.serialization.KSerializer
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.core.GameInfo
import nl.bluecard.engine.core.GameModule
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.model.PlayerInfo
import kotlin.random.Random

/** Plugs Hartenjagen into the generic [GameModule] contract. */
object HjModule : GameModule<HjHouseRules, HjGameState, HjAction, HjPlayerView> {

    const val GAME_ID = "hartenjagen"

    override val info = GameInfo(
        id = GAME_ID,
        displayName = "Hartenjagen",
        minPlayers = HjRules.MIN_PLAYERS,
        maxPlayers = HjRules.MAX_PLAYERS,
        rulesVersion = 1,
    )

    override val configSerializer: KSerializer<HjHouseRules> = HjHouseRules.serializer()
    override val stateSerializer: KSerializer<HjGameState> = HjGameState.serializer()
    override val actionSerializer: KSerializer<HjAction> = HjAction.serializer()
    override val viewSerializer: KSerializer<HjPlayerView> = HjPlayerView.serializer()

    override fun defaultConfig(): HjHouseRules = HjHouseRules()

    override fun validateSetup(playerCount: Int, config: HjHouseRules): String? = HjRules.validateSetup(playerCount)?.name

    override fun newGame(players: List<PlayerInfo>, config: HjHouseRules, seed: Long): HjGameState = HjEngine.newGame(players, config, seed)

    override fun apply(state: HjGameState, playerId: String, action: HjAction): ActionResult<HjGameState> =
        HjEngine.apply(state, playerId, action)

    override fun view(state: HjGameState, viewerId: String): HjPlayerView = HjViews.create(state, viewerId)

    override fun pendingActors(state: HjGameState): Set<String> = HjViews.pendingActors(state)

    override fun stateVersion(state: HjGameState): Long = state.version

    override fun result(state: HjGameState): GameResult? = HjEngine.result(state)

    override fun chooseBotAction(view: HjPlayerView, difficulty: BotDifficulty, random: Random): HjAction =
        HjBot.chooseAction(view, difficulty, random)

    override fun concede(state: HjGameState, playerId: String): HjGameState = HjEngine.concede(state, playerId)

    override fun players(state: HjGameState): List<PlayerInfo> = state.players.map { PlayerInfo(it.id, it.name) }

    /** A full trick stays on the table a moment longer so everybody sees who took it. */
    override fun botDelayMs(state: HjGameState, baseMs: Long): Long =
        if (state.trick.isEmpty() && state.lastTrick.isNotEmpty()) baseMs * 2 else baseMs
}
