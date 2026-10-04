package nl.bluecard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.bluecard.app.session.isTableDisplay
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.R
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.theme.TableColors

/** "Join as the table": a tablet (or phone) in the middle that shows the game big for everybody. */
@Composable
fun TableDisplayCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TableColors.FeltDark.copy(alpha = 0.55f))
            .border(1.dp, TableColors.Ink.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📺", fontSize = 30.sp)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.table_display_join), color = TableColors.OnFelt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.table_display_join_detail), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = TableColors.OnFelt, fontSize = 26.sp)
    }
}

/** True when this device joined as the table display. */
@Composable
fun isTableDisplay(): Boolean {
    val active by appContainer().sessions.active.collectAsStateWithLifecycle()
    return active.isTableDisplay
}

/**
 * The game as the table display shows it: the players along the top, the middle of the table as big as the screen
 * allows (a tablet in the middle of the table), and the latest move and whose turn it is below. No hand, no buttons.
 */
@Composable
fun TableDisplayLayout(top: @Composable () -> Unit, center: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        top()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The middle is laid out at phone size and scaled up, so cards stay sharp and in proportion.
            val scale = minOf(maxWidth / CENTER_WIDTH, maxHeight / CENTER_HEIGHT).coerceIn(1f, MAX_SCALE)
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) { center() }
        }
        bottom()
    }
}

private val CENTER_WIDTH = 380.dp
private val CENTER_HEIGHT = 260.dp
private const val MAX_SCALE = 2.6f
