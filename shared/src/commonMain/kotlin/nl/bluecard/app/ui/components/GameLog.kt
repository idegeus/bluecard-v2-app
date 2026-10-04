package nl.bluecard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card

/** How a line in the game history stands out. */
enum class LogTone(val accent: Color?) {
    NORMAL(null),
    GOOD(Color(0xFF66BB6A)),
    BAD(Color(0xFF90A4AE)),
    FIRE(Color(0xFFFF7043)),
    CHEAT(Color(0xFFE53935)),
    ALERT(Color(0xFFFFA000)),
    GOLD(Color(0xFFFFC107)),
}

/** One line of the game history, independent of the game. */
data class LogItem(
    val seq: Long,
    val text: String,
    /** Who did it (for the avatar), or null for things that just happen. */
    val actorName: String? = null,
    val actorIsBot: Boolean = false,
    val actorAvatar: String? = null,
    val cards: List<Card> = emptyList(),
    val tone: LogTone = LogTone.NORMAL,
)

/**
 * The game history as a bottom sheet: a timeline, newest first, with who did what and the cards involved,
 * and a second tab with the special cards and the full rules of this table ([rules]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLogSheet(
    title: String,
    historyTab: String,
    specialsTab: String,
    items: List<LogItem>,
    onDismiss: () -> Unit,
    rules: (@Composable ColumnScope.() -> Unit)? = null,
    specials: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TableColors.FeltDark,
        contentColor = TableColors.OnFelt,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TableColors.OnFelt)
            Spacer(Modifier.size(10.dp))
            FeltSegmented(listOf(0, 1), tab, label = { if (it == 0) historyTab else specialsTab }, onSelect = { tab = it })
            Spacer(Modifier.size(10.dp))
            if (tab == 0) {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items.asReversed(), key = { it.seq }) { item -> LogRow(item) }
                    item { Spacer(Modifier.size(24.dp)) }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    specials()
                    if (rules != null) {
                        FeltDivider()
                        rules()
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(item: LogItem) {
    val accent = item.tone.accent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent?.copy(alpha = 0.16f) ?: Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Timeline: avatar of the actor, or a small dot for things that just happen.
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            if (item.actorName != null) {
                PlayerAvatar(item.actorName, size = 30.dp, isBot = item.actorIsBot, avatar = item.actorAvatar)
            } else {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent ?: TableColors.OnFeltMuted))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            item.text,
            color = if (accent != null && item.tone != LogTone.BAD) TableColors.OnFelt else TableColors.OnFelt.copy(alpha = 0.9f),
            fontWeight = if (item.tone == LogTone.NORMAL || item.tone == LogTone.BAD) FontWeight.Normal else FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (item.cards.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), modifier = Modifier.padding(start = 6.dp)) {
                item.cards.take(4).forEach { PlayingCard(it, 26.dp) }
            }
        }
    }
}
