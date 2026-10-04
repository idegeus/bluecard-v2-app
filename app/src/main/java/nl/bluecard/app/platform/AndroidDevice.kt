package nl.bluecard.app.platform

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

private val NOTIFICATION_VIBRATION: android.media.AudioAttributes = android.media.AudioAttributes.Builder()
    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** Vibration through the system vibrator (silently nothing on phones without one). */
class AndroidHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    override fun buzz() = vibrate(longArrayOf(0, 140, 70, 140, 70, 260), intArrayOf(0, 255, 0, 200, 0, 255))

    override fun tick() = vibrate(longArrayOf(0, 18), intArrayOf(0, 120))

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            // As a notification-type vibration: Android drops plain vibrations from an app that is not on screen
            // (a buzz may arrive with the screen just switched off).
            @Suppress("DEPRECATION")
            v.vibrate(effect, NOTIFICATION_VIBRATION)
        }
    }
}

/** Shake strength from the accelerometer: the length of the acceleration minus gravity. */
class AndroidMotion(context: Context) : MotionSensor {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val available: Boolean get() = accelerometer != null

    override fun shaking(): Flow<Float> {
        val sensor = accelerometer ?: return emptyFlow()
        val manager = sensors ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val (x, y, z) = event.values
                    trySend(abs(sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH))
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { manager.unregisterListener(listener) }
        }
    }
}

internal fun encodePngAndroid(image: ImageBitmap): ByteArray? = encodeAndroid(image, android.graphics.Bitmap.CompressFormat.PNG, 100)

internal fun encodeAndroid(image: ImageBitmap, format: android.graphics.Bitmap.CompressFormat, quality: Int): ByteArray? = runCatching {
    val out = ByteArrayOutputStream()
    image.asAndroidBitmap().compress(format, quality, out)
    out.toByteArray()
}.getOrNull()

/** Saves the picture in the cache (shared through the FileProvider) and opens the share sheet. */
internal fun shareImageAndroid(context: Context, png: ByteArray, text: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "bluecard-win.png")
    file.writeBytes(png)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
}
