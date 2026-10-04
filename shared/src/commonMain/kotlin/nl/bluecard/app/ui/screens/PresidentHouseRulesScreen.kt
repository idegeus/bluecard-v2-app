package nl.bluecard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.components.FeltDivider
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.components.FeltSegmented
import nl.bluecard.app.ui.components.FeltSwitchRow
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.text.HeartsTexts
import nl.bluecard.app.ui.text.PresidentTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.hartenjagen.HjHouseRules
import nl.bluecard.engine.hartenjagen.HjPreset
import nl.bluecard.engine.presidenten.PrHouseRules
import nl.bluecard.engine.presidenten.PrPreset

/** Editor for the Presidenten house rules (stored in the settings; a hosted lobby picks them up). */
@Composable
fun PresidentHouseRulesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val settings by container.settings.collectAsStateWithLifecycle()
    val rules = settings.presidentRules
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    fun update(transform: (PrHouseRules) -> PrHouseRules) {
        scope.launch { container.settingsRepository.update { it.copy(presidentRules = transform(it.presidentRules)) } }
    }

    FeltScreen(title = stringResource(R.string.hr_title_presidenten), onBack = onBack) {
        FeltPanel(title = stringResource(R.string.hr_preset)) {
            val current = PrPreset.matching(rules)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in PrPreset.entries) {
                    PresetPill(PresidentTexts.preset(res, preset), selected = current == preset) { update { preset.rules } }
                }
                if (current == null) PresetPill(stringResource(R.string.preset_custom), selected = true) {}
            }
        }
        FeltPanel(title = stringResource(R.string.hr_options)) {
            FeltSwitchRow(
                stringResource(R.string.hr_pr_two_high),
                rules.twoHigh,
                { v -> update { it.copy(twoHigh = v) } },
                detail = stringResource(R.string.hr_pr_two_high_detail),
            )
            FeltDivider()
            Text(stringResource(R.string.hr_ps_jokers), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            FeltSegmented(
                options = (0..PrHouseRules.MAX_JOKERS).toList(),
                selected = rules.jokers,
                label = { it.toString() },
                onSelect = { n -> update { it.copy(jokers = n) } },
            )
            Text(stringResource(R.string.rules_pr_jokers), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
            FeltDivider()
            FeltSwitchRow(
                stringResource(R.string.hr_pr_exchange),
                rules.exchange,
                { v -> update { it.copy(exchange = v) } },
                detail = stringResource(R.string.hr_pr_exchange_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_pr_pass_final),
                rules.passIsFinal,
                { v -> update { it.copy(passIsFinal = v) } },
                detail = stringResource(R.string.hr_pr_pass_final_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_pr_equal_skips),
                rules.equalSkips,
                { v -> update { it.copy(equalSkips = v) } },
                detail = stringResource(R.string.hr_pr_equal_skips_detail),
            )
        }
    }
}

/** Editor for the Hartenjagen house rules. */
@Composable
fun HeartsHouseRulesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val settings by container.settings.collectAsStateWithLifecycle()
    val rules = settings.heartsRules
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    fun update(transform: (HjHouseRules) -> HjHouseRules) {
        scope.launch { container.settingsRepository.update { it.copy(heartsRules = transform(it.heartsRules)) } }
    }

    FeltScreen(title = stringResource(R.string.hr_title_hartenjagen), onBack = onBack) {
        FeltPanel(title = stringResource(R.string.hr_preset)) {
            val current = HjPreset.matching(rules)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in HjPreset.entries) {
                    PresetPill(HeartsTexts.preset(res, preset), selected = current == preset) { update { preset.rules } }
                }
                if (current == null) PresetPill(stringResource(R.string.preset_custom), selected = true) {}
            }
        }
        FeltPanel(title = stringResource(R.string.rules_hj_points_title)) {
            Text(stringResource(R.string.hr_hj_queen), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            FeltSegmented(
                options = listOf(5, 13),
                selected = rules.queenPoints,
                label = { it.toString() },
                onSelect = { n -> update { it.copy(queenPoints = n) } },
            )
            Text(stringResource(R.string.hr_hj_jack), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            FeltSegmented(
                options = listOf(0, 2),
                selected = rules.jackPoints,
                label = { it.toString() },
                onSelect = { n -> update { it.copy(jackPoints = n) } },
            )
            Text(stringResource(R.string.hr_hj_target), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            FeltSegmented(
                options = HjHouseRules.TARGETS,
                selected = rules.targetScore,
                label = { if (it == 0) stringResource(R.string.hj_target_one_short) else it.toString() },
                onSelect = { n -> update { it.copy(targetScore = n) } },
            )
        }
        FeltPanel(title = stringResource(R.string.hr_options)) {
            FeltSwitchRow(
                stringResource(R.string.hr_hj_pass),
                rules.passCount > 0,
                { v -> update { it.copy(passCount = if (v) 3 else 0) } },
                detail = pluralStringResource(R.plurals.rules_hj_pass, 3, 3),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_hj_clubs_lead),
                rules.clubsLead,
                { v -> update { it.copy(clubsLead = v) } },
                detail = stringResource(R.string.hr_hj_clubs_lead_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_hj_hearts_broken),
                rules.heartsBroken,
                { v -> update { it.copy(heartsBroken = v) } },
                detail = stringResource(R.string.hr_hj_hearts_broken_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_hj_first_trick),
                rules.noPointsFirstTrick,
                { v -> update { it.copy(noPointsFirstTrick = v) } },
                detail = stringResource(R.string.hr_hj_first_trick_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_hj_moon),
                rules.shootTheMoon,
                { v -> update { it.copy(shootTheMoon = v) } },
                detail = stringResource(R.string.hr_hj_moon_detail),
            )
        }
    }
}
