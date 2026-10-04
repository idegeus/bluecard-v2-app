package nl.bluecard.app.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import nl.bluecard.app.BlueCardApp

/** Android Auto entry point: one screen that shows the table of this phone. */
class TableCarAppService : CarAppService() {

    // Debug builds only (see the debug manifest), so any Android Auto host may connect.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = TableCarScreen(carContext, (application as BlueCardApp).container)
    }
}
