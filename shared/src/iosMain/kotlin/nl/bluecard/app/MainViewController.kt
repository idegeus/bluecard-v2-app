package nl.bluecard.app

import androidx.compose.ui.window.ComposeUIViewController
import nl.bluecard.app.ios.IosPlatform
import nl.bluecard.app.res.Resources
import nl.bluecard.app.ui.BlueCardRoot
import platform.UIKit.UIViewController

/** One container for the whole app run, like the Android Application. */
private val container: AppContainer by lazy {
    AppContainer(IosPlatform()).also { Resources.useLanguage(it.platform.language.effective()) }
}

/** Entry point for the iOS app (called from Swift: `MainViewControllerKt.MainViewController()`). */
fun MainViewController(): UIViewController = ComposeUIViewController { BlueCardRoot(container) }
