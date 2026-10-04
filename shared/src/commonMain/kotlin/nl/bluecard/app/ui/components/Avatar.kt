package nl.bluecard.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.io.encoding.Base64

/**
 * A player's avatar as a small string that travels with the player over Bluetooth:
 * "emoji:🦊|#RRGGBB" (an emoji on a colour) or "photo:<base64 JPEG>" (a small square photo). Empty = initial.
 */
sealed interface AvatarSpec {
    data class Emoji(val emoji: String, val color: Color) : AvatarSpec
    data class Photo(val base64: String) : AvatarSpec

    companion object {
        val EMOJIS = listOf("🦊", "🐻", "🐼", "🐸", "🦁", "🐯", "🐵", "🐧", "🦉", "🐙", "🦄", "🐢", "🐶", "🐱", "🐨", "🐲")
        val COLORS = listOf(
            Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
            Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF6D4C41), Color(0xFF37474F),
        )

        fun emoji(emoji: String, color: Color): String = "emoji:$emoji|#" + color.toArgb().toUInt().toString(16).uppercase().padStart(8, '0')

        fun parse(spec: String?): AvatarSpec? {
            if (spec.isNullOrBlank()) return null
            return when {
                spec.startsWith("emoji:") -> {
                    val body = spec.removePrefix("emoji:")
                    val emoji = body.substringBefore('|')
                    val color = runCatching { Color(body.substringAfter('|').removePrefix("#").toLong(16)) }.getOrNull()
                    if (emoji.isBlank() || color == null) null else Emoji(emoji, color)
                }
                spec.startsWith("photo:") -> Photo(spec.removePrefix("photo:"))
                else -> null
            }
        }
    }
}

/** Decoding photo avatars (making them is platform specific, see PlatformUi.rememberAvatarPhotoPicker). */
object AvatarPhotos {
    /** Set by the AppContainer: decodes JPEG/PNG bytes on this platform. */
    var decoder: (ByteArray) -> ImageBitmap? = { null }

    private const val CACHE_SIZE = 24
    private val cache = LinkedHashMap<String, ImageBitmap>()

    fun bitmap(photo: AvatarSpec.Photo): ImageBitmap? {
        cache[photo.base64]?.let { return it }
        val bytes = runCatching { Base64.decode(photo.base64) }.getOrNull() ?: return null
        val bitmap = decoder(bytes) ?: return null
        if (cache.size >= CACHE_SIZE) cache.remove(cache.keys.first())
        cache[photo.base64] = bitmap
        return bitmap
    }

    /** Wraps a small square JPEG as an avatar string. */
    fun encode(jpeg: ByteArray): String = "photo:" + Base64.encode(jpeg)
}
