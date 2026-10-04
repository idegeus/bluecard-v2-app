package nl.bluecard.app.ui.screens

import nl.bluecard.app.platform.nowMillis
import nl.bluecard.app.ui.components.CountdownBar
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.platform.NearbyTable
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.ui.components.PlayerAvatar
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.components.popWhen
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.multiplayer.session.ConnectionStatus

/**
 * Tables nearby, right on the start screen: the phone keeps looking while this is in view, and a table that opens
 * shows up by itself. One tap joins it (or watches along when a game is running).
 *
 * [onJoined] is called after joining; [onSetUp] opens the multiplayer screen when searching cannot work yet (radio
 * off or no permission).
 */
@Composable
fun NearbyTablesPanel(onJoined: () -> Unit, onSetUp: () -> Unit, onFailed: (String) -> Unit) {
    val container = appContainer()
    val discovery = container.platform.transport?.discovery ?: return
    var ready by remember { mutableStateOf(discovery.ready) }
    LaunchedEffect(discovery) {
        while (true) {
            ready = discovery.ready
            delay(READY_POLL_MS)
        }
    }
    val shape = RoundedCornerShape(16.dp)
    if (!ready) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(TableColors.Shade)
                .clickable(onClick = onSetUp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔍", modifier = Modifier.padding(end = 10.dp))
            Text(stringResource(R.string.nearby_setup), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val tables by remember(discovery) { discovery.tables() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var joining by remember { mutableStateOf<String?>(null) }
    // The table we are connecting to because the notification asked for it (shown in the countdown).
    var joiningWanted by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.join_failed)
    val pending by container.pendingJoin.collectAsStateWithLifecycle()
    val notFound = pending?.let { stringResource(R.string.nearby_join_not_found, it.hostName) }
    fun join(table: NearbyTable, wanted: Boolean = false) {
        joining = table.address
        if (wanted) joiningWanted = table.hostName
        scope.launch {
            val game = GameKind.fromId(table.gameId) ?: GameKind.ZWEEDS_PESTEN
            val status = container.sessions.join(table.address, table.hostName, game, asTable = false)
            joining = null
            joiningWanted = null
            if (status == ConnectionStatus.Connected) onJoined() else onFailed(failed)
        }
    }
    // Opened from the notification: join that table as soon as it shows up (or give up after a while).
    LaunchedEffect(pending, tables) {
        val want = pending ?: return@LaunchedEffect
        // The name in the BLE signal may be shortened.
        val match = tables.firstOrNull { it.hostName.startsWith(want.hostName) && (want.gameId == null || it.gameId == want.gameId) }
        if (match != null && joining == null) {
            container.pendingJoin.value = null
            join(match, wanted = true)
        }
    }
    LaunchedEffect(pending) {
        val want = pending ?: return@LaunchedEffect
        delay((want.giveUpAt - nowMillis()).coerceAtLeast(0L))
        if (container.pendingJoin.value == want) {
            container.pendingJoin.value = null
            notFound?.let(onFailed)
        }
    }
    // A table that just opened gets a little bounce (and a tick).
    var newest by remember { mutableStateOf<String?>(null) }
    var known by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(tables) {
        val fresh = tables.map { it.address }.filter { it !in known }
        if (fresh.isNotEmpty() && known.isNotEmpty()) {
            newest = fresh.first()
            container.platform.haptics.tick()
        }
        known = known + tables.map { it.address }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TableColors.Shade)
            .padding(12.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nearby_title), color = TableColors.OnFelt, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = TableColors.OnFeltMuted)
        }
        pending?.let { want ->
            // Opened from the notification: what we are doing and how long we keep trying.
            CountdownBar(
                label = stringResource(R.string.nearby_joining_wanted, want.hostName),
                startedAt = want.requestedAt,
                endsAt = want.giveUpAt,
            )
            Text(
                stringResource(R.string.cancel),
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.End).clickable { container.pendingJoin.value = null }.padding(6.dp),
            )
        }
        joiningWanted?.let { host ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nearby_join_found, host), color = TableColors.Highlight, fontWeight = FontWeight.SemiBold)
            }
        }
        if (tables.isEmpty() && pending == null) {
            Text(stringResource(R.string.nearby_searching), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
        }
        for (table in tables) {
            TableRow(table, enabled = joining == null, busy = joining == table.address, pop = newest == table.address) { join(table) }
        }
    }
}

@Composable
private fun TableRow(table: NearbyTable, enabled: Boolean, busy: Boolean, pop: Boolean, onJoin: () -> Unit) {
    val res = LocalResources.current
    Row(
        Modifier
            .fillMaxWidth()
            .popWhen(pop)
            .clip(RoundedCornerShape(12.dp))
            .background(TableColors.FeltLight.copy(alpha = 0.55f))
            .clickable(enabled = enabled, onClick = onJoin)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(table.hostName, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.join_table_of, table.hostName),
                color = TableColors.OnFelt,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = buildList {
                GameKind.fromId(table.gameId)?.let { add(GameTexts.gameName(res, it)) }
                if (table.players != null && table.maxPlayers != null) add("${table.players}/${table.maxPlayers}")
                add(stringResource(if (table.inGame) R.string.join_phase_watch else R.string.join_phase_lobby))
            }.joinToString(" · ")
            Text(detail, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = TableColors.TurnGlow)
        } else {
            Text(
                stringResource(if (table.inGame) R.string.nearby_watch else R.string.join_connect),
                color = TableColors.CardBlack,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (enabled) TableColors.TurnGlow else TableColors.OnFeltMuted)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private const val READY_POLL_MS = 2_000L
