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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
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
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.model.Suit
import nl.bluecard.engine.zweedspesten.StartRule
import nl.bluecard.engine.zweedspesten.ZpEffect
import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.engine.zweedspesten.ZpPreset
import nl.bluecard.engine.zweedspesten.ZpRules

/**
 * Editor for the Zweeds Pesten house rules. The rules are stored in the settings; a hosted lobby picks up
 * changes automatically, a running game keeps the rules it started with.
 */
@Composable
fun HouseRulesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val settings by container.settings.collectAsStateWithLifecycle()
    val rules = settings.houseRules
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    fun update(transform: (ZpHouseRules) -> ZpHouseRules) {
        scope.launch { container.settingsRepository.update { it.copy(houseRules = transform(it.houseRules)) } }
    }

    FeltScreen(title = stringResource(R.string.hr_title), onBack = onBack) {
        FeltPanel(title = stringResource(R.string.hr_preset)) {
            val current = ZpPreset.matching(rules)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in ZpPreset.entries) {
                    PresetPill(GameTexts.preset(res, preset), selected = current == preset) {
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
                RankEffectRow(rank, rules.effectOf(rank), rules) { effect -> update { it.withEffect(rank, effect) } }
            }
        }

        FeltPanel(title = stringResource(R.string.hr_options)) {
            Text(
                stringResource(R.string.hr_hand_size, rules.handSize),
                color = TableColors.OnFelt,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = rules.handSize.toFloat(),
                onValueChange = { v ->
                    update { it.copy(handSize = kotlin.math.round(v).toInt().coerceIn(ZpHouseRules.MIN_HAND_SIZE, ZpHouseRules.MAX_HAND_SIZE)) }
                },
                valueRange = ZpHouseRules.MIN_HAND_SIZE.toFloat()..ZpHouseRules.MAX_HAND_SIZE.toFloat(),
                steps = ZpHouseRules.MAX_HAND_SIZE - ZpHouseRules.MIN_HAND_SIZE - 1,
                colors = SliderDefaults.colors(
                    thumbColor = TableColors.TurnGlow,
                    activeTrackColor = TableColors.TurnGlow,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    activeTickColor = TableColors.CardBlack,
                    inactiveTickColor = TableColors.OnFeltMuted,
                ),
            )
            Text(
                pluralStringResource(R.plurals.hr_max_players, ZpRules.maxPlayersFor(rules), ZpRules.maxPlayersFor(rules)),
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            FeltDivider()
            FeltSwitchRow(stringResource(R.string.hr_allow_multiple), rules.allowMultiple, { v -> update { it.copy(allowMultiple = v) } })
            FeltSwitchRow(stringResource(R.string.hr_four_of_kind), rules.fourOfAKindBurns, { v -> update { it.copy(fourOfAKindBurns = v) } })
            FeltSwitchRow(stringResource(R.string.hr_burn_extra), rules.burnGivesExtraTurn, { v -> update { it.copy(burnGivesExtraTurn = v) } })
            FeltSwitchRow(stringResource(R.string.hr_lower_equal), rules.lowerIncludesEqual, { v -> update { it.copy(lowerIncludesEqual = v) } })
            FeltSwitchRow(stringResource(R.string.hr_swap_phase), rules.swapPhase, { v -> update { it.copy(swapPhase = v) } })
            FeltSwitchRow(
                stringResource(R.string.hr_play_after_pickup),
                rules.playAfterPickUp,
                { v -> update { it.copy(playAfterPickUp = v) } },
                detail = stringResource(R.string.hr_play_after_pickup_detail),
            )
            FeltSwitchRow(
                stringResource(R.string.hr_gamble),
                rules.drawGamble,
                { v -> update { it.copy(drawGamble = v) } },
                detail = stringResource(R.string.hr_gamble_detail),
            )
            FeltSwitchRow(stringResource(R.string.hr_reshuffle), rules.reshuffleBurned, { v -> update { it.copy(reshuffleBurned = v) } })
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
                detail = stringResource(R.string.hr_play_until_last_detail),
            )
        }

        FeltPanel(title = stringResource(R.string.hr_start_rule)) {
            FeltSegmented(
                options = StartRule.entries.toList(),
                selected = rules.startRule,
                label = { GameTexts.startRule(res, it) },
                onSelect = { rule -> update { it.copy(startRule = rule) } },
            )
        }

        val hasEscape = rules.effects.values.any { it == ZpEffect.RESET || it == ZpEffect.BURN }
        if (!hasEscape || rules.reshuffleBurned) {
            FeltBanner(stringResource(R.string.hr_warning_long), tone = BannerTone.ERROR)
        }
    }
}

@Composable
internal fun PresetPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) TableColors.CardBlack else TableColors.OnFelt,
        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) TableColors.TurnGlow else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (selected) TableColors.TurnGlow else Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun RankEffectRow(rank: Rank, effect: ZpEffect, rules: ZpHouseRules, onChange: (ZpEffect) -> Unit) {
    val res = LocalResources.current
    var open by remember { mutableStateOf(false) }
    val special = effect != ZpEffect.NONE
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
                Text(GameTexts.effectShort(res, effect), modifier = Modifier.weight(1f), fontWeight = if (special) FontWeight.Bold else FontWeight.Normal)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (option in ZpEffect.entries) {
                    DropdownMenuItem(
                        text = { Text(GameTexts.effectLong(res, option, rules), modifier = Modifier.padding(vertical = 4.dp)) },
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
