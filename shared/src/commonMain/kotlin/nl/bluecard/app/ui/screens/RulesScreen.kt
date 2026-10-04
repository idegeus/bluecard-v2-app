package nl.bluecard.app.ui.screens

import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.engine.pesten.PsHouseRules
import nl.bluecard.app.ui.text.PestenTexts
import nl.bluecard.app.session.GameKind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.text.HeartsTexts
import nl.bluecard.engine.hartenjagen.HjHouseRules
import nl.bluecard.engine.presidenten.PrHouseRules
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Suit

/** Explanation of both games. The special-cards parts are generated from the active house rules. */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val settings by container.settings.collectAsStateWithLifecycle()
    val res = LocalResources.current
    var game by rememberSaveable { mutableStateOf(settings.game) }

    FeltScreen(title = stringResource(R.string.rules_title), onBack = onBack) {
        GameGrid(game) { game = it }
        when (game) {
            GameKind.ZWEEDS_PESTEN -> ZweedsRules(settings.houseRules)
            GameKind.PESTEN -> PestenRules(settings.pestenRules)
            GameKind.PRESIDENTEN -> PresidentRules(settings.presidentRules)
            GameKind.HARTENJAGEN -> HeartsRules(settings.heartsRules)
        }
    }
}

@Composable
internal fun PestenRules(rules: PsHouseRules) {
    val res = LocalResources.current
    FeltHeader(
        title = stringResource(R.string.game_pesten),
        subtitle = pluralStringResource(R.plurals.rules_ps_goal, rules.handSize, rules.handSize),
        cards = headerCards(GameKind.PESTEN),
    )
    RulePanel(stringResource(R.string.rules_turn_title), stringResource(R.string.rules_ps_basic) +
        if (rules.allowMultiple) "\n" + stringResource(R.string.rules_multiple) else "")
    FeltPanel(title = stringResource(R.string.rules_special_title)) {
        for ((rank, effect) in PestenTexts.specials(rules)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayingCard(Card(rank, Suit.HEARTS), 44.dp)
                Text(
                    if (effect == null) PestenTexts.jokerLong(res, rules) else PestenTexts.effectLong(res, effect),
                    color = TableColors.OnFelt,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val extras = buildList {
            if (rules.stackDraws) add(stringResource(R.string.rules_ps_stack))
            if (rules.playAfterPenalty) add(stringResource(R.string.rules_ps_play_after_penalty))
            if (rules.lastCardCall) add(stringResource(R.string.rules_ps_last_card))
            if (!rules.finishOnSpecial) add(stringResource(R.string.rules_ps_finish_special))
        }
        extras.forEach { Text("• $it", color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium) }
    }
    RulePanel(
        stringResource(R.string.rules_end_title),
        stringResource(if (rules.playUntilLast) R.string.rules_end_last else R.string.rules_end_first),
    )
    RulePanel(
        stringResource(R.string.rules_cheating_title),
        stringResource(R.string.rules_ps_cheat) +
            (if (rules.escalatingPenalty) "\n" + stringResource(R.string.rules_ps_escalating) else "") +
            (if (rules.lastCardCall) "\n" + stringResource(R.string.rules_ps_reannounce) else ""),
    )
}

@Composable
internal fun ZweedsRules(rules: ZpHouseRules) {
    val res = LocalResources.current
    FeltHeader(
        title = stringResource(R.string.game_zweeds_pesten),
        subtitle = stringResource(R.string.rules_goal),
        cards = listOf(Card.of("2C"), Card.of("7H"), Card.of("10S")),
    )
    RulePanel(stringResource(R.string.rules_setup_title), stringResource(R.string.rules_setup, rules.handSize))
    if (rules.swapPhase) RulePanel(stringResource(R.string.rules_swap_title), stringResource(R.string.rules_swap))
    RulePanel(
        stringResource(R.string.rules_turn_title),
        stringResource(R.string.rules_turn, rules.handSize) +
            if (rules.allowMultiple) "\n" + stringResource(R.string.rules_multiple) else "",
    )
    RulePanel(stringResource(R.string.rules_pickup_title), stringResource(R.string.rules_pickup) + "\n" + stringResource(R.string.rules_bounce))
    RulePanel(stringResource(R.string.rules_table_title), stringResource(R.string.rules_table))

    FeltPanel(title = stringResource(R.string.rules_special_title)) {
        for ((rank, effect) in rules.specialRanks()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayingCard(Card(rank, Suit.HEARTS), 44.dp)
                Text(GameTexts.effectLong(res, effect, rules), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val extras = buildList {
            if (rules.fourOfAKindBurns) add(stringResource(R.string.rules_four_of_kind))
            if (rules.burnGivesExtraTurn) add(stringResource(R.string.rules_burn_extra))
            if (rules.playAfterPickUp) add(stringResource(R.string.rules_play_after_pickup))
            if (rules.drawGamble) add(stringResource(R.string.rules_gamble))
            if (rules.reshuffleBurned) add(stringResource(R.string.rules_reshuffle))
        }
        extras.forEach { Text("• $it", color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium) }
    }

    RulePanel(
        stringResource(R.string.rules_end_title),
        stringResource(if (rules.playUntilLast) R.string.rules_end_last else R.string.rules_end_first),
    )
    RulePanel(
        stringResource(R.string.rules_cheating_title),
        stringResource(R.string.rules_cheating) +
            if (rules.escalatingPenalty) "\n" + stringResource(R.string.rules_escalating) else "",
    )
    RulePanel(stringResource(R.string.rules_app_title), stringResource(R.string.rules_app))
}

@Composable
internal fun PresidentRules(rules: PrHouseRules) {
    FeltHeader(
        title = stringResource(R.string.game_presidenten),
        subtitle = stringResource(R.string.rules_pr_goal),
        cards = headerCards(GameKind.PRESIDENTEN),
    )
    RulePanel(stringResource(R.string.rules_setup_title), stringResource(R.string.rules_pr_setup))
    RulePanel(
        stringResource(R.string.rules_turn_title),
        stringResource(R.string.rules_pr_play) + "\n" +
            stringResource(if (rules.twoHigh) R.string.rules_pr_order_two_high else R.string.rules_pr_order_two_low) +
            (if (rules.jokers > 0) "\n" + stringResource(R.string.rules_pr_jokers) else "") +
            (if (rules.equalSkips) "\n" + stringResource(R.string.hr_pr_equal_skips_detail) else ""),
    )
    RulePanel(
        stringResource(R.string.rules_pr_trick_title),
        stringResource(R.string.rules_pr_trick) + "\n" +
            stringResource(if (rules.passIsFinal) R.string.hr_pr_pass_final_detail else R.string.rules_pr_pass_open),
    )
    RulePanel(stringResource(R.string.rules_pr_titles_title), stringResource(R.string.rules_pr_titles))
    if (rules.exchange) RulePanel(stringResource(R.string.hr_pr_exchange), stringResource(R.string.hr_pr_exchange_detail))
}

@Composable
internal fun HeartsRules(rules: HjHouseRules) {
    val res = LocalResources.current
    FeltHeader(
        title = stringResource(R.string.game_hartenjagen),
        subtitle = stringResource(R.string.rules_hj_goal),
        cards = headerCards(GameKind.HARTENJAGEN),
    )
    RulePanel(stringResource(R.string.rules_setup_title), stringResource(R.string.rules_hj_setup))
    if (rules.passCount > 0) RulePanel(stringResource(R.string.hr_hj_pass), pluralStringResource(R.plurals.rules_hj_pass, rules.passCount, rules.passCount))
    RulePanel(
        stringResource(R.string.rules_turn_title),
        stringResource(R.string.rules_hj_play) +
            (if (rules.clubsLead) "\n" + stringResource(R.string.hr_hj_clubs_lead_detail) else "") +
            (if (rules.heartsBroken) "\n" + stringResource(R.string.hr_hj_hearts_broken_detail) else "") +
            (if (rules.noPointsFirstTrick) "\n" + stringResource(R.string.hr_hj_first_trick_detail) else ""),
    )
    FeltPanel(title = stringResource(R.string.rules_hj_points_title)) {
        HeartsRulesSummary(rules)
        if (rules.shootTheMoon) Text("• " + stringResource(R.string.hr_hj_moon_detail), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
    }
    RulePanel(stringResource(R.string.rules_end_title), stringResource(R.string.rules_hj_end, HeartsTexts.target(res, rules.targetScore)))
}

@Composable
private fun RulePanel(title: String, body: String) {
    FeltPanel {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = TableColors.OnFelt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
