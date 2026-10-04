package nl.bluecard.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import nl.bluecard.app.AppContainer
import nl.bluecard.app.platform.PlatformUi

/** The app's dependency container, provided by [nl.bluecard.app.ui.BlueCardRoot]. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("no AppContainer provided") }

/** Access to the app's dependency container from composables. */
@Composable
fun appContainer(): AppContainer = LocalAppContainer.current

/** Platform-specific screens and hooks (Android / iOS). */
@Composable
fun platformUi(): PlatformUi = LocalAppContainer.current.platform.ui
