package nl.bluecard.app.service

import nl.bluecard.app.android.R as AndroidR
import nl.bluecard.app.res.Resources
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import nl.bluecard.app.MainActivity
import nl.bluecard.app.R

/**
 * Foreground service that only exists to keep the process (and thus the Bluetooth connections owned by
 * the SessionManager) alive while the app is in the background during a Bluetooth game.
 */
class MultiplayerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    // The manifest does declare foregroundServiceType="connectedDevice"; lint misses it in debug builds, which add
    // a second manifest (Android Auto, src/debug).
    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hosting = intent?.getBooleanExtra(EXTRA_HOSTING, false) ?: false
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(hosting), type)
        } catch (e: RuntimeException) {
            // E.g. ForegroundServiceStartNotAllowedException or missing permission: the game still works
            // while the app is in the foreground, so just stop the service.
            Log.w(TAG, "cannot start foreground service", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(hosting: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(AndroidR.drawable.ic_notification)
            .setContentTitle(Resources.getString(if (hosting) R.string.notif_hosting else R.string.notif_joined))
            .setContentText(Resources.getString(R.string.notif_text))
            .setColor(ContextCompat.getColor(this, AndroidR.color.notification_accent))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "MultiplayerService"
        private const val CHANNEL_ID = "bluetooth_game"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_HOSTING = "hosting"

        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                Resources.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = Resources.getString(R.string.notif_channel_desc) }
            manager.createNotificationChannel(channel)
        }

        fun start(context: Context, hosting: Boolean) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MultiplayerService::class.java).putExtra(EXTRA_HOSTING, hosting),
                )
            } catch (e: RuntimeException) {
                Log.w(TAG, "cannot start service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MultiplayerService::class.java))
        }
    }
}
