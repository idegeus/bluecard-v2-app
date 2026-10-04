package nl.bluecard.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.bluecard.app.AppContainer
import nl.bluecard.app.R
import nl.bluecard.app.platform.Sound
import nl.bluecard.app.platform.nowMillis
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.multiplayer.session.LobbySnapshot
import nl.bluecard.multiplayer.session.PlayerPort
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionPhase
import nl.bluecard.multiplayer.session.SocialEvent
import nl.bluecard.multiplayer.session.SocialKind
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import nl.bluecard.multiplayer.session.SocialLimits
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/* Things that show over every screen while a session runs: the shuffle, buzzes and live reactions. */

/** How long the shuffler has to shake (only the time with real shaking counts). */
const val SHUFFLE_SHAKE_MS = 3_000L

/** Acceleration (m/s² above gravity) that counts as shaking. */
private const val SHAKE_THRESHOLD = 5f

fun SocialKind.emoji(): String = when (this) {
    SocialKind.REACTION -> "✨"
    SocialKind.BUZZ -> "🔔"
    SocialKind.HEART -> "❤️"
    SocialKind.THUMBS_UP -> "👍"
    SocialKind.CRY -> "😢"
    SocialKind.LAUGH -> "😂"
}

/**
 * The session overlay. [shake] is the horizontal offset of the whole app, so a buzz can rattle the screen.
 */
@Composable
fun SessionOverlay(container: AppContainer, shake: Animatable<Float, AnimationVector1D>) {
    val active by container.sessions.active.collectAsStateWithLifecycle()
    val session = active ?: return
    val port = session.port
    val lobby by port.lobby.collectAsStateWithLifecycle()
    val myId by port.playerId.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        SocialLayer(container, port, myId, shake)
        if (session !is ActiveSession.Local) ChatToast(port, myId)
        PlayerProfiles.openId?.let { id -> PlayerProfileDialog(container, session, id) { PlayerProfiles.close() } }
        val current = lobby
        if (current != null && current.phase == SessionPhase.SHUFFLING) {
            ShuffleOverlay(container, session, current, myId)
        }
    }
}

// ====================================================================== buzzes and reactions

private class FloatingReaction(val id: Long, val emoji: String, val name: String, val x: Float, val sway: Float)

@Composable
private fun SocialLayer(container: AppContainer, port: PlayerPort<*, *>, myId: String?, shake: Animatable<Float, AnimationVector1D>) {
    val reactions = remember { mutableStateListOf<FloatingReaction>() }
    var banner by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    val you = stringResource(R.string.you)
    var nextId by remember { mutableStateOf(0L) }

    LaunchedEffect(port, myId) {
        port.social.collect { event: SocialEvent ->
            val name = if (event.fromId == myId) you else event.fromName
            if (event.kind == SocialKind.BUZZ) {
                if (event.toId == myId) {
                    container.platform.haptics.buzz()
                    container.sounds.play(Sound.BUZZ)
                    banner = res.getString(R.string.social_buzzed_you, name) to true
                    scope.launch { rattle(shake) }
                } else {
                    val target = port.lobby.value?.seats?.firstOrNull { it.id == event.toId }?.name ?: ""
                    banner = res.getString(R.string.social_buzzed_other, name, target) to false
                }
            } else {
                if (reactions.size >= MAX_FLOATING) reactions.removeAt(0)
                reactions += FloatingReaction(nextId++, event.emoji ?: event.kind.emoji(), name, Random.nextFloat() * 0.6f + 0.2f, Random.nextFloat() * 2 - 1)
            }
        }
    }
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(BANNER_MS)
            banner = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        val width = maxWidth
        for (reaction in reactions) {
            key(reaction.id) {
                FloatingEmoji(reaction, width, height) { reactions.remove(reaction) }
            }
        }
        AnimatedVisibility(
            visible = banner != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp),
        ) {
            val (text, forMe) = banner ?: ("" to false)
            BuzzBanner(text, forMe)
        }
    }
}

/** Shakes the whole screen left and right, fading out. */
private suspend fun rattle(shake: Animatable<Float, AnimationVector1D>) {
    val strength = 26f
    for (i in 0 until 10) {
        val amount = strength * (1f - i / 10f)
        shake.animateTo(if (i % 2 == 0) amount else -amount, tween(38, easing = LinearEasing))
    }
    shake.animateTo(0f, tween(60))
}

