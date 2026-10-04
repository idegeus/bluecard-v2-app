package nl.bluecard.engine.zweedspesten

import kotlinx.serialization.KSerializer
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.core.GameInfo
import nl.bluecard.engine.core.GameModule
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.model.PlayerInfo
import kotlin.random.Random

/** Plugs Zweeds Pesten into the generic [GameModule] contract. */
object ZpModule : GameModule<ZpHouseRules, ZpGameState, ZpAction, ZpPlayerView> {

    const val GAME_ID = "zweeds-pesten"
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 5

    override val info = GameInfo(
        id = GAME_ID,
        displayName = "Zweeds Pesten",
        minPlayers = MIN_PLAYERS,
        maxPlayers = MAX_PLAYERS,
        rulesVersion = 7,
    )

    override val configSerializer: KSerializer<ZpHouseRules> = ZpHouseRules.serializer()
    override val stateSerializer: KSerializer<ZpGameState> = ZpGameState.serializer()
    override val actionSerializer: KSerializer<ZpAction> = ZpAction.serializer()
    override val viewSerializer: KSerializer<ZpPlayerView> = ZpPlayerView.serializer()

    override fun defaultConfig(): ZpHouseRules = ZpHouseRules()

    override fun validateSetup(playerCount: Int, config: ZpHouseRules): String? =
        ZpRules.validateSetup(playerCount, config)?.name

    override fun newGame(players: List<PlayerInfo>, config: ZpHouseRules, seed: Long): ZpGameState =
        ZpEngine.newGame(players, config, seed)

    override fun newGame(players: List<PlayerInfo>, config: ZpHouseRules, seed: Long, previous: GameResult?): ZpGameState =
        ZpEngine.newGame(players, config, seed, previous)

    override fun apply(state: ZpGameState, playerId: String, action: ZpAction): ActionResult<ZpGameState> =
        ZpEngine.apply(state, playerId, action)

    override fun view(state: ZpGameState, viewerId: String): ZpPlayerView = ZpViews.create(state, viewerId)

    override fun pendingActors(state: ZpGameState): Set<String> = ZpViews.pendingActors(state)

    override fun stateVersion(state: ZpGameState): Long = state.version

    override fun result(state: ZpGameState): GameResult? = ZpEngine.result(state)

    override fun chooseBotAction(view: ZpPlayerView, difficulty: BotDifficulty, random: Random): ZpAction =
        ZpBot.chooseAction(view, difficulty, random)

    override fun concede(state: ZpGameState, playerId: String): ZpGameState = ZpEngine.concede(state, playerId)

    override fun players(state: ZpGameState): List<PlayerInfo> = state.players.map { PlayerInfo(it.id, it.name) }

    /**
     * Right after cards were laid without rule enforcement, bots wait longer (and never less than
     * [CHALLENGE_WINDOW_MS], whatever the bot speed) so people can call "Vals!".
     */
    override fun botDelayMs(state: ZpGameState, baseMs: Long): Long {
        // After a blind card everybody watches it turn over (the reveal takes 2.4 s) before the next move.
        val revealed = state.log.lastOrNull { it.event !is ZpEvent.PileTaken && it.event !is ZpEvent.PlayerFinished }?.event
        if (revealed is ZpEvent.BlindRevealed) return maxOf(baseMs, BLIND_REVEAL_MS)
        // Right after a play (nothing else happened yet) people get a moment to call "Vals!" before a bot moves on.
        val fresh = state.lastPlay?.let { it.logSeqAfter == state.nextLogSeq } == true
        return if (fresh) maxOf(baseMs * 2, CHALLENGE_WINDOW_MS) else baseMs
    }

    override fun tick(state: ZpGameState, nowMs: Long): ZpGameState = ZpEngine.tick(state, nowMs)

    override fun nextTickAt(state: ZpGameState): Long? = ZpEngine.nextTickAt(state)

    const val CHALLENGE_WINDOW_MS = 1_600L
    const val BLIND_REVEAL_MS = 3_200L
}
