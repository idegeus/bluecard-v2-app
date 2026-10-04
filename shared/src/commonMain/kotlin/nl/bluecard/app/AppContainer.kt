package nl.bluecard.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.bluecard.app.data.MatchRepository
import nl.bluecard.app.data.SavedGameRepository
import nl.bluecard.app.data.SettingsRepository
import nl.bluecard.app.platform.Platform
import nl.bluecard.app.platform.PlatformAds
import nl.bluecard.app.platform.SoundPlayer
import nl.bluecard.app.session.SessionManager
import nl.bluecard.app.settings.AppSettings
import nl.bluecard.app.stats.Leaderboard
import nl.bluecard.app.ui.components.AvatarPhotos
import okio.Path.Companion.toPath

/** Manual dependency container; one instance per process, created by the Android Application / iOS app. */
class AppContainer(val platform: Platform) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Same file as Android's `preferencesDataStore("settings")`, so existing settings are kept. */
    private val settingsStore = PreferenceDataStoreFactory.createWithPath(
        produceFile = { "${platform.dataDir}/datastore/settings.preferences_pb".toPath() },
    )
    val settingsRepository = SettingsRepository(settingsStore, platform)
    val savedGames = SavedGameRepository(platform.files, platform)
    val matches = MatchRepository(platform.files, appScope, platform)
    val sessions = SessionManager(platform, appScope, settingsRepository, savedGames, matches)
    val ads: PlatformAds get() = platform.ads

    /** Settings as a hot flow so screens get a value immediately. */
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    val sounds: SoundPlayer = platform.sounds.also { player -> player.enabled = { settings.value.soundEffects } }

    /** A table to join as soon as it is found (the "table nearby" notification was tapped). */
    val pendingJoin = kotlinx.coroutines.flow.MutableStateFlow<PendingJoin?>(null)

    /** How often you won (all games, also against bots): unlocks skins. */
    val myWins: StateFlow<Int> = combine(settings, matches.records) { s, records -> Leaderboard.mine(records, s.deviceId).wins }
        .stateIn(appScope, SharingStarted.Eagerly, 0)

    /** Everything you did so far, for unlocking skins and emoji. */
    val myProgress: StateFlow<nl.bluecard.app.stats.Progress> =
        combine(settings, matches.records) { s, records -> nl.bluecard.app.stats.Progress.of(records, s.deviceId) }
            .stateIn(appScope, SharingStarted.Eagerly, nl.bluecard.app.stats.Progress())

    init {
        AvatarPhotos.decoder = platform::decodeImage
        // The public device id is needed for the leaderboard from the very first game.
        appScope.launch { settingsRepository.ensureToken() }
    }
}

/** Join the table of [hostName] (playing [gameId]) as soon as the search finds it. */
data class PendingJoin(
    val hostName: String,
    val gameId: String?,
    /** When the request came in (wall clock): the search gives up [TIMEOUT_MS] later. */
    val requestedAt: Long = nl.bluecard.app.platform.nowMillis(),
) {
    val giveUpAt: Long get() = requestedAt + TIMEOUT_MS

    companion object {
        const val TIMEOUT_MS = 90_000L
    }
}
