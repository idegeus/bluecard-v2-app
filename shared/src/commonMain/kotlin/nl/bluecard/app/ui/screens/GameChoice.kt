package nl.bluecard.app.ui.screens

import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.multiplayer.protocol.ProtocolJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.KSerializer
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.bluecard.app.R
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.settings.AppSettings
import nl.bluecard.app.ui.components.FeltChip
import nl.bluecard.app.ui.components.FeltChips
import nl.bluecard.app.ui.components.FeltDivider
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltSecondaryButton
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.text.HeartsTexts
import nl.bluecard.app.ui.text.PestenTexts
import nl.bluecard.app.ui.text.PresidentTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.hartenjagen.HjHouseRules
import nl.bluecard.engine.hartenjagen.HjPreset
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.model.Suit
import nl.bluecard.engine.pesten.PsHouseRules
import nl.bluecard.engine.pesten.PsPreset
import nl.bluecard.engine.pesten.PsRules
import nl.bluecard.engine.presidenten.PrHouseRules
import nl.bluecard.engine.presidenten.PrPreset
import nl.bluecard.engine.zweedspesten.ZpRules

/** Whether the app refuses cards that do not fit in [game]. */
/** Whether the app refuses cards that do not fit in [game] (always for games without "Vals!"). */
fun AppSettings.enforceRulesFor(game: GameKind): Boolean = when (game) {
    GameKind.ZWEEDS_PESTEN -> houseRules.enforceRules
    GameKind.PESTEN -> pestenRules.enforceRules
    GameKind.PRESIDENTEN, GameKind.HARTENJAGEN -> true
}

fun AppSettings.withEnforceRules(game: GameKind, enforce: Boolean): AppSettings = when (game) {
    GameKind.ZWEEDS_PESTEN -> copy(houseRules = houseRules.copy(enforceRules = enforce))
    GameKind.PESTEN -> copy(pestenRules = pestenRules.copy(enforceRules = enforce))
    GameKind.PRESIDENTEN, GameKind.HARTENJAGEN -> this
}

/** Most players the current house rules of [game] allow. */
fun AppSettings.maxPlayersFor(game: GameKind): Int = when (game) {
    GameKind.ZWEEDS_PESTEN -> ZpRules.maxPlayersFor(houseRules)
    GameKind.PESTEN -> PsRules.maxPlayersFor(pestenRules)
    GameKind.PRESIDENTEN, GameKind.HARTENJAGEN -> game.maxPlayers
}

/** A few cards that say "this game" in headers. */
fun headerCards(game: GameKind): List<Card> = when (game) {
    GameKind.ZWEEDS_PESTEN -> listOf(Card.of("4C"), Card.of("2H"), Card.of("10D"))
    GameKind.PESTEN -> listOf(Card.of("JS"), Card(Rank.JOKER, Suit.HEARTS), Card.of("2D"))
    GameKind.PRESIDENTEN -> listOf(Card.of("KS"), Card.of("2H"), Card.of("2C"))
    GameKind.HARTENJAGEN -> listOf(Card.of("QS"), Card.of("AH"), Card.of("JC"))
}

fun gameDetail(game: GameKind): Int = when (game) {
    GameKind.ZWEEDS_PESTEN -> R.string.game_choice_zweeds_detail
    GameKind.PESTEN -> R.string.game_choice_pesten_detail
    GameKind.PRESIDENTEN -> R.string.game_choice_presidenten_detail
    GameKind.HARTENJAGEN -> R.string.game_choice_hartenjagen_detail
}

