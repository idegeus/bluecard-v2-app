package nl.bluecard.app.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import nl.bluecard.app.android
import nl.bluecard.app.ui.components.appContainer

/**
 * A full-width banner at the bottom of a screen (start and end-of-game screen). It takes no space until an
 * ad has actually loaded, so without a connection or consent the screen looks exactly as before.
 */
@Composable
fun AdBanner(adUnitId: String, modifier: Modifier = Modifier) {
    val ads = appContainer().android.adsManager
    val canShow by ads.canShowAds.collectAsStateWithLifecycle()
    if (!canShow) return
    val context = LocalContext.current
    val widthPx = LocalWindowInfo.current.containerSize.width
    val widthDp = with(LocalDensity.current) { widthPx.toDp().value.toInt() }
    var loaded by remember { mutableStateOf(false) }
    val adView = remember(widthDp, adUnitId) {
        AdView(context).apply {
            this.adUnitId = adUnitId
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = false
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    // Until loaded the view is kept at zero height (it must stay attached to load).
    AndroidView(
        factory = { adView },
        modifier = modifier
            .fillMaxWidth()
            .then(if (loaded) Modifier.padding(top = 8.dp) else Modifier.height(0.dp)),
    )
}
