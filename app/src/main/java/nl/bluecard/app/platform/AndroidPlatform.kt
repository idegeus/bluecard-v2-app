package nl.bluecard.app.platform

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import nl.bluecard.app.ads.AdBanner
import nl.bluecard.app.ads.AdsManager
import nl.bluecard.app.android.BuildConfig
import nl.bluecard.app.bluetooth.AdapterState
import nl.bluecard.app.bluetooth.BluetoothController
import nl.bluecard.app.service.MultiplayerService
import nl.bluecard.app.sound.SoundEffects
import nl.bluecard.multiplayer.transport.Link
import nl.bluecard.multiplayer.transport.LinkAcceptor
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale

/** The Android side of the shared app: Bluetooth, AdMob, SoundPool, files in the app's private storage. */
class AndroidPlatform(context: Context, scope: CoroutineScope) : Platform {
    private val appContext = context.applicationContext

    /** The visible activity (for consent forms and restarts); set by MainActivity. */
    var activity: WeakReference<Activity> = WeakReference(null)

    val bluetooth = BluetoothController(appContext)
    val adsManager = AdsManager(appContext, scope)

    override val kind = PlatformKind.ANDROID
    override val versionName: String = BuildConfig.VERSION_NAME
    override val isDebug: Boolean = BuildConfig.DEBUG
    override val dataDir: String = appContext.filesDir.absolutePath
    override val files: FileStore = AndroidFileStore(appContext.filesDir)

    override val transport: GameTransport? = if (bluetooth.isSupported) BluetoothTransport(appContext, bluetooth, scope) else null
    override val sounds: SoundPlayer = SoundEffects(appContext)
    override val ads: PlatformAds = AndroidAds(adsManager) { activity.get() }
    override val language: LanguageSettings = AndroidLanguage(appContext) { activity.get() }
    override val ui: PlatformUi = AndroidPlatformUi()

    override fun keepSessionAlive(active: Boolean, hosting: Boolean) {
        if (active) MultiplayerService.start(appContext, hosting) else MultiplayerService.stop(appContext)
    }

    override fun decodeImage(bytes: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

    override fun encodePng(image: ImageBitmap): ByteArray? = encodePngAndroid(image)

    override fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray? =
        encodeAndroid(image, android.graphics.Bitmap.CompressFormat.JPEG, quality)

    override fun shareImage(png: ByteArray, text: String) {
        shareImageAndroid(activity.get() ?: appContext, png, text)
    }

    override val haptics: Haptics = AndroidHaptics(appContext)
    override val motion: MotionSensor = AndroidMotion(appContext)

    override fun log(tag: String, message: String, error: Throwable?) {
        if (error != null) Log.w(tag, message, error) else Log.d(tag, message)
    }
}

/** Files in the app's private directory, written through [AtomicFile] (same files as before the shared module). */
private class AndroidFileStore(private val dir: File) : FileStore {
    private fun file(name: String) = AtomicFile(File(dir, name))

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        val f = file(name)
        if (!f.baseFile.exists()) null else f.readFully().toString(Charsets.UTF_8)
    }

    override suspend fun write(name: String, text: String) = withContext(Dispatchers.IO) {
        val f = file(name)
        val stream = f.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            f.finishWrite(stream)
        } catch (e: IOException) {
            f.failWrite(stream)
            throw e
        }
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.IO) { file(name).delete() }

    override suspend fun exists(name: String): Boolean = withContext(Dispatchers.IO) { file(name).baseFile.exists() }
}

private class BluetoothTransport(private val context: Context, private val bluetooth: BluetoothController, scope: CoroutineScope) : GameTransport {
    override val ready: StateFlow<Boolean> =
        bluetooth.state.map { it == AdapterState.ON }.stateIn(scope, SharingStarted.Eagerly, bluetooth.state.value == AdapterState.ON)

    override fun openServer(hostName: String, gameId: String): LinkAcceptor = bluetooth.openServer()

    override suspend fun connect(address: String): Link = bluetooth.connect(address)

    override val discovery: NearbyDiscovery = BluetoothNearbyDiscovery(context, bluetooth)
}

private class AndroidAds(private val manager: AdsManager, private val activity: () -> Activity?) : PlatformAds {
    override val privacyOptionsRequired: StateFlow<Boolean> get() = manager.privacyOptionsRequired

    override fun showPrivacyOptions() {
        activity()?.let(manager::showPrivacyOptions)
    }

    override fun takeHomeScreenTurn(): Boolean = manager.takeHomeScreenTurn()

    @Composable
    override fun Banner(slot: AdSlot, modifier: Modifier) {
        AdBanner(
            when (slot) {
                AdSlot.HOME -> BuildConfig.ADMOB_HOME_BANNER_ID
                AdSlot.RESULT -> BuildConfig.ADMOB_BANNER_ID
            },
            modifier,
        )
    }
}

/**
 * Android 13+ keeps the per-app language itself (also shown in the system settings) and restarts the activity;
 * older versions use the choice stored here, applied by [wrap] when the activity is recreated.
 */
class AndroidLanguage(private val context: Context, private val activity: () -> Activity?) : LanguageSettings {
    override fun current(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language.orEmpty()
    } else {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
    }

    override fun set(tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, tag) }
            activity()?.recreate()
        }
    }

    override fun effective(): String = current().ifEmpty { Locale.getDefault().language }

    companion object {
        private const val PREFS = "app_language"
        private const val KEY = "tag"

        /** Android 12 and older: a context in the chosen language (used by the activity). */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            if (tag.isEmpty()) return base
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags(tag))
            return base.createConfigurationContext(config)
        }
    }
}
