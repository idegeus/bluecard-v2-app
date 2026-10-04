package nl.bluecard.app.ios

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import nl.bluecard.app.platform.Haptics
import nl.bluecard.app.platform.MotionSensor
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSData
import platform.Foundation.NSOperationQueue
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import kotlin.math.abs
import kotlin.math.sqrt

object IosHaptics : Haptics {
    override fun buzz() {
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred()
    }

    override fun tick() {
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
    }
}

/** Shake strength from the accelerometer (which reports in g): |a| − 1 g, in m/s². */
@OptIn(ExperimentalForeignApi::class)
class IosMotion : MotionSensor {
    private val manager = CMMotionManager()

    override val available: Boolean get() = manager.accelerometerAvailable

    override fun shaking(): Flow<Float> {
        if (!available) return emptyFlow()
        return callbackFlow {
            manager.accelerometerUpdateInterval = 1.0 / 30
            manager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
                data?.acceleration?.useContents {
                    trySend((abs(sqrt(x * x + y * y + z * z) - 1.0) * GRAVITY).toFloat())
                }
            }
            awaitClose { manager.stopAccelerometerUpdates() }
        }
    }

    private companion object {
        const val GRAVITY = 9.81
    }
}

internal fun encodePngIos(image: ImageBitmap): ByteArray? =
    runCatching { Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes }.getOrNull()

internal fun encodeJpegIos(image: ImageBitmap, quality: Int): ByteArray? =
    runCatching { Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)?.bytes }.getOrNull()

@OptIn(ExperimentalForeignApi::class)
internal fun shareImageIos(png: ByteArray, text: String) {
    val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.convert()) }
    val image = UIImage.imageWithData(data) ?: return
    val controller = UIActivityViewController(activityItems = listOf(image, text), applicationActivities = null)
    val top = topViewController() ?: return
    // iPad shows the sheet as a popover, which needs an anchor.
    controller.popoverPresentationController?.sourceView = top.view
    top.presentViewController(controller, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
        ?: UIApplication.sharedApplication.keyWindow
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
