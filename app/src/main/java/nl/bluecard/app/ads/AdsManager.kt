package nl.bluecard.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** No banner on the first start screen after [QUIET_MS] without seeing it: a calm start. */
object HomeAdPolicy {
    const val QUIET_MS = 30 * 60 * 1000L

    fun showBanner(lastShownAt: Long?, now: Long): Boolean = lastShownAt != null && now - lastShownAt in 0..QUIET_MS
}

/**
 * Advertising: a banner on the start screen and on the end-of-game screen, nothing during play. Before any ad is requested the
 * user's consent is gathered with Google's User Messaging Platform (required in the EEA/UK). Without a
 * connection or consent the game simply shows no ad; playing never depends on it.
 */
class AdsManager(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val initialized = AtomicBoolean(false)
    private val prefs = appContext.getSharedPreferences("ads", Context.MODE_PRIVATE)

    private val _canShowAds = MutableStateFlow(false)

    /** True once consent allows ads and the SDK has been started. */
    val canShowAds: StateFlow<Boolean> = _canShowAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** The user must be able to change their ad consent later (shown in the settings). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /**
     * Updates the consent status and, when needed, asks for consent. Call from the main activity on every
     * start; the form only appears when there is no valid decision yet.
     */
    fun gatherConsent(activity: Activity) {
        // A decision from an earlier session is enough to start right away.
        if (consent.canRequestAds()) startSdk()
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                    refresh()
                }
            },
            { error ->
                // Offline, for example: try again on the next start.
                Log.w(TAG, "Consent update: ${error.message}")
                refresh()
            },
        )
    }

    /**
     * Whether this showing of the start screen may carry a banner. Call once per showing: the first start
     * screen after a quiet period ([HomeAdPolicy]) stays ad-free, so people get a calm start.
     */
    fun takeHomeScreenTurn(now: Long = System.currentTimeMillis()): Boolean {
        val last = prefs.getLong(KEY_LAST_HOME, 0L).takeIf { it > 0L }
        prefs.edit { putLong(KEY_LAST_HOME, now) }
        return HomeAdPolicy.showBanner(last, now)
    }

    /** Opens the consent form again so the user can change their choice. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.message}")
            refresh()
        }
    }

    private fun refresh() {
        _privacyOptionsRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (consent.canRequestAds()) startSdk() else _canShowAds.value = false
    }

    private fun startSdk() {
        if (initialized.getAndSet(true)) {
            _canShowAds.value = true
            return
        }
        // Our own phones always get test ads, also with the real ad IDs: no invalid clicks on live ads.
        MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setTestDeviceIds(TEST_DEVICES).build())
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext) { _canShowAds.value = true }
        }
    }

    private companion object {
        const val TAG = "BlueCardAds"
        const val KEY_LAST_HOME = "lastHomeScreenAt"

        /** Hashed ad IDs of the development phones (from the "setTestDeviceIds" hint in logcat). */
        val TEST_DEVICES = listOf(
            "794AAB5ECFADF110F810255F1B832D99", // moto e13 ZY22JW5JL9
            "E4DB06AF3FD5F4557D1609C042404877", // moto e13 ZY22JW5JZ4
        )
    }
}
