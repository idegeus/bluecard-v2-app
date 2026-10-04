package nl.bluecard.app.ui.screens

import androidx.compose.ui.graphics.ImageBitmap

import nl.bluecard.app.ui.components.platformUi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.bluecard.app.R
import nl.bluecard.app.ui.components.AvatarSpec
import nl.bluecard.app.ui.components.FeltSecondaryButton
import nl.bluecard.app.ui.theme.TableColors

/**
 * Choose how you look at the table: an animal on a colour, or your own photo (picked with the system photo
 * picker, no permission needed; you choose the part that shows in the circle).
 */
@Composable
fun AvatarPicker(current: String, onChange: (String) -> Unit) {
    val spec = AvatarSpec.parse(current)
    var color by remember(current) { mutableStateOf((spec as? AvatarSpec.Emoji)?.color ?: AvatarSpec.COLORS[4]) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    val pickPhoto = platformUi().rememberPhotoPicker { picked -> photo = picked }
    photo?.let { picked ->
        AvatarCropDialog(picked, onDone = { avatar -> photo = null; onChange(avatar) }, onCancel = { photo = null })
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (emoji in AvatarSpec.EMOJIS) {
            val chosen = spec is AvatarSpec.Emoji && spec.emoji == emoji
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (chosen) 3.dp else 1.dp, if (chosen) TableColors.TurnGlow else Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable { onChange(AvatarSpec.emoji(emoji, color)) },
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 22.sp) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in AvatarSpec.COLORS) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(if (c == color) 3.dp else 1.dp, if (c == color) TableColors.TurnGlow else Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        color = c
                        if (spec is AvatarSpec.Emoji) onChange(AvatarSpec.emoji(spec.emoji, c))
                    },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeltSecondaryButton(
            stringResource(R.string.avatar_photo),
            onClick = pickPhoto,
            modifier = Modifier.weight(1f),
        )
        if (current.isNotBlank()) {
            FeltSecondaryButton(stringResource(R.string.avatar_reset), onClick = { onChange("") }, modifier = Modifier.weight(1f))
        }
    }
}
