package nl.bluecard.engine.core

import nl.bluecard.engine.hartenjagen.HjModule
import nl.bluecard.engine.pesten.PsModule
import nl.bluecard.engine.presidenten.PrModule
import nl.bluecard.engine.zweedspesten.ZpModule

/** Registry of all card games this build supports. */
object GameCatalog {
    val all: List<GameInfo> = listOf(ZpModule.info, PsModule.info, PrModule.info, HjModule.info)

    fun find(id: String): GameInfo? = all.firstOrNull { it.id == id }
}
