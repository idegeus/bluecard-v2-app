package nl.bluecard.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.bluecard.app.AppContainer
import nl.bluecard.app.res.Resources
import nl.bluecard.app.ui.components.LocalAppContainer
import nl.bluecard.app.ui.navigation.AppNavHost
import nl.bluecard.app.ui.text.CardLabels
import nl.bluecard.app.ui.theme.BlueCardTheme
import nl.bluecard.app.ui.theme.Skins
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.ui.components.SessionOverlay

/** The whole app UI, the same on Android and iOS. */
@Composable
fun BlueCardRoot(container: AppContainer) {
    // Texts follow the chosen app language (or the phone's).
    remember(container) {
        Resources.useLanguage(container.platform.language.effective())
        CardLabels.init(Resources)
    }
    CompositionLocalProvider(LocalAppContainer provides container) {
        val settings by container.settings.collectAsStateWithLifecycle()
        val progress by container.myProgress.collectAsStateWithLifecycle()
        val active by container.sessions.active.collectAsStateWithLifecycle()
        // At someone else's table you see the host's card backs and cloth.
        val hostStyle by remember(active) {
            (active as? ActiveSession.Joined)?.client?.lobby?.map { it?.style } ?: flowOf(null)
        }.collectAsStateWithLifecycle(null)
        // Skins recolour every screen; locked skins fall back to the defaults.
        LaunchedEffect(settings.cardBackSkin, settings.tableSkin, progress, hostStyle) {
            val style = hostStyle
            if (style != null) Skins.applyHost(style.cardBack, style.table) else Skins.apply(settings.cardBackSkin, settings.tableSkin, progress)
        }
        // A buzz rattles the whole screen.
        val shake = remember { Animatable(0f) }
        BlueCardTheme {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = shake.value * density }) {
                AppNavHost()
                SessionOverlay(container, shake)
            }
        }
    }
}
