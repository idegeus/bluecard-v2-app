package nl.bluecard.app.platform

import kotlin.time.Clock

/** Wall-clock time in milliseconds since 1970 (common replacement for System.currentTimeMillis()). */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
