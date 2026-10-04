package nl.bluecard.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Debug builds only: renders the app as if the screen were [widthDp] × [heightDp] (a higher density and a shorter
 * frame), to check layouts for small phones without changing any system setting.
 * Start with `adb shell am start -n nl.bluecard.app/.MainActivity --ei sim_w 320 --ei sim_h 568`.
 */
@Composable
fun SimulatedScreen(widthDp: Int, heightDp: Int, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val real = LocalDensity.current
        val scale = maxWidth.value / widthDp
        val density = Density(real.density * scale, real.fontScale)
        val frameHeight = if (heightDp > 0) (heightDp * scale).dp.coerceAtMost(maxHeight) else maxHeight
        Box(Modifier.fillMaxWidth().height(frameHeight).align(Alignment.TopCenter)) {
            CompositionLocalProvider(LocalDensity provides density) { content() }
        }
    }
}
