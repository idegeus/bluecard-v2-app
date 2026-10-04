package nl.bluecard.app.ui.screens

import nl.bluecard.app.ui.text.HeartsTexts
import nl.bluecard.app.ui.text.PresidentTexts
import nl.bluecard.engine.hartenjagen.HjPreset
import nl.bluecard.engine.presidenten.PrPreset
import androidx.compose.foundation.layout.Arrangement
import nl.bluecard.app.settings.AppLanguage
import androidx.compose.foundation.layout.FlowRow
import nl.bluecard.app.ui.components.FeltDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.Resources
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.settings.AppSettings
import nl.bluecard.app.settings.BotSpeed
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.components.FeltSecondaryButton
import nl.bluecard.app.ui.components.FeltSegmented
import nl.bluecard.app.ui.components.FeltSwitchRow
import nl.bluecard.app.ui.components.PlayerAvatar
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.components.feltTextFieldColors
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.ui.text.PestenTexts
import nl.bluecard.engine.pesten.PsPreset
import nl.bluecard.engine.zweedspesten.ZpPreset

@Composable
fun SettingsScreen(onBack: () -> Unit, onEditRules: (GameKind) -> Unit) {
    val container = appContainer()
    val settings by container.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { container.settingsRepository.update(transform) }
    }

    FeltScreen(title = stringResource(R.string.settings_title), onBack = onBack) {
        FeltPanel(title = stringResource(R.string.settings_player)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(settings.displayName, size = 56.dp, avatar = settings.avatar)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    PlayerNameField(settings.playerName, onFelt = true) { name -> update { it.copy(playerName = name) } }
                }
            }
            FeltDivider()
            Text(stringResource(R.string.avatar_title), color = TableColors.OnFelt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            AvatarPicker(settings.avatar) { avatar -> update { it.copy(avatar = avatar) } }
        }

        FeltPanel(title = stringResource(R.string.settings_language)) {
            val languages = container.platform.language
            var language by remember { mutableStateOf(languages.current()) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tag in AppLanguage.OPTIONS) {
                    PresetPill(
                        if (tag.isEmpty()) stringResource(R.string.lang_system) else AppLanguage.nativeName(tag),
                        selected = language == tag,
                    ) {
                        language = tag
                        languages.set(tag)
                        Resources.useLanguage(languages.effective())
                    }
                }
            }
        }

        FeltPanel(title = stringResource(R.string.settings_gameplay)) {
            SettingLabel(stringResource(R.string.settings_bot_speed))
            FeltSegmented(
                options = BotSpeed.entries.toList(),
                selected = settings.botSpeed,
                label = { speed ->
                    stringResource(
                        when (speed) {
                            BotSpeed.SLOW -> R.string.speed_SLOW
                            BotSpeed.NORMAL -> R.string.speed_NORMAL
                            BotSpeed.FAST -> R.string.speed_FAST
                        },
                    )
                },
                onSelect = { speed -> update { it.copy(botSpeed = speed) } },
            )
            SettingLabel(stringResource(R.string.settings_default_difficulty))
            FeltSegmented(
                options = BotDifficulty.entries.toList(),
                selected = settings.defaultDifficulty,
                label = { GameTexts.difficulty(res, it) },
                onSelect = { d -> update { it.copy(defaultDifficulty = d) } },
            )
            FeltSwitchRow(stringResource(R.string.settings_hints), settings.showHints, { v -> update { it.copy(showHints = v) } })
            FeltSwitchRow(stringResource(R.string.settings_keep_screen_on), settings.keepScreenOn, { v -> update { it.copy(keepScreenOn = v) } })
            FeltSwitchRow(stringResource(R.string.settings_sound), settings.soundEffects, { v -> update { it.copy(soundEffects = v) } })
            FeltSwitchRow(
                stringResource(R.string.settings_shuffle),
                settings.shuffleRitual,
                { v -> update { it.copy(shuffleRitual = v) } },
                detail = stringResource(R.string.settings_shuffle_detail),
            )
            if (container.platform.kind == nl.bluecard.app.platform.PlatformKind.ANDROID) {
                FeltSwitchRow(
                    stringResource(R.string.settings_nearby_alerts),
                    settings.nearbyAlerts,
                    { v -> update { it.copy(nearbyAlerts = v) } },
                    detail = stringResource(R.string.settings_nearby_alerts_detail),
                )
            }
        }

        FeltPanel(title = stringResource(R.string.settings_house_rules)) {
            RulesLink(
                title = stringResource(R.string.settings_house_rules_zweeds),
                detail = GameTexts.preset(res, ZpPreset.matching(settings.houseRules)),
                onClick = { onEditRules(GameKind.ZWEEDS_PESTEN) },
            )
            RulesLink(
                title = stringResource(R.string.settings_house_rules_pesten),
                detail = PestenTexts.preset(res, PsPreset.matching(settings.pestenRules)),
                onClick = { onEditRules(GameKind.PESTEN) },
            )
            RulesLink(
                title = stringResource(R.string.settings_house_rules_presidenten),
                detail = PresidentTexts.preset(res, PrPreset.matching(settings.presidentRules)),
                onClick = { onEditRules(GameKind.PRESIDENTEN) },
            )
            RulesLink(
                title = stringResource(R.string.settings_house_rules_hartenjagen),
                detail = HeartsTexts.preset(res, HjPreset.matching(settings.heartsRules)),
                onClick = { onEditRules(GameKind.HARTENJAGEN) },
            )
        }

        FeltPanel(title = stringResource(R.string.settings_about)) {
            Text(
                stringResource(R.string.settings_about_text, container.platform.versionName),
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            val privacyRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()
            if (privacyRequired) {
                FeltSecondaryButton(
                    stringResource(R.string.settings_ad_privacy),
                    onClick = { container.ads.showPrivacyOptions() },
                )
            }
            FeltSecondaryButton(stringResource(R.string.settings_reset), onClick = { scope.launch { container.settingsRepository.resetPreferences() } })
        }
        Spacer(Modifier.size(4.dp))
        Text(
            stringResource(R.string.menu_offline_note),
            color = TableColors.OnFeltMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** A tappable row that opens the house rules of one game. */
@Composable
private fun RulesLink(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TableColors.OnFelt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = TableColors.OnFelt, fontSize = 30.sp)
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, color = TableColors.OnFelt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
}

/** Name input that saves shortly after typing stops. */
@Composable
fun PlayerNameField(
    current: String,
    label: String = stringResource(R.string.settings_name),
    onFelt: Boolean = true,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    var initialised by remember { mutableStateOf(current.isNotEmpty()) }
    LaunchedEffect(current) {
        // Take over the stored value once it has loaded, but never overwrite what the user is typing.
        if (!initialised && current.isNotEmpty()) {
            text = current
            initialised = true
        }
    }
    LaunchedEffect(text) {
        if (text.trim() != current) {
            delay(400)
            onSave(text.trim().take(AppSettings.MAX_NAME_LENGTH))
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(AppSettings.MAX_NAME_LENGTH) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
        colors = if (onFelt) feltTextFieldColors() else OutlinedTextFieldDefaults.colors(),
    )
}
