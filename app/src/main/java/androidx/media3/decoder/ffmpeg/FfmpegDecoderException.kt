package androidx.media3.decoder.ffmpeg

import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException

@UnstableApi
class FfmpegDecoderException : DecoderException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)
}
