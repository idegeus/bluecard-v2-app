import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

// Shared app (Kotlin Multiplatform + Compose Multiplatform): all screens, game UI, settings, storage and the session
// logic, used by the Android app (:app) and the iOS app (iosApp/). Platform specifics (Bluetooth / Multipeer, ads,
// sounds, photo picker) come in through nl.bluecard.app.platform.Platform.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ============================================================================ generated resources
//
// Texts live in src/commonMain/strings/values[-xx]/strings.xml (Android format, "values" = English = fallback) and
// icons in src/commonMain/drawables/*.xml (Android vector drawables with plain paths). They are turned into Kotlin
// (nl.bluecard.app.R plus tables) so Android and iOS read them the same way, synchronously, like Android resources.

val generateResources by tasks.registering {
    val stringsDir = layout.projectDirectory.dir("src/commonMain/strings")
    val drawablesDir = layout.projectDirectory.dir("src/commonMain/drawables")
    val outDir = layout.buildDirectory.dir("generated/bluecardResources/kotlin")
    inputs.dir(stringsDir)
    inputs.dir(drawablesDir)
    outputs.dir(outDir)
    doLast {
        val out = outDir.get().asFile.resolve("nl/bluecard/app").apply { deleteRecursively(); mkdirs() }
        val factory = DocumentBuilderFactory.newInstance()

        fun kotlinString(s: String): String = buildString {
            append('"')
            for (c in s) when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(c)
            }
            append('"')
        }

        // Android string rules: collapse whitespace, then resolve backslash escapes.
        fun androidText(raw: String): String {
            val collapsed = raw.trim().replace(Regex("\\s+"), " ")
            val sb = StringBuilder()
            var i = 0
            while (i < collapsed.length) {
                val c = collapsed[i]
                if (c == '\\' && i + 1 < collapsed.length) {
                    val n = collapsed[i + 1]
                    when (n) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> {
                            sb.append(collapsed.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                } else {
                    if (c != '"') sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }

        class Table(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

        fun readTable(file: File): Table {
            val doc = factory.newDocumentBuilder().parse(file)
            val strings = linkedMapOf<String, String>()
            val plurals = linkedMapOf<String, Map<String, String>>()
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val name = el.getAttribute("name")
                when (el.tagName) {
                    "string" -> strings[name] = androidText(el.textContent)
                    "plurals" -> {
                        val items = linkedMapOf<String, String>()
                        val children = el.getElementsByTagName("item")
                        for (j in 0 until children.length) {
                            val item = children.item(j) as Element
                            items[item.getAttribute("quantity")] = androidText(item.textContent)
                        }
                        plurals[name] = items
                    }
                }
            }
            return Table(strings, plurals)
        }

        val dirs = stringsDir.asFile.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!.sortedBy { it.name }
        val tables = dirs.associate { dir ->
            val lang = if (dir.name == "values") "en" else dir.name.removePrefix("values-")
            lang to readTable(dir.resolve("strings.xml"))
        }
        val base = tables.getValue("en")
        val stringNames = base.strings.keys.toList()
        val pluralNames = base.plurals.keys.toList()
        val quantities = listOf("zero", "one", "two", "few", "many", "other")

        val keywords = setOf("in", "is", "as", "do", "if", "for", "fun", "val", "var", "when", "try", "object", "class", "package", "return", "this", "null", "true", "false", "while", "break", "continue", "typealias", "throw", "super", "interface", "typeof")
        fun ident(n: String) = if (n in keywords) "`$n`" else n

        val drawableFiles = drawablesDir.asFile.listFiles { f -> f.extension == "xml" }!!.sortedBy { it.name }

        out.resolve("R.kt").writeText(buildString {
            appendLine("// GENERATED from src/commonMain/strings and src/commonMain/drawables — do not edit.")
            appendLine("@file:Suppress(\"ObjectPropertyName\", \"unused\")")
            appendLine("package nl.bluecard.app")
            appendLine()
            appendLine("object R {")
            appendLine("    object string {")
            stringNames.forEachIndexed { i, n -> appendLine("        const val ${ident(n)}: Int = $i") }
            appendLine("    }")
            appendLine("    object plurals {")
            pluralNames.forEachIndexed { i, n -> appendLine("        const val ${ident(n)}: Int = $i") }
            appendLine("    }")
            appendLine("    object drawable {")
            drawableFiles.forEachIndexed { i, f -> appendLine("        const val ${ident(f.nameWithoutExtension)}: Int = $i") }
            appendLine("    }")
            appendLine("}")
        })

        val langObjects = mutableListOf<Pair<String, String>>()
        for ((lang, table) in tables) {
            val obj = "Strings" + lang.replaceFirstChar { it.uppercase() }
            langObjects += lang to obj
            out.resolve("$obj.kt").writeText(buildString {
                appendLine("// GENERATED — do not edit.")
                appendLine("package nl.bluecard.app")
                appendLine()
                appendLine("internal object $obj {")
                appendLine("    val strings: Array<String> = arrayOf(")
                for (n in stringNames) {
                    // Missing translations fall back to English.
                    appendLine("        ${kotlinString(table.strings[n] ?: base.strings.getValue(n))},")
                }
                appendLine("    )")
                appendLine("    /** Per plural: texts for ${quantities.joinToString()} (null = not given). */")
                appendLine("    val plurals: Array<Array<String?>> = arrayOf(")
                for (n in pluralNames) {
                    val items = table.plurals[n] ?: base.plurals.getValue(n)
                    appendLine("        arrayOf(" + quantities.joinToString { q -> items[q]?.let(::kotlinString) ?: "null" } + "),")
                }
                appendLine("    )")
                appendLine("}")
            })
        }
        out.resolve("StringTables.kt").writeText(buildString {
            appendLine("// GENERATED — do not edit.")
            appendLine("package nl.bluecard.app")
            appendLine()
            appendLine("internal object StringTables {")
            appendLine("    val languages: List<String> = listOf(" + langObjects.joinToString { kotlinString(it.first) } + ")")
            appendLine("    fun strings(lang: String): Array<String> = when (lang) {")
            langObjects.filter { it.first != "en" }.forEach { (l, o) -> appendLine("        ${kotlinString(l)} -> $o.strings") }
            appendLine("        else -> StringsEn.strings")
            appendLine("    }")
            appendLine("    fun plurals(lang: String): Array<Array<String?>> = when (lang) {")
            langObjects.filter { it.first != "en" }.forEach { (l, o) -> appendLine("        ${kotlinString(l)} -> $o.plurals") }
            appendLine("        else -> StringsEn.plurals")
            appendLine("    }")
            appendLine("}")
        })

        out.resolve("DrawableTables.kt").writeText(buildString {
            appendLine("// GENERATED — do not edit.")
            appendLine("package nl.bluecard.app")
            appendLine()
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine("import androidx.compose.ui.graphics.SolidColor")
            appendLine("import androidx.compose.ui.graphics.vector.ImageVector")
            appendLine("import androidx.compose.ui.graphics.vector.addPathNodes")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine()
            appendLine("internal object DrawableTables {")
            appendLine("    private val cache = HashMap<Int, ImageVector>()")
            appendLine("    fun vector(id: Int): ImageVector = cache.getOrPut(id) { build(id) }")
            appendLine("    private fun build(id: Int): ImageVector = when (id) {")
            drawableFiles.forEachIndexed { i, f ->
                val doc = factory.newDocumentBuilder().parse(f).documentElement
                fun attr(e: Element, n: String) = e.getAttribute("android:$n")
                val w = attr(doc, "width").removeSuffix("dp")
                val h = attr(doc, "height").removeSuffix("dp")
                appendLine("        $i -> ImageVector.Builder(${kotlinString(f.nameWithoutExtension)}, ${w}f.dp, ${h}f.dp, ${attr(doc, "viewportWidth")}f, ${attr(doc, "viewportHeight")}f)")
                val paths = doc.getElementsByTagName("path")
                for (j in 0 until paths.length) {
                    val p = paths.item(j) as Element
                    val color = attr(p, "fillColor").removePrefix("#").let { if (it.length == 6) "FF$it" else it }.ifEmpty { "FF000000" }
                    appendLine("            .addPath(addPathNodes(${kotlinString(attr(p, "pathData"))}), fill = SolidColor(Color(0x${color}L)))")
                }
                appendLine("            .build()")
            }
            appendLine("        else -> error(\"unknown drawable \$id\")")
            appendLine("    }")
            appendLine("}")
        })
    }
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "BlueCardShared"
            isStatic = true
        }
        target.compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.optIn.addAll("kotlinx.cinterop.ExperimentalForeignApi", "kotlinx.cinterop.BetaInteropApi")
            }
        }
    }

    compilerOptions {
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlin.uuid.ExperimentalUuidApi",
            "kotlin.io.encoding.ExperimentalEncodingApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateResources)
            dependencies {
                api(project(":multiplayer"))
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.ui)
                api(libs.cmp.material.icons.core)
                api(libs.jb.lifecycle.viewmodel.compose)
                api(libs.jb.lifecycle.runtime.compose)
                api(libs.jb.navigation.compose)
                api(libs.androidx.datastore.preferences.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.atomicfu)
            }
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
        }
    }
}

android {
    namespace = "nl.bluecard.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
