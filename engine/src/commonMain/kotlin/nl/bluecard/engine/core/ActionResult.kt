package nl.bluecard.engine.core

/**
 * Outcome of applying an action to a game state.
 * The engine never throws for invalid player input; it returns [Rejected] with a stable reason code.
 */
sealed interface ActionResult<out S> {
    data class Accepted<S>(val state: S) : ActionResult<S>
    data class Rejected(val reason: String, val detail: String? = null) : ActionResult<Nothing>
}
