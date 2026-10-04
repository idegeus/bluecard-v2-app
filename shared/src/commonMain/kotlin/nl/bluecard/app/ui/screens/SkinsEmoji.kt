package nl.bluecard.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import nl.bluecard.app.R
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.stats.Progress
import nl.bluecard.app.stats.Requirement
import nl.bluecard.app.ui.text.RequirementTexts
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.components.EmojiUnlocks
import nl.bluecard.app.ui.components.FeltDivider
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltSecondaryButton
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.multiplayer.session.EmojiCatalog

/**
 * The reaction emoji: your bar, and the tiers you unlock by winning (the last one: free choice of every emoji).
 * Tapping an unlocked emoji puts it in your bar or takes it out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiPanel(progress: Progress, chosen: List<String>, onChange: (List<String>) -> Unit) {
    val res = LocalResources.current
    val bar = EmojiUnlocks.bar(chosen, progress)
    val unlocked = EmojiUnlocks.unlocked(progress)
    var picking by remember { mutableStateOf(false) }
    fun toggle(emoji: String) {
        if (emoji !in unlocked) return
        onChange(
            when {
                emoji in bar && bar.size > 1 -> bar - emoji
                emoji in bar -> bar
                bar.size >= EmojiUnlocks.BAR_SIZE -> bar.drop(1) + emoji
                else -> bar + emoji
            },
        )
    }
    if (picking) EmojiPickerDialog(bar, ::toggle) { picking = false }

    FeltPanel(title = stringResource(R.string.skins_emojis)) {
        Text(stringResource(R.string.skins_emoji_bar), color = TableColors.OnFelt, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (emoji in bar) EmojiChip(emoji, selected = true, locked = false) { toggle(emoji) }
        }
        Text(stringResource(R.string.skins_emoji_bar_hint), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
        FeltDivider()
        for (tier in EmojiUnlocks.TIERS) {
            val open = tier.requirement.met(progress)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (open) "✅" else "🔒", fontSize = 14.sp)
                Text(
                    if (tier.requirement == Requirement.Free) stringResource(R.string.skins_emoji_tier_start)
                    else RequirementTexts.progress(res, tier.requirement, progress),
                    color = if (open) TableColors.Highlight else TableColors.OnFeltMuted,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            val emojis = tier.emojis
            if (emojis != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (emoji in emojis) EmojiChip(emoji, selected = emoji in bar, locked = !open) { toggle(emoji) }
                }
            } else {
                // The top tier: every emoji.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🌈", fontSize = 26.sp, modifier = Modifier.alpha(if (open) 1f else 0.4f))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.skins_emoji_free), color = TableColors.OnFelt, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.skins_emoji_free_detail), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!open) Icon(Icons.Filled.Lock, contentDescription = null, tint = TableColors.OnFeltMuted)
                }
                if (open) FeltSecondaryButton(stringResource(R.string.skins_emoji_pick), onClick = { picking = true })
            }
        }
    }
}

@Composable
private fun EmojiChip(emoji: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) TableColors.TurnGlow.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.2f))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) TableColors.TurnGlow else TableColors.Ink.copy(alpha = 0.2f)), CircleShape)
            .clickable(enabled = !locked, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 22.sp, modifier = Modifier.alpha(if (locked) 0.3f else 1f))
        if (locked) Icon(Icons.Filled.Lock, contentDescription = null, tint = TableColors.OnFeltMuted, modifier = Modifier.size(14.dp))
    }
}

/** Every emoji, for the top tier: tap to put it in your bar or take it out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiPickerDialog(bar: List<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = TableColors.FeltDark, contentColor = TableColors.OnFelt, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp).widthIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.skins_emoji_pick), fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (emoji in bar) EmojiChip(emoji, selected = true, locked = false) { onToggle(emoji) }
                }
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for ((_, emojis) in EmojiCatalog.GROUPS) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (emoji in emojis) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (emoji in bar) TableColors.TurnGlow.copy(alpha = 0.35f) else Color.Transparent)
                                        .clickable { onToggle(emoji) },
                                    contentAlignment = Alignment.Center,
                                ) { Text(emoji, fontSize = 22.sp) }
                            }
                        }
                        FeltDivider()
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
                ) { Text(stringResource(R.string.ok), fontWeight = FontWeight.Bold) }
            }
        }
    }
}
