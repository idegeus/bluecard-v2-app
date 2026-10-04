package nl.bluecard.app.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import nl.bluecard.app.DrawableTables
import nl.bluecard.app.StringTables

/**
 * The app texts (generated from src/commonMain/strings into [nl.bluecard.app.R]), looked up synchronously like Android
 * resources, on Android and iOS alike. [language] is the effective language; changing it recomposes every text.
 */
object Resources {
    /** Supported languages; anything else shows English. */
    val languages: List<String> get() = StringTables.languages

    var language: String by mutableStateOf("en")
        private set

    fun useLanguage(tag: String) {
        language = tag.substringBefore('-').substringBefore('_').lowercase().takeIf { it in StringTables.languages } ?: "en"
    }

    fun getString(id: Int): String = StringTables.strings(language)[id]

    fun getString(id: Int, vararg formatArgs: Any?): String = grammar(format(getString(id), formatArgs))

    fun getQuantityString(id: Int, quantity: Int): String {
        val forms = StringTables.plurals(language)[id]
        val index = QUANTITIES.indexOf(PluralRules.category(language, quantity))
        return forms[index] ?: forms[QUANTITIES.lastIndex] ?: ""
    }

    fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String =
        grammar(format(getQuantityString(id, quantity), formatArgs))

    /**
     * Event texts put "Jij" where a name goes (Dutch only, see GameTexts.selfName). Most verbs then still fit
     * ("Jij speelt"), but "zijn" and "hebben" do not: "Jij is" → "Jij bent", "Jij heeft" → "Jij hebt".
     */
    private fun grammar(text: String): String {
        if (language != "nl" || !text.contains("Jij ")) return text
        return text.replace(DUTCH_IS, "Jij bent").replace(DUTCH_HEEFT, "Jij hebt")
    }

    private val DUTCH_IS = Regex("\\bJij is\\b")
    private val DUTCH_HEEFT = Regex("\\bJij heeft\\b")

    private val QUANTITIES = listOf("zero", "one", "two", "few", "many", "other")

    /**
     * Android-style formatting for the placeholders our texts use: `%1$s`, `%2$d`, plain `%s` / `%d` (taken in
     * order) and `%%`.
     */
    fun format(template: String, args: Array<out Any?>): String {
        val out = StringBuilder(template.length + 16)
        var next = 0
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c != '%' || i + 1 >= template.length) {
                out.append(c)
                i++
                continue
            }
            if (template[i + 1] == '%') {
                out.append('%')
                i += 2
                continue
            }
            var j = i + 1
            var index = -1
            while (j < template.length && template[j].isDigit()) j++
            if (j < template.length && template[j] == '$' && j > i + 1) {
                index = template.substring(i + 1, j).toInt() - 1
                j++
            } else {
                j = i + 1
            }
            if (j < template.length && (template[j] == 's' || template[j] == 'd')) {
                val arg = args.getOrNull(if (index >= 0) index else next++)
                out.append(arg?.toString() ?: "null")
                i = j + 1
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

/** CLDR plural categories for our languages. */
object PluralRules {
    fun category(language: String, n: Int): String = when (language) {
        "fr" -> when {
            n == 0 || n == 1 -> "one"
            n != 0 && n % 1_000_000 == 0 -> "many"
            else -> "other"
        }
        "ca" -> when {
            n == 1 -> "one"
            n != 0 && n % 1_000_000 == 0 -> "many"
            else -> "other"
        }
        else -> if (n == 1) "one" else "other"
    }
}

/** Same name as on Android, so screens read `LocalResources.current.getString(...)`. */
val LocalResources = staticCompositionLocalOf { Resources }

@Composable
fun stringResource(id: Int): String = Resources.getString(id)

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String = Resources.getString(id, *formatArgs)

@Composable
fun pluralStringResource(id: Int, count: Int): String = Resources.getQuantityString(id, count)

@Composable
fun pluralStringResource(id: Int, count: Int, vararg formatArgs: Any): String = Resources.getQuantityString(id, count, *formatArgs)

fun vectorResource(id: Int): ImageVector = DrawableTables.vector(id)

@Composable
fun painterResource(id: Int): Painter = rememberVectorPainter(vectorResource(id))
