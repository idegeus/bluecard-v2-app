package nl.bluecard.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import nl.bluecard.app.android.R
import nl.bluecard.app.platform.Sound
import nl.bluecard.app.platform.SoundPlayer

/**
 * Short table sounds (cheat caught, wrong accusation, forgotten "last card", buzz, shuffle, drumroll). Silent when switched off in the settings
 * or when the phone is on silent / vibrate.
 */
class SoundEffects(context: Context) : SoundPlayer {
    override var enabled: () -> Boolean = { true }

    private val audio = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = mapOf(
        Sound.CHEAT to pool.load(context, R.raw.sfx_cheat, 1),
        Sound.FALSE_ALARM to pool.load(context, R.raw.sfx_false_alarm, 1),
        Sound.FORGOT to pool.load(context, R.raw.sfx_forgot, 1),
        Sound.BUZZ to pool.load(context, R.raw.sfx_buzz, 1),
        Sound.SHUFFLE to pool.load(context, R.raw.sfx_shuffle, 1),
        Sound.DRUMROLL to pool.load(context, R.raw.sfx_drumroll, 1),
        Sound.BUSTED to pool.load(context, R.raw.sfx_busted, 1),
    )

    override fun play(sound: Sound) {
        if (!enabled() || audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        ids[sound]?.let { pool.play(it, VOLUME, VOLUME, 1, 0, 1f) }
    }

    private companion object {
        const val VOLUME = 0.8f
    }
}