/** "Which game?" — a tile per game in a grid, with a one-line description of the chosen one. */
@Composable
fun GamePicker(selected: GameKind, onSelect: (GameKind) -> Unit) {
    FeltPanel(title = stringResource(R.string.game_choice)) {
        GameGrid(selected, onSelect)
        Text(stringResource(gameDetail(selected)), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/** All games as tiles, two per row: three small cards and the name. */
@Composable
fun GameGrid(selected: GameKind, onSelect: (GameKind) -> Unit) {
    val res = LocalResources.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in GameKind.entries.chunked(2)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (game in row) {
                    val chosen = game == selected
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .background(if (chosen) TableColors.TurnGlow else Color.White.copy(alpha = 0.06f))
                            .border(if (chosen) 0.dp else 1.dp, Color.White.copy(alpha = 0.2f), shape)
                            .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(game) })
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                            headerCards(game).forEachIndexed { i, card ->
                                PlayingCard(card, 24.dp, Modifier.rotate((i - 1) * 10f))
                            }
                        }
                        Text(
                            GameTexts.gameName(res, game),
                            color = if (chosen) TableColors.CardBlack else TableColors.OnFelt,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The rules of the chosen game at a glance, with the enforcement switch and a link to the house rules. */
@Composable
fun GameRulesPanel(settings: AppSettings, game: GameKind, onEnforce: (Boolean) -> Unit, onEditRules: (() -> Unit)?) {
    FeltPanel(title = stringResource(R.string.lobby_rules_title)) {
        if (game.hasCheating) {
            FeltEnforceSwitch(settings.enforceRulesFor(game), onEnforce)
            FeltDivider()
        }
        when (game) {
            GameKind.ZWEEDS_PESTEN -> RulesTableSummary(settings.houseRules)
            GameKind.PESTEN -> PestenRulesSummary(settings.pestenRules)
            GameKind.PRESIDENTEN -> PresidentRulesSummary(settings.presidentRules)
            GameKind.HARTENJAGEN -> HeartsRulesSummary(settings.heartsRules)
        }
        if (onEditRules != null) FeltSecondaryButton(stringResource(R.string.bots_change_rules), onClick = onEditRules)
    }
}

/** Pesten's special cards as real mini cards, plus the most important options. */
@Composable
fun PestenRulesSummary(rules: PsHouseRules, modifier: Modifier = Modifier) {
    val res = LocalResources.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            PestenTexts.preset(res, PsPreset.matching(rules)),
            color = TableColors.OnFelt,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        val specials = PestenTexts.specials(rules)
        if (specials.isNotEmpty()) {
            // Wraps onto a second line when there are many special cards.
            FeltChips {
                specials.forEachIndexed { i, (rank, effect) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                        PlayingCard(Card(rank, if (i % 2 == 0) Suit.HEARTS else Suit.SPADES), 36.dp)
                        Text(
                            if (effect == null) PestenTexts.jokerShort(res, rules) else PestenTexts.effectShort(res, effect),
                            color = TableColors.OnFeltMuted,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        FeltChips {
            FeltChip(pluralStringResource(R.plurals.hr_ps_hand_size_value, rules.handSize, rules.handSize))
            if (rules.stackDraws) FeltChip(stringResource(R.string.lobby_chip_stack))
            if (rules.playAfterPenalty) FeltChip(stringResource(R.string.hr_ps_play_after_penalty))
            if (rules.lastCardCall) FeltChip(stringResource(R.string.lobby_chip_last_card))
            if (rules.finishOnSpecial) FeltChip(stringResource(R.string.lobby_chip_finish_special))
            if (rules.allowMultiple) FeltChip(stringResource(R.string.lobby_chip_multiple))
            if (rules.winnerSwap) FeltChip(stringResource(R.string.hr_winner_swap))
            if (!rules.enforceRules && rules.escalatingPenalty) FeltChip(stringResource(R.string.hr_escalating))
            FeltChip(
                stringResource(if (rules.enforceRules) R.string.rules_enforced else R.string.rules_not_enforced),
                accent = !rules.enforceRules,
            )
        }
    }
}

@Composable
fun PresidentRulesSummary(rules: PrHouseRules, modifier: Modifier = Modifier) {
    val res = LocalResources.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            PresidentTexts.preset(res, PrPreset.matching(rules)),
            color = TableColors.OnFelt,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        FeltChips {
            FeltChip(stringResource(if (rules.twoHigh) R.string.hr_pr_two_high else R.string.hr_pr_two_low))
            if (rules.jokers > 0) FeltChip(stringResource(R.string.hr_ps_jokers) + ": " + rules.jokers)
            if (rules.exchange) FeltChip(stringResource(R.string.hr_pr_exchange))
            if (rules.passIsFinal) FeltChip(stringResource(R.string.hr_pr_pass_final))
            if (rules.equalSkips) FeltChip(stringResource(R.string.hr_pr_equal_skips))
        }
    }
}

@Composable
fun HeartsRulesSummary(rules: HjHouseRules, modifier: Modifier = Modifier) {
    val res = LocalResources.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            HeartsTexts.preset(res, HjPreset.matching(rules)),
            color = TableColors.OnFelt,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        // The cards that cost points, with their price.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeartsPenaltyCard(Card.of("AH"), HeartsTexts.points(res, 1))
            HeartsPenaltyCard(Card.of("QS"), HeartsTexts.points(res, rules.queenPoints))
            if (rules.jackPoints > 0) HeartsPenaltyCard(Card.of("JC"), HeartsTexts.points(res, rules.jackPoints))
        }
        FeltChips {
            FeltChip(HeartsTexts.target(res, rules.targetScore))
            if (rules.passCount > 0) FeltChip(pluralStringResource(R.plurals.hr_hj_pass_value, rules.passCount, rules.passCount))
            if (rules.heartsBroken) FeltChip(stringResource(R.string.hr_hj_hearts_broken))
            if (rules.shootTheMoon) FeltChip(stringResource(R.string.hr_hj_moon))
            if (rules.clubsLead) FeltChip(stringResource(R.string.hr_hj_clubs_lead))
        }
    }
}

@Composable
private fun HeartsPenaltyCard(card: Card, points: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        PlayingCard(card, 36.dp)
        Text(
            points,
            color = TableColors.OnFeltMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The house rules a lobby sends, decoded for the game of that table (null when they cannot be read). */
@Composable
fun LobbyRulesSummary(game: GameKind, config: JsonElement) {
    fun <T> decode(serializer: KSerializer<T>): T? = runCatching { ProtocolJson.json.decodeFromJsonElement(serializer, config) }.getOrNull()
    when (game) {
        GameKind.ZWEEDS_PESTEN -> remember(config) { decode(ZpHouseRules.serializer()) }?.let { RulesTableSummary(it) }
        GameKind.PESTEN -> remember(config) { decode(PsHouseRules.serializer()) }?.let { PestenRulesSummary(it) }
        GameKind.PRESIDENTEN -> remember(config) { decode(PrHouseRules.serializer()) }?.let { PresidentRulesSummary(it) }
        GameKind.HARTENJAGEN -> remember(config) { decode(HjHouseRules.serializer()) }?.let { HeartsRulesSummary(it) }
    }
}
