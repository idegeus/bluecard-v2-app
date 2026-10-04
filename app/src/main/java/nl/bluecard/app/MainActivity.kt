package nl.bluecard.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nl.bluecard.app.android.BuildConfig
import nl.bluecard.app.platform.AndroidLanguage
import nl.bluecard.app.ui.BlueCardRoot
import nl.bluecard.app.ui.debug.SimulatedScreen
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AndroidLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Our screens draw their own backgrounds behind the navigation bar; no grey scrim needed.
            window.isNavigationBarContrastEnforced = false
        }
        val container = (application as BlueCardApp).container
        container.android.activity = WeakReference(this)
        takeJoinRequest(intent)
        // Ad consent (EEA/UK); the form only shows when no valid choice has been made yet.
        container.android.adsManager.gatherConsent(this)
        // Debug builds: `--ei sim_w 320 --ei sim_h 568` renders the app as a small phone (see SimulatedScreen).
        val simWidth = if (BuildConfig.DEBUG) intent.getIntExtra("sim_w", 0) else 0
        setContent {
            if (simWidth > 0) {
                SimulatedScreen(simWidth, intent.getIntExtra("sim_h", 0)) { BlueCardRoot(container) }
            } else {
                BlueCardRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        takeJoinRequest(intent)
    }

    /** Opened from "a table nearby": join that table as soon as it is found. */
    private fun takeJoinRequest(intent: android.content.Intent?) {
        val host = intent?.getStringExtra(nl.bluecard.app.nearby.NearbyTableAlerts.EXTRA_JOIN_HOST) ?: return
        intent.removeExtra(nl.bluecard.app.nearby.NearbyTableAlerts.EXTRA_JOIN_HOST)
        val container = (application as BlueCardApp).container
        if (container.sessions.active.value != null) return
        container.pendingJoin.value = PendingJoin(host, intent.getStringExtra(nl.bluecard.app.nearby.NearbyTableAlerts.EXTRA_JOIN_GAME))
    }
}
