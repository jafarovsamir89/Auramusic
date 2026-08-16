package az.simplesoft.aura.playback

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
internal class AuraRenderersFactory(
    context: Context,
    private val equalizerProcessor: AuraEqualizerAudioProcessor
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(equalizerProcessor))
        .build()
}
