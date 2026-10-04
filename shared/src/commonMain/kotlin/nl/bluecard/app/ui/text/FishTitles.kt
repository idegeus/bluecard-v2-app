package nl.bluecard.app.ui.text

import nl.bluecard.app.R
import nl.bluecard.app.res.Resources

/** Kibbeling (Zweeds pesten) ranks the table in fish: Kibbeling first, Sardientje second, … and the Stinkvis last. */
object FishTitles {
    fun title(res: Resources, position: Int, players: Int): String = res.getString(
        when {
            players >= 2 && position == players -> R.string.fish_last
            position == 1 -> R.string.fish_1
            position == 2 -> R.string.fish_2
            position == 3 -> R.string.fish_3
            position == 4 -> R.string.fish_4
            else -> R.string.fish_5
        },
    )

    fun emoji(position: Int, players: Int): String = if (players >= 2 && position == players) "🐡" else "🐟"
}
