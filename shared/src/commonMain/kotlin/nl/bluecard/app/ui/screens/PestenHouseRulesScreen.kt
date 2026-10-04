package nl.bluecard.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.BannerTone
import nl.bluecard.app.ui.components.FeltBanner
import nl.bluecard.app.ui.components.FeltDivider
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.components.FeltSegmented
import nl.bluecard.app.ui.components.FeltSwitchRow
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.text.PestenTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.model.Suit
import nl.bluecard.engine.pesten.PsEffect
import nl.bluecard.engine.pesten.PsHouseRules
import nl.bluecard.engine.pesten.PsPreset
import nl.bluecard.engine.pesten.PsRules

/**
 * Editor for the Pesten house rules. Stored in the settings; a hosted Pesten lobby picks up changes
 * automatically, a running game keeps the rules it started with.
 */
@Composable
fun PestenHouseRulesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val settings by container.settings.collectAsStateWithLifecycle()
    val rules = settings.pestenRules
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    fun update(transform: (PsHouseRules) -> PsHouseRules) {
        scope.launch { container.settingsRepository.update { it.copy(pestenRules = transform(it.pestenRules)) } }
    }

    FeltScreen(title = stringResource(R.string.hr_title_pesten), onBack = onBack) {
        FeltPanel(title = stringResource(R.string.hr_preset)) {
            val current = PsPreset.matching(rules)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in PsPreset.entries) {
                    PresetPill(PestenTexts.preset(res, preset), selected = current == preset) {
                        update { preset.rules.copy(enforceRules = it.enforceRules) }
                    }
                }
                if (current == null) PresetPill(stringResource(R.string.preset_custom), selected = true) {}
            }
            FeltDivider()
            FeltEnforceSwitch(rules.enforceRules) { v -> update { it.copy(enforceRules = v) } }
            if (!rules.enforceRules) {
                FeltSwitchRow(
                    stringResource(R.string.hr_escalating),
                    rules.escalatingPenalty,
                    { v -> update { it.copy(escalatingPenalty = v) } },
                    detail = stringResource(R.string.hr_escalating_detail),
                )
            }
        }

        FeltPanel(title = stringResource(R.string.hr_special_cards)) {
            for (rank in Rank.STANDARD) {
                PestenEffectRow(rank, rules.effectOf(rank)) { effect -> update { it.withEffect(rank, effect) } }
            }
            FeltDivider()
            Text(stringResource(R.string.hr_ps_jokers), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            FeltSegmented(
                options = (0..PsHouseRules.MAX_JOKERS).toList(),
                selected = rules.jokers,
                label = { it.toString() },
                onSelect = { n -> update { it.copy(jokers = n) } },
            )
            if (rules.jokers > 0) {
                Text(stringResource(R.string.hr_ps_joker_draw), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                FeltSegmented(
                    options = listOf(3, 4, 5),
                    selected = rules.jokerDraw,
                    label = { it.toString() },
                    onSelect = { n -> update { it.copy(jokerDraw = n) } },
                )
            }
        }

        FeltPanel(title = stringResource(R.string.hr_options)) {
            Text(
                pluralStringResource(R.plurals.hr_ps_hand_size_value, rules.handSize, rules.handSize),
                color = TableColors.OnFelt,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = rules.handSize.toFloat(),
                onValueChange = { v ->
                    update { it.copy(handSize = kotlin.math.round(v).toInt().coerceIn(PsHouseRules.MIN_HAND_SIZE, PsHouseRules.MAX_HAND_SIZE)) }
                },
                valueRange = PsHouseRules.MIN_HAND_SIZE.toFloat()..PsHouseRules.MAX_HAND_SIZE.toFloat(),
                steps = PsHouseRules.MAX_HAND_SIZE - PsHouseRules.MIN_HAND_SIZE - 1,
                colors = SliderDefaults.colors(
                    thumbColor = TableColors.TurnGlow,
                    activeTrackColor = TableColors.TurnGlow,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    activeTickColor = TableColors.CardBlack,
                    inactiveTickColor = TableColors.OnFeltMuted,
                ),
            )
            Text(
                pluralStringResource(R.plurals.hr_max_players, PsRules.maxPlayersFor(rules), PsRules.maxPlayersFor(rules)),
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            FeltDivider()
            FeltSwitchRow(
                stringResource(R.string.hr_ps_stack),
                rules.stackDraws,
                { v -> update { it.copy(stackDraws = v) } },
                detail = stringResource(R.string.hr_ps_stack_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_ps_play_after_penalty),
                rules.playAfterPenalty,
                { v -> update { it.copy(playAfterPenalty = v) } },
                detail = stringResource(R.string.hr_ps_play_after_penalty_detail),
            )
            FeltSwitchRow(stringResource(R.string.hr_allow_multiple), rules.allowMultiple, { v -> update { it.copy(allowMultiple = v) } })
            FeltSwitchRow(
                stringResource(R.string.hr_ps_last_card),
                rules.lastCardCall,
                { v -> update { it.copy(lastCardCall = v) } },
                detail = stringResource(R.string.hr_ps_last_card_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_ps_finish_special),
                rules.finishOnSpecial,
                { v -> update { it.copy(finishOnSpecial = v) } },
                detail = stringResource(R.string.hr_ps_finish_special_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_winner_swap),
                rules.winnerSwap,
                { v -> update { it.copy(winnerSwap = v) } },
                detail = stringResource(R.string.hr_winner_swap_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_play_until_last),
                rules.playUntilLast,
                { v -> update { it.copy(playUntilLast = v) } },
                detail = stringResource(R.string.hr_ps_play_until_last_detail),
            )
        }
    }
}

@Composable
private fun PestenEffectRow(rank: Rank, effect: PsEffect, onChange: (PsEffect) -> Unit) {
    val res = LocalResources.current
    var open by remember { mutableStateOf(false) }
    val special = effect != PsEffect.NONE
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayingCard(Card(rank, if (rank.ordinal % 2 == 0) Suit.SPADES else Suit.HEARTS), 34.dp, highlighted = special)
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, if (special) TableColors.TurnGlow else Color.White.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (special) TableColors.TurnGlow else TableColors.OnFelt),
            ) {
                Text(PestenTexts.effectShort(res, effect), modifier = Modifier.weight(1f), fontWeight = if (special) FontWeight.Bold else FontWeight.Normal)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (option in PsEffect.entries) {
                    DropdownMenuItem(
                        text = { Text(PestenTexts.effectLong(res, option), modifier = Modifier.padding(vertical = 4.dp)) },
                        onClick = {
                            open = false
                            onChange(option)
                        },
                    )
                }
            }
        }
    }
}
