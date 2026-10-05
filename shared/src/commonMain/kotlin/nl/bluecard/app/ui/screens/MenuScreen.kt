package nl.bluecard.app.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.BoxWithConstraints
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.platform.AdSlot
import nl.bluecard.app.ui.components.platformUi
import nl.bluecard.app.ui.components.PlayerAvatar
import nl.bluecard.app.ui.components.feltTable
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import nl.bluecard.app.res.painterResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.SystemBarIcons
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.multiplayer.session.SessionPhase

@Composable
fun MenuScreen(
    onStartGame: () -> Unit,
    onJoin: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit,
    onLeaderboard: () -> Unit,
    onSkins: () -> Unit,
    onResumed: () -> Unit,
    onReturnToSession: (SessionPhase?, Boolean) -> Unit,
) {
    val container = appContainer()
    val hasSaved by container.sessions.hasSavedGame.collectAsStateWithLifecycle()
    val active by container.sessions.active.collectAsStateWithLifecycle()
    val settings by container.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val resumeFailed = stringResource(R.string.menu_continue_failed)
    SystemBarIcons(darkBackground = true)
    val intro = rememberMenuIntro()
    // A new table: game, bots and players are all chosen in its lobby (Android first asks for Bluetooth).
    val prepareTable = platformUi().rememberHostPreparer(onStartGame)
    var confirmLeaveTable by remember { mutableStateOf(false) }
    val startGame = { if (active is ActiveSession.Joined) confirmLeaveTable = true else prepareTable() }
    if (confirmLeaveTable) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLeaveTable = false },
            text = { Text(stringResource(R.string.menu_start_leaves)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeaveTable = false
                    prepareTable()
                }) { Text(stringResource(R.string.yes_leave)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLeaveTable = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .feltTable(),
    ) {
        // Decided each time the start screen comes into view (but not again after a mere rotation): the
        // first start screen in a while stays ad-free.
        val changingConfigurations = platformUi().rememberIsChangingConfigurations()
        var showAd by rememberSaveable { mutableStateOf(false) }
        var rotating by rememberSaveable { mutableStateOf(false) }
        LifecycleStartEffect(Unit) {
            if (rotating) rotating = false else showAd = container.ads.takeHomeScreenTurn()
            onStopOrDispose { rotating = changingConfigurations() }
        }
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Small phones: a smaller logo and tighter spacing, so all buttons fit without scrolling.
            val compact = maxHeight < 700.dp
            val tiny = maxHeight < 600.dp
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (tiny) 16.dp else 24.dp, vertical = if (compact) 12.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
                ) {
                    if (!compact) Spacer(Modifier.height(16.dp))
                    LogoCards(intro.cards.value, if (tiny) 46.dp else if (compact) 56.dp else 70.dp)
                    Text(
                        stringResource(R.string.app_name),
                        color = TableColors.OnFelt,
                        fontSize = if (compact) 32.sp else 40.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.introAppear(intro.title.value, fromScale = 0.4f),
                    )
                    if (!tiny) Text(
                        LocalResources.current.let { res -> GameKind.entries.joinToString(" · ") { GameTexts.gameName(res, it) } },
                        color = TableColors.OnFeltMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.introAppear(intro.title.value),
                    )
                    if (settings.playerName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.introAppear(intro.title.value)) {
                            PlayerAvatar(settings.displayName, size = 30.dp, avatar = settings.avatar)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.menu_hello, settings.playerName), color = TableColors.OnFelt)
                            // A small thank-you for those who tipped.
                            val thanks by container.entitlements.collectAsStateWithLifecycle()
                            if (thanks.supporter) Text(" 💚", color = TableColors.OnFelt)
                        }
                    }
                    Spacer(Modifier.height(if (compact) 0.dp else 8.dp))
                    Column(
                        Modifier.widthIn(max = 420.dp).fillMaxWidth().introAppear(intro.buttons.value, fromOffsetY = 140f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                    ) {
                        val running = active
                        if (running != null) {
                            Button(
                                onClick = {
                                    onReturnToSession(running.port.lobby.value?.phase, running is ActiveSession.Hosting)
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = if (tiny) 54.dp else 64.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.menu_return), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(stringResource(R.string.menu_return_detail), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else if (hasSaved) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (container.sessions.resumeLocalGame()) onResumed() else snackbar.showSnackbar(resumeFailed)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = if (tiny) 54.dp else 64.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.menu_continue), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(stringResource(R.string.menu_continue_detail), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        MenuButton(stringResource(R.string.menu_start_game), Icons.Filled.PlayArrow, primary = true, onClick = startGame, modifier = Modifier.fillMaxWidth(), gold = running == null && !hasSaved, small = tiny)
                        MenuButton(stringResource(R.string.menu_join), null, primary = true, onClick = onJoin, modifier = Modifier.fillMaxWidth(), small = tiny)
                        // Tables nearby show up here by themselves (only while you are not in a game).
                        if (running == null) {
                            NearbyTablesPanel(
                                onJoined = {
                                    // The host's lobby info arrives just after joining: wait for it, then go to the
                                    // lobby (or straight to the table when a game is running).
                                    scope.launch {
                                        val joined = container.sessions.active.value
                                        val lobby = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                                            joined?.port?.lobby?.first { it != null }
                                        }
                                        onReturnToSession(lobby?.phase ?: SessionPhase.LOBBY, false)
                                    }
                                },
                                onSetUp = onJoin,
                                onFailed = { message -> scope.launch { snackbar.showSnackbar(message) } },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MenuButton(stringResource(R.string.menu_leaderboard), Icons.Filled.Star, primary = false, onClick = onLeaderboard, modifier = Modifier.weight(1f), small = tiny)
                            MenuButton(stringResource(R.string.menu_skins), Icons.Filled.Favorite, primary = false, onClick = onSkins, modifier = Modifier.weight(1f), small = tiny)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MenuButton(stringResource(R.string.menu_rules), Icons.Filled.Info, primary = false, onClick = onRules, modifier = Modifier.weight(1f), small = tiny)
                            MenuButton(stringResource(R.string.menu_settings), Icons.Filled.Settings, primary = false, onClick = onSettings, modifier = Modifier.weight(1f), small = tiny)
                        }
                    }
                }
                val entitlements by container.entitlements.collectAsStateWithLifecycle()
                if (showAd && !entitlements.adsRemoved) container.ads.Banner(AdSlot.HOME, Modifier)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** The three logo cards. [rise] runs from 0 (below the screen) to 1 (fanned out above the title). */
@Composable
private fun LogoCards(rise: Float, cardWidth: Dp = 70.dp) {
    val breathe by rememberInfiniteTransition(label = "logo").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400), RepeatMode.Reverse),
        label = "breathe",
    )
    Box(Modifier.height(cardWidth * 1.72f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        for ((card, side) in listOf(Card.of("AS") to -1, Card.of("10D") to 0, Card.of("2H") to 1)) {
            PlayingCard(
                card,
                cardWidth,
                Modifier.graphicsLayer {
                    // Each card shoots up from below with a spin, the outer ones a little later, then fans out.
                    val own = ((rise - (side + 1) * 0.08f) / 0.76f).coerceIn(0f, 1f)
                    val spread = ((rise - 0.55f) / 0.45f).coerceIn(0f, 1.2f)
                    translationY = (1f - own) * 900.dp.toPx() + (if (side == 0) -6f - breathe * 3f else breathe * 2f) * density
                    translationX = side * (cardWidth * 0.63f).toPx() * spread
                    rotationZ = (1f - own) * 540f * (if (side == 0) 1f else side.toFloat()) + side * (14f + breathe) * spread
                    alpha = own.coerceAtLeast(0.01f)
                },
            )
        }
    }
}

/** Timeline of the opening animation; plays once per app start, otherwise everything is in place at once. */
private class MenuIntro(
    val cards: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val title: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val buttons: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
)

/** Set once the intro has played in this process, so it only greets you when you open the app. */
private var introPlayed = false

@Composable
private fun rememberMenuIntro(): MenuIntro {
    val start = if (introPlayed) 1f else 0f
    val intro = remember {
        MenuIntro(
            androidx.compose.animation.core.Animatable(start),
            androidx.compose.animation.core.Animatable(start),
            androidx.compose.animation.core.Animatable(start),
        )
    }
    LaunchedEffect(Unit) {
        if (introPlayed) return@LaunchedEffect
        introPlayed = true
        launch { intro.cards.animateTo(1f, tween(1_300, easing = FastOutSlowInEasing)) }
        launch {
            delay(650)
            intro.title.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        launch {
            delay(950)
            intro.buttons.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
    }
    return intro
}

/** Fades, grows and slides an element in as [progress] goes from 0 to 1. */
private fun Modifier.introAppear(progress: Float, fromScale: Float = 0.9f, fromOffsetY: Float = 40f): Modifier = graphicsLayer {
    alpha = progress.coerceIn(0f, 1f)
    val scale = fromScale + (1f - fromScale) * progress
    scaleX = scale
    scaleY = scale
    translationY = (1f - progress) * fromOffsetY * density
}

/**
 * Menu buttons with clear contrast on the felt: the main actions as solid buttons (gold and white), the others
 * as light outlined buttons.
 */
@Composable
private fun MenuButton(
    text: String,
    icon: ImageVector?,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    small: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        } else {
            Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(8.dp))
        // Long translations shrink instead of being cut off.
        BasicText(
            text,
            maxLines = 1,
            style = TextStyle(color = LocalContentColor.current, fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold),
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = if (small) 15.sp else 17.sp, stepSize = 0.5.sp),
        )
    }
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = if (small) 50.dp else 58.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (gold) TableColors.TurnGlow else Color.White,
                contentColor = if (gold) TableColors.CardBlack else TableColors.FeltDark,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = if (small) 44.dp else 52.dp),
            border = BorderStroke(1.5.dp, TableColors.Ink.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.18f), contentColor = TableColors.OnFelt),
        ) { content() }
    }
}
