package nl.bluecard.app.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import nl.bluecard.app.platform.PlatformUi
import nl.bluecard.app.platform.PHOTO_MAX_SIDE
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIGraphicsImageRendererFormat
import nl.bluecard.app.ui.screens.NearbyJoinScreen
import nl.bluecard.app.ui.screens.NearbyPlayScreen
import nl.bluecard.app.ui.screens.NearbyVisibilityPanel
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

class IosPlatformUi : PlatformUi {
    @Composable
    override fun MultiplayerScreen(onBack: () -> Unit, onJoin: () -> Unit) = NearbyPlayScreen(onBack, onJoin)

    @Composable
    override fun JoinScreen(onBack: () -> Unit, onGameStarted: () -> Unit) = NearbyJoinScreen(onBack, onGameStarted)

    @Composable
    override fun HostVisibilityPanel() = NearbyVisibilityPanel()

    /** iPhones have no back button; leaving goes through the on-screen ✕ / back arrow. */
    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

    @Composable
    override fun rememberPhotoPicker(onPicked: (ImageBitmap?) -> Unit): () -> Unit {
        val callback by rememberUpdatedState(onPicked)
        val delegate = remember { PickerDelegate { callback(it) } }
        return {
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.imagesFilter
            config.selectionLimit = 1
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }

    @Composable
    override fun rememberIsChangingConfigurations(): () -> Boolean = { false }

    @Composable
    override fun KeepScreenOn(enabled: Boolean) {
        DisposableEffect(enabled) {
            UIApplication.sharedApplication.idleTimerDisabled = enabled
            onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
        }
    }

    /** The status bar style is fixed to light text in Info.plist (the app is drawn on dark felt). */
    @Composable
    override fun SystemBarIcons(darkBackground: Boolean) = Unit
}

private fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

/** Gets the picked photo, upright and scaled down, as an [ImageBitmap] for the crop screen. */
private class PickerDelegate(private val onResult: (ImageBitmap?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return onResult(null)
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val photo = data?.let(::photoFrom)
            dispatch_async(dispatch_get_main_queue()) { onResult(photo) }
        }
    }
}

/**
 * Redraws the photo at most [PHOTO_MAX_SIDE] pixels: drawing a UIImage applies its orientation, so sideways camera
 * photos come out upright.
 */
@OptIn(ExperimentalForeignApi::class)
private fun photoFrom(data: NSData): ImageBitmap? {
    val image = UIImage.imageWithData(data) ?: return null
    val (w, h) = image.size.useContents { width to height }
    if (w <= 0.0 || h <= 0.0) return null
    val scale = minOf(1.0, PHOTO_MAX_SIDE / maxOf(w, h))
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(w * scale, h * scale), format = format)
    val upright = renderer.imageWithActions { _ -> image.drawInRect(CGRectMake(0.0, 0.0, w * scale, h * scale)) }
    val jpeg = UIImageJPEGRepresentation(upright, 0.9) ?: return null
    val bytes = ByteArray(jpeg.length.toInt())
    if (bytes.isEmpty()) return null
    bytes.usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
    return runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}
