package com.webdav.player.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioProcessor that enforces a canonical sample rate floor (44,100 Hz).
 *
 * Incoming PCM audio with sample rates strictly below 44,100 Hz (e.g. 22050 Hz, 16000 Hz, 11025 Hz, 8000 Hz)
 * is upsampled to standard 44,100 Hz stereo PCM in-memory.
 * Standard and high-resolution PCM (>= 44,100 Hz) remains 100% passthrough (isActive = false) with zero
 * calculation overhead or quality degradation.
 */
@OptIn(UnstableApi::class)
class CanonicalResamplingAudioProcessor(
    val canonicalSampleRateHz: Int = CANONICAL_SAMPLE_RATE_HZ,
) : AudioProcessor {
    companion object {
        const val CANONICAL_SAMPLE_RATE_HZ: Int = 44_100
    }

    private var sonicProcessor: SonicAudioProcessor? = null
    private var pendingInputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var pendingOutputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var active: Boolean = false
    private var duplicateMonoToStereo: Boolean = false

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded: Boolean = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        pendingInputAudioFormat = inputAudioFormat

        // Passthrough for standard/high-resolution audio (>= 44,100 Hz),
        // non-16-bit PCM streams (e.g. 32-bit Float PCM, 24-bit PCM),
        // or invalid formats to prevent unhandled pipeline crashes.
        if (inputAudioFormat.sampleRate >= canonicalSampleRateHz ||
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount <= 0
        ) {
            active = false
            duplicateMonoToStereo = false
            pendingOutputAudioFormat = AudioFormat.NOT_SET
            sonicProcessor?.reset()
            return AudioFormat.NOT_SET
        }

        active = true
        duplicateMonoToStereo = (inputAudioFormat.channelCount == 1)
        val outputChannelCount = if (duplicateMonoToStereo) 2 else inputAudioFormat.channelCount
        pendingOutputAudioFormat =
            AudioFormat(
                canonicalSampleRateHz,
                outputChannelCount,
                C.ENCODING_PCM_16BIT,
            )

        try {
            val sonic = sonicProcessor ?: SonicAudioProcessor().also { sonicProcessor = it }
            sonic.setOutputSampleRateHz(canonicalSampleRateHz)
            sonic.configure(inputAudioFormat)
            return pendingOutputAudioFormat
        } catch (_: Throwable) {
            // Adaptive fallback to safe passthrough if Sonic configuration fails
            active = false
            duplicateMonoToStereo = false
            pendingOutputAudioFormat = AudioFormat.NOT_SET
            return AudioFormat.NOT_SET
        }
    }

    override fun isActive(): Boolean = active

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active || !inputBuffer.hasRemaining()) return
        val sonic = checkNotNull(sonicProcessor)
        sonic.queueInput(inputBuffer)
    }

    override fun queueEndOfStream() {
        if (!active) return
        inputEnded = true
        sonicProcessor?.queueEndOfStream()
    }

    override fun getOutput(): ByteBuffer {
        if (!active) return EMPTY_BUFFER
        val sonic = sonicProcessor ?: return EMPTY_BUFFER
        if (!duplicateMonoToStereo) {
            return sonic.output
        }
        if (outputBuffer.hasRemaining()) {
            val out = outputBuffer
            outputBuffer = EMPTY_BUFFER
            return out
        }
        val sonicOut = sonic.output
        if (sonicOut.hasRemaining()) {
            val samplesCount = sonicOut.remaining() / 2
            val bytesNeeded = samplesCount * 4
            if (buffer.capacity() < bytesNeeded) {
                buffer = ByteBuffer.allocateDirect(bytesNeeded).order(ByteOrder.nativeOrder())
            } else {
                buffer.clear()
            }
            while (sonicOut.hasRemaining()) {
                val sample = sonicOut.short
                buffer.putShort(sample)
                buffer.putShort(sample)
            }
            buffer.flip()
            return buffer
        }
        return EMPTY_BUFFER
    }

    override fun isEnded(): Boolean {
        if (!active) return true
        val sonic = sonicProcessor
        return inputEnded && (sonic == null || sonic.isEnded) && !outputBuffer.hasRemaining()
    }

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        if (active) {
            sonicProcessor?.flush()
        }
    }

    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        outputBuffer = EMPTY_BUFFER
        pendingInputAudioFormat = AudioFormat.NOT_SET
        pendingOutputAudioFormat = AudioFormat.NOT_SET
        active = false
        duplicateMonoToStereo = false
        inputEnded = false
        sonicProcessor?.reset()
        sonicProcessor = null
    }
}
