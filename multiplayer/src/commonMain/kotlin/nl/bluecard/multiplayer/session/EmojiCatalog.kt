package nl.bluecard.multiplayer.session

/**
 * Every emoji that can be sent as a reaction, grouped for the picker. The host only relays reactions from this list,
 * so a reaction can never carry arbitrary text.
 */
object EmojiCatalog {
    val GROUPS: List<Pair<String, List<String>>> = listOf(
        "faces" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶",
            "😏", "😒", "🙄", "😬", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🥵", "🥶",
            "🥴", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺",
            "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤",
            "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "🤖", "😺", "🙈",
            "🙉", "🙊",
        ),
        "hands" to listOf(
            "👍", "👎", "👏", "🙌", "👐", "🤝", "🙏", "✌️", "🤞", "🤟", "🤘", "👌", "🤌", "👈", "👉", "👆",
            "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤙", "💪", "🫶", "👀", "🧠", "🫡", "🥷",
        ),
        "hearts" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💯", "💥", "💫", "💤", "💨", "🔥", "✨", "⭐", "🌟"),
        "animals" to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
            "🐧", "🐦", "🦆", "🦉", "🦄", "🐝", "🐛", "🦋", "🐌", "🐢", "🐍", "🦖", "🐙", "🦑", "🦀", "🐠",
            "🐟", "🐡", "🐬", "🐳", "🦈", "🐊", "🦓", "🦒", "🐘", "🦔", "🦥",
        ),
        "food" to listOf(
            "🍎", "🍋", "🍌", "🍉", "🍇", "🍓", "🍒", "🍑", "🍍", "🥑", "🌶️", "🥕", "🌽", "🥐", "🧀", "🍟",
            "🍕", "🌭", "🍔", "🌮", "🍿", "🍦", "🍩", "🍪", "🎂", "🍫", "🍬", "☕", "🍺", "🍻", "🥂", "🍷",
        ),
        "fun" to listOf(
            "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🥈", "🥉", "👑", "💎", "💰", "🃏", "🎲", "🎯", "🎰", "🎮",
            "🚀", "💣", "🧨", "🚨", "🔔", "📣", "⏰", "⌛", "🍀", "🌈", "☀️", "🌙", "⚡", "❄️", "☔", "🌊", "🗿",
            "♠️", "♥️", "♦️", "♣️", "❓", "❗", "✅", "❌", "🆗", "🆒", "🔝", "🙃",
        ),
    )

    val ALL: Set<String> = GROUPS.flatMap { it.second }.toSet()

    fun isAllowed(emoji: String?): Boolean = emoji != null && emoji in ALL
}

/** A chat line at the table, numbered by the host. */
data class ChatMessage(val id: Long, val fromId: String, val fromName: String, val text: String)

object ChatLimits {
    const val MAX_LENGTH = 140
    const val MESSAGES_PER_10S = 5
    const val KEEP = 100

    /** One line, trimmed, at most [MAX_LENGTH] characters; null when nothing is left. */
    fun clean(text: String): String? =
        text.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(MAX_LENGTH).takeIf { it.isNotBlank() }
}
