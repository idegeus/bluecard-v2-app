package nl.bluecard.app.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import nl.bluecard.app.res.painterResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.bluecard.app.R
import nl.bluecard.app.ui.theme.TableColors

/*
 * The "card table" look of the start screen, as reusable building blocks: dark green felt, light text,
 * translucent panels and the yellow primary button. Used by all screens on the way into a game.
 */

/** Full screen on felt: title bar, scrollable content and an optional bar pinned to the bottom. */
@Composable
fun FeltScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SystemBarIcons(darkBackground = true)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TableColors.FeltLight, TableColors.Felt, TableColors.FeltDark))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = TableColors.OnFelt,
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Text(
                    title,
                    color = TableColors.OnFelt,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(48.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
            if (bottomBar != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        content = bottomBar,
                    )
                }
            }
        }
        if (snackbarHostState != null) {
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
        }
    }
}

/** Translucent panel lying on the felt, like the opponent panels at the table. */
@Composable
fun FeltPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = TableColors.FeltDark.copy(alpha = 0.55f),
        contentColor = TableColors.OnFelt,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, TableColors.Ink.copy(alpha = 0.14f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (title != null) {
                        Text(
                            title.uppercase(),
                            color = TableColors.OnFeltMuted,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    trailing?.invoke(this)
                }
            }
            content()
        }
    }
}

enum class BannerTone { INFO, SUCCESS, ERROR }

/** Coloured message strip on the felt. */
@Composable
fun FeltBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.INFO,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val (background, border) = when (tone) {
        BannerTone.INFO -> TableColors.Ink.copy(alpha = 0.08f) to TableColors.Ink.copy(alpha = 0.18f)
        BannerTone.SUCCESS -> TableColors.FeltLight.copy(alpha = 0.7f) to TableColors.Highlight.copy(alpha = 0.6f)
        BannerTone.ERROR -> TableColors.Danger.copy(alpha = 0.22f) to TableColors.Danger.copy(alpha = 0.7f)
    }
    Surface(
        color = background,
        contentColor = TableColors.OnFelt,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.size(10.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}

/** The big yellow button of the start screen. */
@Composable
fun FeltPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TableColors.TurnGlow,
            contentColor = TableColors.CardBlack,
            disabledContainerColor = TableColors.Ink.copy(alpha = 0.14f),
            disabledContentColor = TableColors.OnFeltMuted,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

/** Outlined light button for secondary actions on the felt. */
@Composable
fun FeltSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        border = BorderStroke(1.dp, TableColors.Ink.copy(alpha = if (enabled) 0.55f else 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TableColors.OnFelt, disabledContentColor = TableColors.OnFeltMuted),
    ) {
        when {
            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            iconContent != null -> iconContent()
        }
        if (icon != null || iconContent != null) Spacer(Modifier.size(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Switch row in the felt style; the whole row toggles. */
@Composable
fun FeltSwitchRow(
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text, color = TableColors.OnFelt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (detail != null) {
                Text(detail, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TableColors.CardBlack,
                checkedTrackColor = TableColors.TurnGlow,
                uncheckedThumbColor = TableColors.OnFeltMuted,
                uncheckedTrackColor = Color.Black.copy(alpha = 0.25f),
                uncheckedBorderColor = TableColors.OnFeltMuted,
            ),
        )
    }
}

/** Text field colours readable on the felt. */
@Composable
fun feltTextFieldColors() = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TableColors.OnFelt,
            unfocusedTextColor = TableColors.OnFelt,
            focusedBorderColor = TableColors.TurnGlow,
            unfocusedBorderColor = TableColors.Ink.copy(alpha = 0.45f),
            focusedLabelColor = TableColors.TurnGlow,
            unfocusedLabelColor = TableColors.OnFeltMuted,
            cursorColor = TableColors.TurnGlow,
            focusedContainerColor = Color.Black.copy(alpha = 0.15f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.15f),
)

/** A row of round toggle pills; exactly one is selected. */
@Composable
fun <T> FeltSegmented(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) TableColors.TurnGlow else TableColors.Ink.copy(alpha = 0.08f))
                    .border(1.dp, if (isSelected) TableColors.TurnGlow else TableColors.Ink.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .toggleable(value = isSelected, role = Role.RadioButton, onValueChange = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (isSelected) TableColors.CardBlack else TableColors.OnFelt,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

/** Small rounded label, e.g. "Hand: 3". */
@Composable
fun FeltChip(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) TableColors.TurnGlow.copy(alpha = 0.9f) else TableColors.Ink.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (accent) TableColors.CardBlack else TableColors.OnFelt,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Round avatar with the player's initial; colour derived from the name so it stays the same everywhere. */
@Composable
fun PlayerAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isBot: Boolean = false,
    avatar: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val spec = if (isBot) null else AvatarSpec.parse(avatar)
    val palette = AvatarColors
    // Bots get one neutral slate colour so people and bots are told apart at a glance.
    val color = when {
        isBot -> BotAvatarColor
        spec is AvatarSpec.Emoji -> spec.color
        else -> palette[name.hashCode().mod(palette.size)]
    }
    val photo = (spec as? AvatarSpec.Photo)?.let { remember(it) { AvatarPhotos.bitmap(it) } }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))))
            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else if (spec is AvatarSpec.Emoji) {
            Text(spec.emoji, fontSize = (size.value * 0.52f).sp)
        } else if (content != null) {
            content()
        } else if (isBot) {
            Icon(painterResource(R.drawable.ic_smart_toy), null, Modifier.size(size * 0.62f), tint = Color.White)
        } else {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

private val BotAvatarColor = Color(0xFF546E7A)

private val AvatarColors = listOf(
    Color(0xFF1565C0),
    Color(0xFFC62828),
    Color(0xFF6A1B9A),
    Color(0xFFEF6C00),
    Color(0xFF00838F),
    Color(0xFF2E7D32),
    Color(0xFFAD1457),
)

/** Dashed "empty chair" row, optionally clickable (e.g. "Bot toevoegen"). */
@Composable
fun EmptySeat(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = TableColors.Ink.copy(alpha = 0.35f),
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
        }
        Row(
            Modifier.matchParentSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leading?.invoke()
            if (leading != null) Spacer(Modifier.size(8.dp))
            Text(
                text,
                color = if (onClick != null) TableColors.OnFelt else TableColors.OnFeltMuted,
                fontWeight = if (onClick != null) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Row of chips that wraps on small screens. */
@Composable
fun FeltChips(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** Thin divider line on the felt. */
@Composable
fun FeltDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(TableColors.Ink.copy(alpha = 0.12f)))
}
