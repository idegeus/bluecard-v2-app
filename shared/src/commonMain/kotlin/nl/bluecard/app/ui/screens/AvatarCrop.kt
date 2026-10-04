package nl.bluecard.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import nl.bluecard.app.R
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.components.AvatarPhotos
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.theme.TableColors
import kotlin.math.max
import kotlin.math.roundToInt

/** Side of the photo avatar in pixels (small, so lobby messages stay small). */
private const val AVATAR_PX = 160
private const val AVATAR_JPEG_QUALITY = 74
private const val MAX_ZOOM = 6f

/**
 * Cut your avatar out of a photo: drag to move, pinch to zoom; the circle is what the others see.
 * [onDone] gets the avatar string ("photo:…").
 */
@Composable
fun AvatarCropDialog(photo: ImageBitmap, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val platform = appContainer().platform
    var zoom by remember(photo) { mutableFloatStateOf(1f) }
    var offset by remember(photo) { mutableStateOf(Offset.Zero) }
    var boxPx by remember { mutableFloatStateOf(1f) }
    Dialog(onDismissRequest = onCancel) {
        Surface(color = TableColors.FeltDark, contentColor = TableColors.OnFelt, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp).widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.avatar_crop_title), fontWeight = FontWeight.Black)
                Text(stringResource(R.string.avatar_crop_hint), color = TableColors.OnFeltMuted, textAlign = TextAlign.Start)
                BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds()) {
                    val side = constraints.maxWidth.toFloat()
                    boxPx = side
                    // At zoom 1 the photo just covers the square.
                    val base = side / minOf(photo.width, photo.height)
                    fun clamp(o: Offset, z: Float): Offset {
                        val k = base * z
                        val maxX = max(0f, (photo.width * k - side) / 2f)
                        val maxY = max(0f, (photo.height * k - side) / 2f)
                        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                    }
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .pointerInput(photo) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                                    offset = clamp(offset + pan, zoom)
                                }
                            },
                    ) {
                        val k = base * zoom
                        val w = photo.width * k
                        val h = photo.height * k
                        val left = (side - w) / 2f + offset.x
                        val top = (side - h) / 2f + offset.y
                        drawImage(
                            photo,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        )
                        // Darken everything outside the circle.
                        val outside = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, side, side))
                            addOval(androidx.compose.ui.geometry.Rect(0f, 0f, side, side))
                        }
                        drawPath(outside, Color.Black.copy(alpha = 0.55f))
                        drawCircle(Color.White.copy(alpha = 0.9f), radius = side / 2f - 1.5f, style = Stroke(3f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TableColors.OnFelt),
                    ) { Text(stringResource(R.string.cancel), maxLines = 1) }
                    Button(
                        onClick = {
                            val avatar = cropAvatar(photo, boxPx, zoom, offset)
                            platform.encodeJpeg(avatar, AVATAR_JPEG_QUALITY)?.let { onDone(AvatarPhotos.encode(it)) } ?: onCancel()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TableColors.TurnGlow, contentColor = TableColors.CardBlack),
                    ) { Text(stringResource(R.string.avatar_crop_use), fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
    }
}

/** The part of [photo] inside the crop square (of [side] px on screen), as an [AVATAR_PX] square. */
private fun cropAvatar(photo: ImageBitmap, side: Float, zoom: Float, offset: Offset): ImageBitmap {
    val k = side / minOf(photo.width, photo.height) * zoom
    val left = (side - photo.width * k) / 2f + offset.x
    val top = (side - photo.height * k) / 2f + offset.y
    val srcX = (-left / k).coerceIn(0f, photo.width.toFloat())
    val srcY = (-top / k).coerceIn(0f, photo.height.toFloat())
    val srcSide = (side / k).coerceAtMost(minOf(photo.width - srcX, photo.height - srcY))
    val out = ImageBitmap(AVATAR_PX, AVATAR_PX)
    Canvas(out).drawImageRect(
        photo,
        srcOffset = IntOffset(srcX.roundToInt(), srcY.roundToInt()),
        srcSize = IntSize(srcSide.roundToInt().coerceAtLeast(1), srcSide.roundToInt().coerceAtLeast(1)),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(AVATAR_PX, AVATAR_PX),
        paint = Paint().apply { filterQuality = androidx.compose.ui.graphics.FilterQuality.High },
    )
    return out
}
