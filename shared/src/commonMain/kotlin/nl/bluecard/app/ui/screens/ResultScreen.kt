package nl.bluecard.app.ui.screens

import androidx.compose.ui.text.style.TextOverflow
import nl.bluecard.app.ui.text.HeartsTexts
import nl.bluecard.app.ui.text.PresidentTexts
import nl.bluecard.app.ui.text.FishTitles
import nl.bluecard.app.platform.AdSlot
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.app.ui.components.PlayerAvatar
import nl.bluecard.app.session.GameKind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import nl.bluecard.app.ui.components.Confetti
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import nl.bluecard.app.ui.components.platformUi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.SystemBarIcons
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.multiplayer.session.SessionPhase

@Composable
fun ResultScreen(onNewGame: () -> Unit, onExitToMenu: () -> Unit, onBackToLobby: () -> Unit) {
    val container = appContainer()
    val vm: ResultViewModel = viewModel { ResultViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    // A new game was started (by us or by the host): go back to the table.
    LaunchedEffect(ui.summary) {
        if (ui.summary?.running == true) onNewGame()
    }
    LaunchedEffect(ui.kind, ui.lobbyPhase) {
        if (ui.kind == SessionKind.CLIENT && ui.lobbyPhase == SessionPhase.LOBBY) onBackToLobby()
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val text = when (message) {
                is GameMessage.Rejected -> GameTexts.reject(res, message.code)
                is GameMessage.Notice -> GameTexts.notice(res, message.notice)
                else -> null
            }
            if (text != null) snackbar.showSnackbar(text)
        }
    }
    val exit = {
        vm.leave()
        onExitToMenu()
    }
    platformUi().BackHandler { exit() }
    SystemBarIcons(darkBackground = true)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TableColors.FeltLight, TableColors.FeltDark))),
    ) {
        if (!ui.hasSession) {
            NoSession(onExitToMenu)
            return@Box
        }
        val view = ui.summary
        val result = view?.result
        val current = ui.game
        var nextGame by remember(current) { mutableStateOf(current) }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.result_title), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.titleMedium)
                if (view == null || result == null) {
                    CircularProgressIndicator(color = TableColors.OnFelt)
                    return@Column
                }
                val winner = result.winner
                val iWon = winner?.playerId == view.viewerId
                // The two aces jump up for the winner.
                val jump = remember { Animatable(if (iWon) 0f else 1f) }
                LaunchedEffect(iWon) {
                    if (iWon) jump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-18).dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.6f + 0.4f * jump.value
                        scaleY = 0.6f + 0.4f * jump.value
                        translationY = (1f - jump.value) * 60f
                    },
                ) {
                    PlayingCard(Card.of("AH"), 54.dp, Modifier.rotate(-8f * jump.value))
                    PlayingCard(Card.of("AS"), 54.dp, Modifier.rotate(8f * jump.value))
                }
                Text(
                    when {
                        winner == null -> ""
                        winner.playerId == view.viewerId -> stringResource(R.string.result_you_won)
                        else -> stringResource(R.string.result_winner, winner.name)
                    },
                    color = TableColors.Highlight,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                if (iWon && current != null) {
                    var sharing by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { sharing = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TableColors.Highlight),
                    ) { Text("📸  " + stringResource(R.string.share_open), fontWeight = FontWeight.Bold) }
                    if (sharing) WinShareDialog(current, result) { sharing = false }
                }

                Text(stringResource(R.string.result_standings), color = TableColors.OnFelt, style = MaterialTheme.typography.titleMedium)
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (entry in result.ranking) {
                        val isMe = entry.playerId == view.viewerId
                        val isLoser = result.ranking.size > 1 && entry == result.loser
                        Surface(
                            color = if (isMe) TableColors.FeltLight else Color.Black.copy(alpha = 0.25f),
                            contentColor = TableColors.OnFelt,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${entry.position}.",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    modifier = Modifier.width(36.dp),
                                    color = if (entry.position == 1) TableColors.Highlight else TableColors.OnFelt,
                                )
                                val seat = ui.seats.firstOrNull { it.id == entry.playerId }
                                PlayerAvatar(entry.name, size = 34.dp, isBot = seat?.kind == SeatKind.BOT, avatar = seat?.avatar)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isMe) stringResource(R.string.you_suffix, entry.name) else entry.name,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    val score = entry.score
                                    when {
                                        score != null -> Text(HeartsTexts.points(res, score), style = MaterialTheme.typography.bodySmall)
                                        ui.game == GameKind.ZWEEDS_PESTEN -> Text(
                                            FishTitles.emoji(entry.position, result.ranking.size) + " " +
                                                FishTitles.title(res, entry.position, result.ranking.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (entry.position == 1) TableColors.Highlight else TableColors.OnFeltMuted,
                                        )
                                        ui.game == GameKind.PRESIDENTEN -> Text(
                                            PresidentTexts.title(res, PresidentTexts.titleFor(entry.position, result.ranking.size)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (entry.position == 1) TableColors.Highlight else TableColors.OnFeltMuted,
                                        )
                                        entry.cardsLeft > 0 -> Text(
                                            pluralStringResource(R.plurals.result_cards_left, entry.cardsLeft, entry.cardsLeft),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                // Pesten games crown the "pestkop"; Presidenten shows titles and Hartenjagen points under the name instead.
                                if (isLoser && ui.game == GameKind.PESTEN) {
                                    Text(stringResource(R.string.result_loser), color = TableColors.Danger, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                GameFactsPanel(result)
                // The next round may be another game.
                if (ui.kind != SessionKind.CLIENT && current != null && nextGame != null) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth()) { GameGrid(nextGame!!) { nextGame = it } }
                }
            }
            // The actions stay in view below the (scrolling) standings, also with many players or a small screen.
            if (result != null) {
                Column(
                    Modifier.widthIn(max = 468.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val switchToLobby = ui.kind == SessionKind.HOST && nextGame != null && nextGame != current
                    when (ui.kind) {
                        SessionKind.LOCAL, SessionKind.HOST -> Button(
                            onClick = {
                                val game = nextGame
                                if (switchToLobby && game != null) vm.lobbyWith(game, onBackToLobby) else vm.playAgain(game)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
                        ) {
                            Text(
                                if (switchToLobby) stringResource(R.string.result_to_lobby_with, GameTexts.gameName(res, nextGame!!)) else stringResource(R.string.result_play_again),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        SessionKind.CLIENT -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TableColors.OnFelt)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.result_wait_host), color = TableColors.OnFelt)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Between rounds: let a phone that comes in later find the table (it plays the next round).
                        if (ui.kind == SessionKind.HOST) platformUi().HostVisibilityButton()
                        if (ui.kind == SessionKind.HOST) {
                            OutlinedButton(
                                onClick = {
                                    vm.returnToLobby()
                                    onBackToLobby()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TableColors.OnFelt),
                            ) { Text(stringResource(R.string.result_to_lobby), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        OutlinedButton(
                            onClick = exit,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TableColors.OnFelt),
                        ) { Text(stringResource(R.string.result_to_menu), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            // Below everything, away from the buttons; never during play.
            container.ads.Banner(AdSlot.RESULT, Modifier)
        }
        // Confetti for the winner.
        val final = ui.summary
        val finalResult = final?.result
        if (finalResult != null && finalResult.winner?.playerId == final.viewerId) Confetti(seed = finalResult.hashCode())
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}
