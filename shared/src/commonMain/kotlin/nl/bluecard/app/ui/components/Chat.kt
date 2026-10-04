package nl.bluecard.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.multiplayer.session.ChatLimits
import nl.bluecard.multiplayer.session.ChatMessage
import nl.bluecard.multiplayer.session.PlayerPort

/** What has been read of the chat, and whether the chat sheet is open (one table at a time). */
object ChatState {
    var lastSeenId by mutableLongStateOf(0L)
    var sheetOpen by mutableStateOf(false)
}

/** 💬 in the table's top bar, with the number of unread lines; opens the chat. */
@Composable
internal fun ChatButton(port: PlayerPort<*, *>, myId: String?) {
    val chat by port.chat.collectAsStateWithLifecycle()
    val unread = chat.count { it.id > ChatState.lastSeenId && it.fromId != myId }
    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(onClick = { ChatState.sheetOpen = true }) { Text("💬", fontSize = 20.sp) }
        if (unread > 0) {
            Text(
                if (unread > 9) "9+" else unread.toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 6.dp, end = 4.dp)
                    .clip(CircleShape)
                    .background(TableColors.Danger)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
    if (ChatState.sheetOpen) ChatSheet(port, myId) { ChatState.sheetOpen = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSheet(port: PlayerPort<*, *>, myId: String?, onDismiss: () -> Unit) {
    val chat by port.chat.collectAsStateWithLifecycle()
    LaunchedEffect(chat.size) { chat.lastOrNull()?.let { ChatState.lastSeenId = maxOf(ChatState.lastSeenId, it.id) } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TableColors.FeltDark,
        contentColor = TableColors.OnFelt,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding()) {
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            ChatContent(port, myId, maxListHeight = 420)
        }
    }
}

/** The chat in the lobby: the latest lines and a field to type in. */
@Composable
fun LobbyChatPanel(port: PlayerPort<*, *>, myId: String?) {
    FeltPanel(title = stringResource(R.string.chat_title)) {
        val chat by port.chat.collectAsStateWithLifecycle()
        LaunchedEffect(chat.size) { chat.lastOrNull()?.let { ChatState.lastSeenId = maxOf(ChatState.lastSeenId, it.id) } }
        ChatContent(port, myId, maxListHeight = 220)
    }
}

@Composable
private fun ChatContent(port: PlayerPort<*, *>, myId: String?, maxListHeight: Int) {
    val chat by port.chat.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(chat.size) { if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex) }
    fun send(line: String) {
        scope.launch { if (port.sendChat(line)) text = "" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (chat.isEmpty()) {
            Text(stringResource(R.string.chat_empty), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxListHeight.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(chat, key = { it.id }) { line -> ChatLine(line, mine = line.fromId == myId) }
            }
        }
        // Quick lines for during a game.
        val quick = listOf(
            stringResource(R.string.chat_quick_1),
            stringResource(R.string.chat_quick_2),
            stringResource(R.string.chat_quick_3),
            stringResource(R.string.chat_quick_4),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (line in quick) {
                Text(
                    line,
                    color = TableColors.OnFelt,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(TableColors.Ink.copy(alpha = 0.12f))
                        .clickable { send(line) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(ChatLimits.MAX_LENGTH) },
                placeholder = { Text(stringResource(R.string.chat_hint)) },
                singleLine = true,
                colors = feltTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(text) }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { send(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.chat_send), color = TableColors.TurnGlow, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChatLine(line: ChatMessage, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) TableColors.TurnGlow.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.3f),
            contentColor = if (mine) TableColors.CardBlack else TableColors.OnFelt,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (!mine) Text(line.fromName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(line.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A new chat line from someone else pops up at the top of the screen for a few seconds (unless the chat is open). */
@Composable
internal fun ChatToast(port: PlayerPort<*, *>, myId: String?) {
    val chat by port.chat.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf<ChatMessage?>(null) }
    var seen by remember(port) { mutableLongStateOf(chat.lastOrNull()?.id ?: 0L) }
    LaunchedEffect(chat.lastOrNull()?.id) {
        val last = chat.lastOrNull() ?: return@LaunchedEffect
        if (last.id <= seen) return@LaunchedEffect
        seen = last.id
        if (last.fromId == myId || ChatState.sheetOpen) return@LaunchedEffect
        shown = last
        delay(TOAST_MS)
        if (shown == last) shown = null
    }
    Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = shown != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val line = shown ?: return@AnimatedVisibility
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 420.dp).clickable { shown = null; ChatState.sheetOpen = true },
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💬", modifier = Modifier.size(24.dp))
                    Text(line.fromName + ": ", fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(line.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private const val TOAST_MS = 3_500L
