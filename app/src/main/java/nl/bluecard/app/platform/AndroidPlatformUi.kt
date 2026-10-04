package nl.bluecard.app.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.bluecard.app.ui.screens.BluetoothScreen
import nl.bluecard.app.ui.screens.BluetoothVisibilityPanel
import nl.bluecard.app.ui.screens.JoinScreen
import java.io.IOException

class AndroidPlatformUi : PlatformUi {
    @Composable
    override fun MultiplayerScreen(onBack: () -> Unit, onJoin: () -> Unit) = BluetoothScreen(onBack, onJoin)

    @Composable
    override fun JoinScreen(onBack: () -> Unit, onGameStarted: () -> Unit) = JoinScreen(onLeave = onBack, onGameStarted = onGameStarted)

    @Composable
    override fun rememberHostPreparer(onReady: () -> Unit): () -> Unit = nl.bluecard.app.ui.screens.rememberHostPermissions(onReady)

    @Composable
    override fun HostVisibilityPanel() = BluetoothVisibilityPanel()

    @Composable
    override fun HostVisibilityButton() = nl.bluecard.app.ui.screens.BluetoothVisibilityButton()

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

    @Composable
    override fun rememberPhotoPicker(onPicked: (ImageBitmap?) -> Unit): () -> Unit {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { onPicked(photoFromUri(context, uri)?.asImageBitmap()) }
        }
        return { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }

    @Composable
    override fun rememberIsChangingConfigurations(): () -> Boolean {
        val activity = LocalActivity.current
        return { activity?.isChangingConfigurations == true }
    }

    @Composable
    override fun KeepScreenOn(enabled: Boolean) {
        val view = LocalView.current
        DisposableEffect(enabled) {
            view.keepScreenOn = enabled
            onDispose { view.keepScreenOn = false }
        }
    }

    @Composable
    override fun SystemBarIcons(darkBackground: Boolean) {
        val view = LocalView.current
        val systemDark = isSystemInDarkTheme()
        // Applied when the screen is resumed, so during navigation transitions the destination on top always wins.
        LifecycleResumeEffect(darkBackground, systemDark) {
            val window = view.context.findActivityOrNull()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                val lightBars = !darkBackground && !systemDark
                controller.isAppearanceLightStatusBars = lightBars
                controller.isAppearanceLightNavigationBars = lightBars
            }
            onPauseOrDispose { }
        }
    }
}

private fun Context.findActivityOrNull(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Loads the picked photo scaled down to about [PHOTO_MAX_SIDE] and turned upright: camera photos are often stored
 * sideways with an EXIF orientation, which BitmapFactory ignores.
 */
// The framework ExifInterface's known issues are on Android 6 and older; minSdk is 26.
@android.annotation.SuppressLint("ExifInterface")
private suspend fun photoFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_SIDE) sample *= 2
        val source = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null
        val orientation = resolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
        } ?: android.media.ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        if (matrix.isIdentity) source else Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }
}