@Composable
private fun BuzzBanner(text: String, forMe: Boolean) {
    val wobble = rememberInfiniteTransition(label = "bell")
    val angle by wobble.animateFloat(-18f, 18f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "bellAngle")
    Surface(
        color = if (forMe) TableColors.TurnGlow else Color.Black.copy(alpha = 0.75f),
        contentColor = if (forMe) TableColors.CardBlack else Color.White,
        shape = RoundedCornerShape(50),
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 420.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔔", fontSize = if (forMe) 26.sp else 18.sp, modifier = Modifier.rotate(if (forMe) angle else 0f))
            Spacer(Modifier.size(10.dp))
            Text(
                text,
                fontWeight = FontWeight.Bold,
                style = if (forMe) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FloatingEmoji(reaction: FloatingReaction, width: Dp, height: Dp, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(REACTION_MS.toInt(), easing = LinearEasing))
        onDone()
    }
    val p = progress.value
    val pop = if (p < 0.12f) 0.4f + 0.6f * (p / 0.12f) else 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset(
                x = width * reaction.x + (reaction.sway * 24 * sin(p * 2 * PI * 1.5)).dp - 28.dp,
                y = height * (0.82f - 0.55f * p),
            )
            .graphicsLayer {
                alpha = if (p > 0.7f) (1f - p) / 0.3f else 1f
                scaleX = pop
                scaleY = pop
            },
    ) {
        Text(reaction.emoji, fontSize = 40.sp)
        Surface(color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = RoundedCornerShape(50)) {
            Text(
                reaction.name,
                Modifier.padding(horizontal = 6.dp, vertical = 1.dp).widthIn(max = 90.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A bell that appears once [targetId] has let the table wait [SocialLimits.BUZZ_IDLE_MS] (nothing changed since
 * [changeKey]); tapping it buzzes them and hides it again for a while.
 */
@Composable
private fun BuzzButton(port: PlayerPort<*, *>, targetId: String, targetName: String, changeKey: Any?) {
    var since by remember { mutableStateOf(nowMillis()) }
    LaunchedEffect(changeKey, targetId) { since = nowMillis() }
    var now by remember { mutableStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = nowMillis()
        }
    }
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.social_buzz, targetName)
    val wobble = rememberInfiniteTransition(label = "buzzBell")
    val angle by wobble.animateFloat(-14f, 14f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "buzzAngle")
    AnimatedVisibility(
        visible = now - since >= SocialLimits.BUZZ_IDLE_MS,
        enter = androidx.compose.animation.scaleIn() + fadeIn(),
        exit = fadeOut(),
    ) {
        IconButton(onClick = {
            since = nowMillis()
            scope.launch { port.sendSocial(SocialKind.BUZZ, targetId) }
        }) {
            Text(
                "🔔",
                fontSize = 20.sp,
                modifier = Modifier.rotate(angle).semantics { contentDescription = label },
            )
        }
    }
}

/**
 * The reactions button for the table's top bar (only at a shared table): hearts, thumbs, tears, laughs, and a buzz
 * for the player who is taking their time.
 */
@Composable
fun SocialButton() {
    val container = appContainer()
    val active by container.sessions.active.collectAsStateWithLifecycle()
    val session = active ?: return
    if (session is ActiveSession.Local) return
    // The host can make the table findable again for phones that come in later.
    if (session is ActiveSession.Hosting) platformUi().HostVisibilityButton()
    val port = session.port
    val view by port.view.collectAsStateWithLifecycle()
    val lobby by port.lobby.collectAsStateWithLifecycle()
    val summary = view?.let { session.game.binding.summarizeAny(it) }
    val target = summary?.currentPlayerId
        ?.takeIf { it != summary.viewerId }
        ?.let { id -> lobby?.seats?.firstOrNull { it.id == id && it.kind != SeatKind.BOT && !it.botControlled } }
    var open by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settings by container.settings.collectAsStateWithLifecycle()
    val progress by container.myProgress.collectAsStateWithLifecycle()
    val bar = EmojiUnlocks.bar(settings.reactionEmojis, progress)
    // The buzzer only comes forward when the player whose turn it is has done nothing for a while.
    if (target != null) BuzzButton(port, target.id, target.name, changeKey = view)
    ChatButton(port, myId = summary?.viewerId)
    Box {
        IconButton(onClick = { open = true; refused = false }) {
            Text("😄", fontSize = 20.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = TableColors.FeltDark,
        ) {
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (emoji in bar) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clickable {
                                scope.launch {
                                    refused = !port.sendSocial(SocialKind.REACTION, emoji = emoji)
                                    if (!refused) open = false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emoji, fontSize = 26.sp)
                    }
                }
            }
            if (refused) {
                Text(
                    stringResource(R.string.social_slow_down),
                    color = TableColors.OnFeltMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).widthIn(max = 240.dp),
                )
            }
        }
    }
}

// ====================================================================== shuffle

@Composable
private fun ShuffleOverlay(container: AppContainer, session: ActiveSession, lobby: LobbySnapshot, myId: String?) {
    val shufflerId = lobby.shufflerId
    val shuffler = lobby.seats.firstOrNull { it.id == shufflerId }
    val mine = shufflerId != null && shufflerId == myId
    val isHost = session !is ActiveSession.Joined
    val motion = container.platform.motion
    val progress = remember(shufflerId) { MutableStateFlow(0f) }
    val intensity = remember(shufflerId) { mutableFloatStateOf(0f) }
    var sent by remember(shufflerId) { mutableStateOf(false) }
    var canSkip by remember(shufflerId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (mine) {
        LaunchedEffect(shufflerId) {
            if (!motion.available) return@LaunchedEffect
            var shakenMs = 0L
            var last = nowMillis()
            var lastTick = 0L
            var entropy = 0L
            motion.shaking().collect { strength ->
                val now = nowMillis()
                val dt = (now - last).coerceIn(0, 100)
                last = now
                intensity.floatValue = intensity.floatValue * 0.8f + strength * 0.2f
                if (sent || strength < SHAKE_THRESHOLD) return@collect
                shakenMs += dt
                entropy = entropy * 31 + (strength * 1000).toLong()
                if (now - lastTick > TICK_MS) {
                    lastTick = now
                    container.platform.haptics.tick()
                    container.sounds.play(Sound.SHUFFLE)
                }
                progress.value = (shakenMs.toFloat() / SHUFFLE_SHAKE_MS).coerceAtMost(1f)
                if (shakenMs >= SHUFFLE_SHAKE_MS) {
                    sent = true
                    session.port.shuffled(entropy xor now)
                }
            }
        }
    }
    if (isHost && !mine) {
        LaunchedEffect(shufflerId) {
            delay(SKIP_AFTER_MS)
            canSkip = true
        }
    }

    // A full-screen scrim that also blocks touches on the screen underneath.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                // Without a motion sensor the shuffler taps the deck instead.
                if (mine && !motion.available && !sent) {
                    progress.value = (progress.value + TAP_SHARE).coerceAtMost(1f)
                    container.platform.haptics.tick()
                    if (progress.value >= 1f) {
                        sent = true
                        scope.launch { session.port.shuffled(nowMillis()) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(24.dp).widthIn(max = 420.dp),
        ) {
            ShufflingDeck(speed = if (mine) 0.4f + (intensity.floatValue / 12f).coerceAtMost(2f) else 1f)
            Text(
                if (mine) stringResource(R.string.shuffle_you) else stringResource(R.string.shuffle_other, shuffler?.name ?: ""),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            if (mine) {
                Text(
                    stringResource(if (motion.available) R.string.shuffle_you_detail else R.string.shuffle_tap_detail),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                ShuffleProgress(progress, sent)
            }
            if (canSkip) {
                TextButton(onClick = { (session as? ActiveSession.Hosting)?.host?.finishShuffle() ?: (session as? ActiveSession.Local)?.host?.finishShuffle() }) {
                    Text(stringResource(R.string.shuffle_skip), color = TableColors.TurnGlow)
                }
            }
        }
    }
}

@Composable
private fun ShuffleProgress(progress: StateFlow<Float>, done: Boolean) {
    val value by progress.collectAsStateWithLifecycle()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = TableColors.TurnGlow,
            trackColor = Color.White.copy(alpha = 0.2f),
        )
        if (done) {
            Text(stringResource(R.string.shuffle_done), color = TableColors.TurnGlow, fontWeight = FontWeight.Bold)
        }
    }
}

/** Two halves of a deck riffling into each other, endlessly; [speed] 1 = calm. */
@Composable
private fun ShufflingDeck(speed: Float) {
    val transition = rememberInfiniteTransition(label = "shuffle")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing)), label = "riffle")
    val width = 64.dp
    val spread = (sin(phase * 2 * PI).toFloat() * 34f * speed.coerceIn(0.4f, 2.2f))
    Box(Modifier.size(width * 3, width * 1.9f), contentAlignment = Alignment.Center) {
        for (i in 0 until 6) {
            val left = i % 2 == 0
            val lift = (i / 2) * 3f
            CardBack(
                width,
                Modifier
                    .offset(x = (if (left) -spread else spread).dp, y = (-lift - kotlin.math.abs(spread) * 0.25f).dp)
                    .rotate(if (left) -spread / 4f else spread / 4f),
            )
        }
    }
}

private const val MAX_FLOATING = 14
private const val REACTION_MS = 2_600L
private const val BANNER_MS = 2_800L
private const val TICK_MS = 650L
private const val SKIP_AFTER_MS = 10_000L
private const val TAP_SHARE = 0.06f
