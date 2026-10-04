package nl.bluecard.engine.pesten

import kotlinx.serialization.KSerializer
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.core.GameInfo
import nl.bluecard.engine.core.GameModule
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.model.PlayerInfo
import kotlin.random.Random

/** Plugs Pesten into the generic [GameModule] contract. */
object PsModule : GameModule<PsHouseRules, PsGameState, PsAction, PsPlayerView> {

    const val GAME_ID = "pesten"

    override val info = GameInfo(
        id = GAME_ID,
        displayName = "Pesten",
        minPlayers = PsRules.MIN_PLAYERS,
        maxPlayers = PsRules.MAX_PLAYERS,
        rulesVersion = 4,
    )

    override val configSerializer: KSerializer<PsHouseRules> = PsHouseRules.serializer()
    override val stateSerializer: KSerializer<PsGameState> = PsGameState.serializer()
    override val actionSerializer: KSerializer<PsAction> = PsAction.serializer()
    override val viewSerializer: KSerializer<PsPlayerView> = PsPlayerView.serializer()

    override fun defaultConfig(): PsHouseRules = PsHouseRules()

    override fun validateSetup(playerCount: Int, config: PsHouseRules): String? =
        PsRules.validateSetup(playerCount, config)?.name

    override fun newGame(players: List<PlayerInfo>, config: PsHouseRules, seed: Long): PsGameState =
        PsEngine.newGame(players, config, seed)

    override fun newGame(players: List<PlayerInfo>, config: PsHouseRules, seed: Long, previous: GameResult?): PsGameState =
        PsEngine.newGame(players, config, seed, previous)

    override fun apply(state: PsGameState, playerId: String, action: PsAction): ActionResult<PsGameState> =
        PsEngine.apply(state, playerId, action)

    override fun view(state: PsGameState, viewerId: String): PsPlayerView = PsViews.create(state, viewerId)

    override fun pendingActors(state: PsGameState): Set<String> = PsViews.pendingActors(state)

    override fun stateVersion(state: PsGameState): Long = state.version

    override fun result(state: PsGameState): GameResult? = PsEngine.result(state)

    override fun chooseBotAction(view: PsPlayerView, difficulty: BotDifficulty, random: Random): PsAction =
        PsBot.chooseAction(view, difficulty, random)

    override fun concede(state: PsGameState, playerId: String): PsGameState = PsEngine.concede(state, playerId)

    override fun players(state: PsGameState): List<PlayerInfo> = state.players.map { PlayerInfo(it.id, it.name) }

    /** Give people time to call "Vals!" or "Vergeten!" before a bot moves on. */
    override fun botDelayMs(state: PsGameState, baseMs: Long): Long {
        val fresh = state.lastPlay?.let { it.logSeqAfter == state.nextLogSeq } == true
        return if (fresh || state.forgottenId != null) maxOf(baseMs * 2, CHALLENGE_WINDOW_MS) else baseMs
    }

    override fun tick(state: PsGameState, nowMs: Long): PsGameState = PsEngine.tick(state, nowMs)

    override fun nextTickAt(state: PsGameState): Long? = PsEngine.nextTickAt(state)

    /** Shortest time people get to call "Vals!" or "Vergeten!" before a bot moves on. */
    const val CHALLENGE_WINDOW_MS = 1_600L
}
