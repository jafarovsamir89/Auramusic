package az.simplesoft.aura.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertFalse
import org.junit.Test

class AuraEqualizerAudioProcessorTest {
    @Test
    fun bassPresetChangesPcmSamplesComparedWithFlat() {
        val flat = render(EqualizerPresetForTest.FLAT)
        val bass = render(EqualizerPresetForTest.BASS)
        assertFalse(flat.contentEquals(bass))
    }

    private fun render(preset: EqualizerPresetForTest): ByteArray {
        val processor = AuraEqualizerAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        processor.setPreset(preset.name.lowercase())
        val input = ByteBuffer.allocate(44_100 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(44_100) { index -> input.putShort((kotlin.math.sin(index * .08) * 8_000).toInt().toShort()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        val bytes = ByteArray(output.remaining())
        output.get(bytes)
        return bytes
    }

    private enum class EqualizerPresetForTest { FLAT, BASS }
}
