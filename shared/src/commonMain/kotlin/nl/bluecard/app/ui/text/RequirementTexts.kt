package nl.bluecard.app.ui.text

import nl.bluecard.app.R
import nl.bluecard.app.res.Resources
import nl.bluecard.app.stats.Progress
import nl.bluecard.app.stats.Requirement

/** "3/10 · Kibbeling won": what an unlock asks and how far you are. */
object RequirementTexts {
    fun progress(res: Resources, requirement: Requirement, progress: Progress): String {
        val now = requirement.current(progress).coerceAtMost(requirement.target)
        val what = when (requirement) {
            Requirement.Free -> return ""
            is Requirement.Wins -> res.getString(R.string.req_wins)
            is Requirement.Played -> res.getString(R.string.req_played)
            is Requirement.GameWins -> res.getString(R.string.req_game_wins, GameTexts.gameName(res, requirement.game))
            is Requirement.GamePlayed -> res.getString(R.string.req_game_played, GameTexts.gameName(res, requirement.game))
            is Requirement.RightCalls -> res.getString(R.string.req_right_calls)
            is Requirement.Ninja -> res.getString(R.string.req_ninja)
        }
        return res.getString(R.string.req_line, now, requirement.target, what)
    }
}
