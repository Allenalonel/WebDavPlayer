package com.webdav.player.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class CanonicalResamplingAudioProcessorTest {
    private lateinit var processor: CanonicalResamplingAudioProcessor

    @Before
    fun setUp() {
        processor = CanonicalResamplingAudioProcessor()
    }

    @Test
    fun configure_withSub44100HzStereo_activatesAndUpsamplesTo44100HzStereo() {
        val inputFormat = AudioFormat(22050, 2, C.ENCODING_PCM_16BIT)
        val outputFormat = processor.configure(inputFormat)

        assertTrue(processor.isActive)
        assertEquals(44100, outputFormat.sampleRate)
        assertEquals(2, outputFormat.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, outputFormat.encoding)
    }

    @Test
    fun configure_withSub44100HzMono_activatesAndUpsamplesTo44100HzStereo() {
        val inputFormat = AudioFormat(16000, 1, C.ENCODING_PCM_16BIT)
        val outputFormat = processor.configure(inputFormat)

        assertTrue(processor.isActive)
        assertEquals(44100, outputFormat.sampleRate)
        assertEquals(2, outputFormat.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, outputFormat.encoding)
    }

    @Test
    fun configure_with44100Hz_remainsInactivePassthrough() {
        val inputFormat = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val outputFormat = processor.configure(inputFormat)

        assertFalse(processor.isActive)
        assertEquals(AudioFormat.NOT_SET, outputFormat)
    }

    @Test
    fun configure_withHighRes96000Hz_remainsInactivePassthrough() {
        val inputFormat = AudioFormat(96000, 2, C.ENCODING_PCM_16BIT)
        val outputFormat = processor.configure(inputFormat)

        assertFalse(processor.isActive)
        assertEquals(AudioFormat.NOT_SET, outputFormat)
    }

    @Test
    fun audioDataProcessing_with22050HzPCM_producesResampledPCMAt44100Hz() {
        val inputFormat = AudioFormat(22050, 2, C.ENCODING_PCM_16BIT)
        processor.configure(inputFormat)
        processor.flush()

        // 22050 frames at stereo 16-bit = 22050 * 2 channels * 2 bytes = 88200 bytes
        val frameCount = 22050
        val inputBytes = frameCount * 4
        val inputBuffer = ByteBuffer.allocateDirect(inputBytes).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            val sample = (i % 1000).toShort()
            inputBuffer.putShort(sample) // Left
            inputBuffer.putShort(sample) // Right
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        processor.queueEndOfStream()

        var totalOutputBytes = 0
        while (!processor.isEnded) {
            val output = processor.output
            if (output.hasRemaining()) {
                totalOutputBytes += output.remaining()
                output.position(output.limit()) // consume
            }
        }

        // Output should be ~44100 frames * 4 bytes = ~176400 bytes (within +/- 5% due to filter delay/padding)
        assertTrue(totalOutputBytes > 160000)
        assertTrue(totalOutputBytes < 190000)
    }

    @Test
    fun audioDataProcessing_with16000HzMono_producesStereoPCMAt44100Hz() {
        val inputFormat = AudioFormat(16000, 1, C.ENCODING_PCM_16BIT)
        processor.configure(inputFormat)
        processor.flush()

        // 16000 frames mono 16-bit = 32000 bytes
        val frameCount = 16000
        val inputBytes = frameCount * 2
        val inputBuffer = ByteBuffer.allocateDirect(inputBytes).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            inputBuffer.putShort((i % 500).toShort())
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        processor.queueEndOfStream()

        var totalOutputBytes = 0
        while (!processor.isEnded) {
            val output = processor.output
            if (output.hasRemaining()) {
                // Must be multiple of 4 bytes (stereo 16-bit frame)
                assertEquals(0, output.remaining() % 4)
                totalOutputBytes += output.remaining()
                output.position(output.limit()) // consume
            }
        }

        // Output was converted from mono to stereo and resampled from 16000 to 44100 Hz
        // 1 second of 44100Hz stereo = ~176400 bytes
        assertTrue(totalOutputBytes > 160000)
        assertTrue(totalOutputBytes < 190000)
    }
}
