package az.simplesoft.aura.playback

import android.media.audiofx.Equalizer
import az.simplesoft.aura.assistant.EqualizerPreset

/** Device EQ attached to the active Media3 audio session. */
class AudioEffectsController {
    private var equalizer: Equalizer? = null
    private var sessionId: Int = 0
    private var requestedPreset: EqualizerPreset = EqualizerPreset.FLAT
    private var enabled = false

    fun setSessionId(value: Int) {
        if (value <= 0 || value == sessionId) return
        sessionId = value
        recreate()
    }

    fun apply(preset: EqualizerPreset) {
        requestedPreset = preset
        enabled = preset != EqualizerPreset.FLAT
        recreate()
    }

    fun disable() {
        enabled = false
        equalizer?.enabled = false
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        sessionId = 0
    }

    private fun recreate() {
        if (sessionId <= 0) return
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching {
            Equalizer(0, sessionId).also { effect ->
                effect.enabled = enabled
                val range = effect.bandLevelRange
                val bands = effect.numberOfBands.toInt()
                for (index in 0 until bands) {
                    val value = gainFor(requestedPreset, index, bands)
                    effect.setBandLevel(index.toShort(), value.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
                equalizer = effect
            }
        }
    }

    private fun gainFor(preset: EqualizerPreset, band: Int, count: Int): Int {
        val normalized = if (count <= 1) 0f else band.toFloat() / (count - 1)
        return when (preset) {
            EqualizerPreset.FLAT -> 0
            EqualizerPreset.BASS -> if (normalized < .45f) 650 else if (normalized > .8f) -150 else 250
            EqualizerPreset.VOCAL -> if (normalized in .3f.. .75f) 500 else -100
            EqualizerPreset.ROCK -> when {
                normalized < .25f -> 450
                normalized > .75f -> 550
                else -> -150
            }
            EqualizerPreset.ACOUSTIC -> if (normalized in .2f.. .7f) 350 else 100
        }
    }
}
