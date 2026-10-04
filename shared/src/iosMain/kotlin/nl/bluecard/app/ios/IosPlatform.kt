package nl.bluecard.app.ios

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import nl.bluecard.app.platform.FileStore
import nl.bluecard.app.platform.GameTransport
import nl.bluecard.app.platform.Haptics
import nl.bluecard.app.platform.MotionSensor
import nl.bluecard.app.platform.LanguageSettings
import nl.bluecard.app.platform.Platform
import nl.bluecard.app.platform.PlatformKind
import nl.bluecard.app.platform.PlatformUi
import nl.bluecard.app.platform.SoundPlayer
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.preferredLanguages
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import kotlin.experimental.ExperimentalNativeApi

/** The iOS side of the shared app: files in Application Support, AVAudio sounds, Multipeer Connectivity. */
class IosPlatform : Platform {
    override val kind = PlatformKind.IOS

    override val versionName: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "1.0"

    @OptIn(ExperimentalNativeApi::class)
    override val isDebug: Boolean = kotlin.native.Platform.isDebugBinary

    override val dataDir: String = run {
        val base = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String)
            ?: NSTemporaryDirectoryFallback
        val dir = "$base/BlueCard"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        dir
    }

    override val files: FileStore = IosFileStore(dataDir)
    override val transport: GameTransport = MultipeerTransport()
    override val sounds: SoundPlayer = IosSounds()
    override val ads = NoAds
    override val language: LanguageSettings = IosLanguage
    override val ui: PlatformUi = IosPlatformUi()

    /** iOS stops background work anyway; a game simply continues when the app comes back. */
    override fun keepSessionAlive(active: Boolean, hosting: Boolean) = Unit

    override fun decodeImage(bytes: ByteArray): ImageBitmap? =
        runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    override fun encodePng(image: ImageBitmap): ByteArray? = encodePngIos(image)

    override fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray? = encodeJpegIos(image, quality)

    override fun shareImage(png: ByteArray, text: String) = shareImageIos(png, text)

    override val haptics: Haptics = IosHaptics
    override val motion: MotionSensor = IosMotion()

    override fun log(tag: String, message: String, error: Throwable?) {
        NSLog("%@", "[$tag] $message" + (error?.let { " ($it)" } ?: ""))
    }

    private companion object {
        const val NSTemporaryDirectoryFallback = "/tmp"
    }
}

/** Small text files, written atomically. */
private class IosFileStore(private val dir: String) : FileStore {
    private fun path(name: String) = "$dir/$name"

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        if (!NSFileManager.defaultManager.fileExistsAtPath(path(name))) return@withContext null
        NSString.stringWithContentsOfFile(path(name), NSUTF8StringEncoding, null) ?: throw IOException("cannot read $name")
    }

    override suspend fun write(name: String, text: String) = withContext(Dispatchers.IO) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val ok = NSString.create(string = text).writeToFile(path(name), atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!ok) throw IOException("cannot write $name")
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { NSFileManager.defaultManager.removeItemAtPath(path(name), null) }
    }

    override suspend fun exists(name: String): Boolean = withContext(Dispatchers.IO) { NSFileManager.defaultManager.fileExistsAtPath(path(name)) }
}

/** The chosen language is stored here; "" follows the phone. Texts switch at once (no restart needed). */
private object IosLanguage : LanguageSettings {
    private const val KEY = "app_language"

    override fun current(): String = NSUserDefaults.standardUserDefaults.stringForKey(KEY).orEmpty()

    override fun set(tag: String) {
        NSUserDefaults.standardUserDefaults.setObject(tag, KEY)
    }

    override fun effective(): String = current().ifEmpty { NSLocale.preferredLanguages.firstOrNull() as? String ?: "en" }
}
