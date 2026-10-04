package nl.bluecard.app.session

import nl.bluecard.app.android.R as AndroidR
import nl.bluecard.app.res.Resources
import nl.bluecard.app.ui.text.GameTexts
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.bluecard.app.MainActivity
import nl.bluecard.app.R

/**
 * In a Bluetooth game the others wait for you. When it becomes your turn while the app is in the background,
 * the phone vibrates and shows a notification, repeated a few times as long as you don't react. Coming back to
 * the app (or the turn passing, e.g. to a bot that took over) clears it.
 */
class TurnAlerts(private val context: Context, private val scope: CoroutineScope, private val sessions: SessionManager) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        createChannel()
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
        val waitingForMe = sessions.active.flatMapLatest { active ->
            if (active == null || active is ActiveSession.Local) {
                flowOf(null)
            } else {
                active.port.view.map { view ->
                    val summary = view?.let { active.game.binding.summarizeAny(it) }
                    if (summary?.myTurn == true) active.game else null
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            combine(waitingForMe, foreground) { game, inFront -> game.takeIf { !inFront } }
                .distinctUntilChanged()
                .collectLatest { game ->
                    if (game == null) {
                        cancel()
                        return@collectLatest
                    }
                    // A few reminders, then stop nagging; collectLatest cancels this as soon as you react.
                    repeat(REMINDERS) { attempt ->
                        alert(game, attempt)
                        delay(REMINDER_INTERVAL_MS)
                    }
                }
        }
    }

    private fun alert(game: GameKind, attempt: Int) {
        vibrate()
        if (!canNotify()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val gameName = GameTexts.gameName(Resources, game)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AndroidR.drawable.ic_notification)
            .setContentTitle(Resources.getString(if (attempt == 0) R.string.turn_alert_title else R.string.turn_alert_title_again))
            .setContentText(Resources.getString(R.string.turn_alert_text, gameName))
            .setColor(ContextCompat.getColor(context, AndroidR.color.notification_accent))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()
        notify(notification)
    }

    @SuppressLint("MissingPermission") // checked in canNotify()
    private fun notify(notification: android.app.Notification) =
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

    private fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180, 120, 320), -1))
    }

    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, Resources.getString(R.string.turn_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = Resources.getString(R.string.turn_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 120, 180, 120, 320)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "your_turn"
        const val NOTIFICATION_ID = 1002
        const val REMINDERS = 4
        const val REMINDER_INTERVAL_MS = 30_000L
    }
}
