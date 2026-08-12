package az.simplesoft.aura.assistant.gemini

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean

class GeminiAudioOutput(context: Context? = null) {
    private val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    private val focusRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && audioManager != null) {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
    } else null
    private var track: AudioTrack? = null
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        focusRequest?.let { audioManager?.requestAudioFocus(it) }
        val min = AudioTrack.getMinBufferSize(
            GeminiLiveConfig.OUTPUT_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(GeminiLiveConfig.OUTPUT_RATE / 5)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(GeminiLiveConfig.OUTPUT_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track?.play()
    }

    fun write(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        if (!started.get()) start()
        track?.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
    }

    fun clear() {
        track?.pause()
        track?.flush()
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.release() }
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        track = null
    }
}
