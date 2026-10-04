package nl.bluecard.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow
import nl.bluecard.multiplayer.transport.Link
import nl.bluecard.multiplayer.transport.LinkAcceptor

/** What the shared app needs from Android or iOS. One instance per process, handed to the AppContainer. */
interface Platform {
    val kind: PlatformKind
    val versionName: String
    val isDebug: Boolean

    /** Absolute directory for the app's own data (settings, saved game, match history). */
    val dataDir: String
    val files: FileStore

    /** Connections to nearby phones (Bluetooth on Android, Multipeer Connectivity on iOS); null = none. */
    val transport: GameTransport?
    val sounds: SoundPlayer
    val ads: PlatformAds
    val language: LanguageSettings
    val ui: PlatformUi

    /** Keeps a multiplayer session running while the app is in the background (Android foreground service). */
    fun keepSessionAlive(active: Boolean, hosting: Boolean)

    fun decodeImage(bytes: ByteArray): ImageBitmap?

    /** PNG bytes of [image] (for sharing), null when that fails. */
    fun encodePng(image: ImageBitmap): ByteArray?

    /** JPEG bytes of [image] ([quality] 0–100), e.g. for a photo avatar. */
    fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray?

    /** Opens the system share sheet (Instagram, WhatsApp, …) with a PNG picture and a short text. */
    fun shareImage(png: ByteArray, text: String)

    val haptics: Haptics
    val motion: MotionSensor

    fun log(tag: String, message: String, error: Throwable? = null)
}

enum class PlatformKind { ANDROID, IOS }

/** Picked photos are scaled down to this longest side before cropping. */
const val PHOTO_MAX_SIDE = 1280

interface Haptics {
    /** A strong rattle: somebody buzzes you to hurry up. */
    fun buzz()

    /** A short tick, e.g. while shuffling. */
    fun tick()
}

interface MotionSensor {
    /** False when the phone has no accelerometer; the shuffle then works by tapping. */
    val available: Boolean

    /**
     * While collected: how hard the phone is being shaken, in m/s² above gravity, about 30 times a second.
     * Completes immediately when not [available].
     */
    fun shaking(): kotlinx.coroutines.flow.Flow<Float>
}

/** Small text files in [Platform.dataDir], written atomically. */
interface FileStore {
    suspend fun read(name: String): String?
    suspend fun write(name: String, text: String)
    suspend fun delete(name: String)
    suspend fun exists(name: String): Boolean
}

interface GameTransport {
    /** True while the radio can be used (Bluetooth switched on). */
    val ready: StateFlow<Boolean>

    /**
     * Starts listening for players; the returned acceptor hands out their connections. Throws IOException.
     * [hostName] and [gameId] are announced to nearby phones where the platform supports that (iOS).
     */
    fun openServer(hostName: String, gameId: String): LinkAcceptor

    /** Connects to a host found by the platform's join screen ([address] comes from there). */
    suspend fun connect(address: String): Link

    /** Tables announced nearby (home screen, and the shared join screen on iOS); null when the platform cannot search. */
    val discovery: NearbyDiscovery? get() = null
}

/** A table announced by a phone nearby. [address] is what [GameTransport.connect] needs. */
data class NearbyTable(
    val address: String,
    val hostName: String,
    val gameId: String?,
    /** A game is running there: joining means watching until the next round. */
    val inGame: Boolean = false,
    val players: Int? = null,
    val maxPlayers: Int? = null,
)

interface NearbyDiscovery {
    /** The tables found so far; searches while collected. */
    fun tables(): kotlinx.coroutines.flow.Flow<List<NearbyTable>>

    /** False when searching cannot work right now (radio off, no permission); the app then offers to set it up. */
    val ready: Boolean get() = true
}

enum class Sound { CHEAT, FALSE_ALARM, FORGOT, BUZZ, SHUFFLE, DRUMROLL, BUSTED }

interface SoundPlayer {
    /** Plays [sound] unless sounds are off ([enabled]) or the phone is silenced. */
    fun play(sound: Sound)
    var enabled: () -> Boolean
}

enum class AdSlot { HOME, RESULT }

interface PlatformAds {
    /** The user must be able to change their ad consent (a button in the settings). */
    val privacyOptionsRequired: StateFlow<Boolean>
    fun showPrivacyOptions()

    /** Whether this showing of the start screen may carry a banner (see HomeAdPolicy). */
    fun takeHomeScreenTurn(): Boolean

    @Composable
    fun Banner(slot: AdSlot, modifier: Modifier)
}

interface LanguageSettings {
    /** The chosen language tag, "" = follow the phone. */
    fun current(): String

    /** Stores the choice; the platform makes the app follow it (Android may restart the screen). */
    fun set(tag: String)

    /** The language the texts should be shown in now (chosen or the phone's). */
    fun effective(): String
}

/** Screens and composable hooks that differ per platform. */
interface PlatformUi {
    /** Join a multiplayer table nearby, as a player or as the table display (Bluetooth / nearby phones). */
    @Composable
    fun MultiplayerScreen(onBack: () -> Unit, onJoin: () -> Unit)

    /** Find and join a table, then wait in the lobby until the game starts. */
    @Composable
    fun JoinScreen(onBack: () -> Unit, onGameStarted: () -> Unit)

    /**
     * Returns an action that gets ready to open a table (Android: asks for the Bluetooth permissions when they are
     * missing) and then calls [onReady] — also when they are refused, because a table with bots works without.
     */
    @Composable
    fun rememberHostPreparer(onReady: () -> Unit): () -> Unit = onReady

    /** Host lobby panel about being findable for other phones (may be empty). */
    @Composable
    fun HostVisibilityPanel()

    /**
     * Small button for the host during a game / between rounds: makes the table findable again for phones that want to
     * watch or join (Android: discoverable for 5 minutes; nothing on iOS, where the table is always announced).
     */
    @Composable
    fun HostVisibilityButton() {}

    @Composable
    fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

    /**
     * Returns a launcher that lets the user pick a photo. [onPicked] gets it upright (camera rotation applied) and
     * scaled down to at most [PHOTO_MAX_SIDE] pixels, or null when nothing was picked.
     */
    @Composable
    fun rememberPhotoPicker(onPicked: (ImageBitmap?) -> Unit): () -> Unit

    /** True while the screen is only being re-created (rotation), so per-showing logic is not repeated. */
    @Composable
    fun rememberIsChangingConfigurations(): () -> Boolean

    /** Keeps the display on while [enabled] (during a game). */
    @Composable
    fun KeepScreenOn(enabled: Boolean)

    /** Status/navigation bar icon colour for the current screen. */
    @Composable
    fun SystemBarIcons(darkBackground: Boolean)
}
