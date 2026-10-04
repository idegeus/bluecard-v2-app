package nl.bluecard.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Sets the colour of the status/navigation bar icons for the current screen.
 *
 * @param darkBackground true for screens drawn on the dark card-table felt (light icons needed).
 */
@Composable
fun SystemBarIcons(darkBackground: Boolean) = platformUi().SystemBarIcons(darkBackground)
