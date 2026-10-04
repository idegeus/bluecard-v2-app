package nl.bluecard.app.session

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import nl.bluecard.app.MainActivity
import nl.bluecard.app.R
import nl.bluecard.app.res.Resources
import nl.bluecard.multiplayer.session.SocialKind
import nl.bluecard.app.android.R as AndroidR

/**
 * A buzz has to reach you with the phone in your pocket and the screen off. The table itself shakes and vibrates,
 * but Android ignores vibrations from an app that is not on screen; so then the buzz comes as a notification on
 * its own channel, with a strong vibration pattern and the buzzer sound, which also wakes a dark screen.
 */
class BuzzAlerts(private val context: Context, private val scope: CoroutineScope, private val sessions: SessionManager) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        createChannel()
        scope.launch(Dispatchers.Main) {
            sessions.active.flatMapLatest { active -> active?.port?.social ?: emptyFlow() }.collect { event ->
                val active = sessions.active.value ?: return@collect
                if (event.kind != SocialKind.BUZZ || event.toId != active.port.playerId.value) return@collect
                if (onScreen()) return@collect
                alert(event.fromName)
            }
        }
    }

    /** The app is in front and the screen is on: the table shows the buzz itself. */
    private fun onScreen(): Boolean {
        val inFront = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive != false
        return inFront && screenOn
    }

    private fun alert(fromName: String) {
        if (!canNotify()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AndroidR.drawable.ic_notification)
            .setContentTitle(Resources.getString(R.string.social_buzzed_you, fromName))
            .setColor(ContextCompat.getColor(context, AndroidR.color.notification_accent))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Pre-O phones take these from the notification instead of the channel.
            .setVibrate(PATTERN)
            .setSound(buzzSound())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        notify(notification)
    }

    @SuppressLint("MissingPermission") // checked in canNotify()
    private fun notify(notification: android.app.Notification) =
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun buzzSound(): Uri =
        (ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + AndroidR.raw.sfx_buzz).toUri()

    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, Resources.getString(R.string.buzz_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = Resources.getString(R.string.buzz_channel_desc)
            enableVibration(true)
            vibrationPattern = PATTERN
            setSound(
                buzzSound(),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "buzz"
        const val NOTIFICATION_ID = 1004
        const val TIMEOUT_MS = 60_000L
        val PATTERN = longArrayOf(0, 300, 120, 300, 120, 600)
    }
}
