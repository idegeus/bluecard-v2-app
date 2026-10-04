package nl.bluecard.app.settings

/** The app languages offered in the settings; storing and applying the choice is up to the platform. */
object AppLanguage {
    /** Language tags; "" = follow the phone. */
    val OPTIONS = listOf("", "nl", "en", "fr", "de", "ca", "eu")

    /** Each language's name in that language. */
    fun nativeName(tag: String): String = when (tag) {
        "nl" -> "Nederlands"
        "en" -> "English"
        "fr" -> "Français"
        "de" -> "Deutsch"
        "ca" -> "Català"
        "eu" -> "Euskara"
        else -> tag
    }
}
