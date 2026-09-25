package androidx.media3.decoder.ffmpeg

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer

@UnstableApi
class FfmpegAudioRenderer : DecoderAudioRenderer<FfmpegAudioDecoder> {

    companion object {
        private const val TAG = "FfmpegAudioRenderer"
        private const val NUM_BUFFERS = 16
        private const val DEFAULT_INPUT_BUFFER_SIZE = 9600
    }

    constructor() : super()

    constructor(
        eventHandler: Handler?,
        eventListener: AudioRendererEventListener?,
        vararg audioProcessors: AudioProcessor
    ) : super(eventHandler, eventListener, *audioProcessors)

    constructor(
        eventHandler: Handler?,
        eventListener: AudioRendererEventListener?,
        audioSink: AudioSink
    ) : super(eventHandler, eventListener, audioSink)

    override fun getName(): String = TAG

    override fun supportsFormatInternal(format: Format): Int {
        val mimeType = format.sampleMimeType ?: return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        if (!FfmpegLibrary.isAvailable() || !MimeTypes.isAudio(mimeType)) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        if (!FfmpegLibrary.supportsFormat(mimeType)) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
        }
        val sinkFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(if (format.channelCount != Format.NO_VALUE) format.channelCount else 2)
            .setSampleRate(if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 44100)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        return if (sinkSupportsFormat(sinkFormat)) {
            RendererCapabilities.create(C.FORMAT_HANDLED)
        } else {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
        }
    }

    public override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): FfmpegAudioDecoder {
        return FfmpegAudioDecoder(
            format = format,
            numInputBuffers = NUM_BUFFERS,
            numOutputBuffers = NUM_BUFFERS,
            initialInputBufferSize = DEFAULT_INPUT_BUFFER_SIZE,
            outputFloat = shouldOutputFloat(format)
        )
    }

    override fun getOutputFormat(decoder: FfmpegAudioDecoder): Format {
        return Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(decoder.channelCount)
            .setSampleRate(decoder.sampleRate)
            .setPcmEncoding(decoder.encoding)
            .build()
    }

    private fun shouldOutputFloat(format: Format): Boolean {
        val floatFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(if (format.channelCount != Format.NO_VALUE) format.channelCount else 2)
            .setSampleRate(if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 44100)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .build()
        return sinkSupportsFormat(floatFormat) && format.pcmEncoding == C.ENCODING_PCM_FLOAT
    }
}
