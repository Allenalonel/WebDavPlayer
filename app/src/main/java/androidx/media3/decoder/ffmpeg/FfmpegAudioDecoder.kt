package androidx.media3.decoder.ffmpeg

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer

@UnstableApi
class FfmpegAudioDecoder(
    val format: Format,
    numInputBuffers: Int = 16,
    numOutputBuffers: Int = 16,
    initialInputBufferSize: Int = 9600,
    val outputFloat: Boolean = false
) : SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>(
    @Suppress("UNCHECKED_CAST")
    arrayOfNulls<DecoderInputBuffer>(numInputBuffers) as Array<DecoderInputBuffer>,
    @Suppress("UNCHECKED_CAST")
    arrayOfNulls<SimpleDecoderOutputBuffer>(numOutputBuffers) as Array<SimpleDecoderOutputBuffer>
) {
    val codecName: String = requireNotNull(FfmpegLibrary.getCodecName(requireNotNull(format.sampleMimeType))) {
        "Unsupported MIME type ${format.sampleMimeType}"
    }

    val channelCount: Int = if (format.channelCount != Format.NO_VALUE && format.channelCount > 0) {
        format.channelCount
    } else {
        2
    }

    val sampleRate: Int = if (format.sampleRate != Format.NO_VALUE && format.sampleRate > 0) {
        format.sampleRate
    } else {
        44100
    }

    val encoding: Int = if (outputFloat) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT

    init {
        setInitialInputBufferSize(initialInputBufferSize)
    }

    override fun getName(): String = "ffmpeg:$codecName"

    override fun createInputBuffer(): DecoderInputBuffer {
        return DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
    }

    override fun createOutputBuffer(): SimpleDecoderOutputBuffer {
        return SimpleDecoderOutputBuffer { releaseOutputBuffer(it) }
    }

    override fun createUnexpectedDecodeException(error: Throwable): FfmpegDecoderException {
        return FfmpegDecoderException("Unexpected decode error in $codecName", error)
    }

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: SimpleDecoderOutputBuffer,
        reset: Boolean
    ): FfmpegDecoderException? {
        if (inputBuffer.isEndOfStream) {
            outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
            return null
        }
        val inputData = inputBuffer.data
        if (inputData != null && inputData.hasRemaining()) {
            val length = inputData.remaining()
            val out = outputBuffer.init(inputBuffer.timeUs, length)
            out.put(inputData)
        }
        return null
    }
}
