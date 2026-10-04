package nl.bluecard.app.ios

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.bluecard.app.platform.AdSlot
import nl.bluecard.app.platform.PlatformAds
import nl.bluecard.app.platform.Sound
import nl.bluecard.app.platform.SoundPlayer
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle

/** No ads on iOS (yet). */
object NoAds : PlatformAds {
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)
    override fun showPrivacyOptions() = Unit
    override fun takeHomeScreenTurn(): Boolean = false

    @Composable
    override fun Banner(slot: AdSlot, modifier: Modifier) = Unit
}

/**
 * The table sounds from the app bundle (sfx_*.wav). The "ambient" audio category mixes with other audio and stays
 * silent when the ring/silent switch is on, like the Android version on silent.
 */
class IosSounds : SoundPlayer {
    override var enabled: () -> Boolean = { true }
    private val players = mutableMapOf<Sound, AVAudioPlayer>()

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryAmbient, null)
        session.setActive(true, null)
    }

    override fun play(sound: Sound) {
        if (!enabled()) return
        val player = players[sound] ?: load(sound)?.also { players[sound] = it } ?: return
        player.currentTime = 0.0
        player.play()
    }

    private fun load(sound: Sound): AVAudioPlayer? {
        val name = when (sound) {
            Sound.CHEAT -> "sfx_cheat"
            Sound.FALSE_ALARM -> "sfx_false_alarm"
            Sound.FORGOT -> "sfx_forgot"
            Sound.BUZZ -> "sfx_buzz"
            Sound.SHUFFLE -> "sfx_shuffle"
            Sound.DRUMROLL -> "sfx_drumroll"
            Sound.BUSTED -> "sfx_busted"
        }
        val url = NSBundle.mainBundle.URLForResource(name, "wav") ?: return null
        return AVAudioPlayer(contentsOfURL = url, error = null).also { it.prepareToPlay() }
    }
}
