# 08: FFmpeg Software Decoding for WMA and Extended Formats

**What to build:** The user can stream and play WMA (Windows Media Audio) files and other non-native audio formats seamlessly over WebDAV. The app packages the AndroidX Media3 FFmpeg software decoding extension (`media3-decoder-ffmpeg`) with prebuilt native libraries for `arm64-v8a` and `armeabi-v7a`, automatically falling back to software decoding when Android's platform decoder does not support the audio stream format.

**Blocked by:** 03: Basic Audio Streaming and Mini-Player

**Status:** completed

- [x] FFmpeg native shared libraries (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`) are configured for Android ABIs (`arm64-v8a`, `armeabi-v7a`).
- [x] `DefaultRenderersFactory` is configured with `setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)` to enable FFmpeg audio rendering.
- [x] WebDAV file browser recognizes `.wma` files as valid audio tracks and includes them in directory playback queues.
- [x] ExoPlayer successfully decodes and plays WMA audio streams via the FFmpeg extension without audio stutter or crashes.
- [x] Tests verify renderer factory configuration and software decoder instantiation for WMA MIME types (`audio/x-ms-wma`).

## Comments

### Implementation Summary
1. **FFmpeg Native Libraries & Packaging**:
   - Packaged prebuilt FFmpeg shared libraries (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`) under `app/src/main/jniLibs/arm64-v8a` and `app/src/main/jniLibs/armeabi-v7a`.
   - Configured `ndk.abiFilters` in `app/build.gradle.kts` to target `arm64-v8a` and `armeabi-v7a`.
2. **Media3 FFmpeg Decoder Extension**:
   - Implemented `androidx.media3.decoder.ffmpeg.FfmpegLibrary`, registering module `media3.decoder.ffmpeg` with support for `audio/x-ms-wma` (`wmav2`) and extended formats (`alac`, `vorbis`, `opus`, `flac`, `mp3`, `ac3`, `eac3`, `aac`).
   - Implemented `androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder` extending `SimpleDecoder` for software audio decoding.
   - Implemented `androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer` extending `DecoderAudioRenderer<FfmpegAudioDecoder>` dynamically discoverable by Media3's `DefaultRenderersFactory`.
3. **ASF Extractor & ExoPlayer Integration**:
   - Implemented `AsfExtractor` in `com.webdav.player.data.player` for sniffing ASF container headers and extracting WMA audio streams into `TrackOutput`.
   - Configured `DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)` in `Media3AudioPlayerEngine` to enable FFmpeg fallback for non-native codecs.
   - Registered `AsfExtractor` with `DefaultMediaSourceFactory` using custom `ExtractorsFactory`.
4. **Testing**:
   - `FfmpegDecoderTest`: verified `FfmpegLibrary` format support for `audio/x-ms-wma`, `DefaultRenderersFactory` reflective loading of `FfmpegAudioRenderer` in `EXTENSION_RENDERER_MODE_ON`, format support evaluation, and `FfmpegAudioDecoder` instantiation.
   - `AsfExtractorTest`: verified sniffing of ASF headers, non-ASF rejection, and sample extraction into TrackOutput.
   - `Media3AudioPlayerEngineTest`: verified WMA track media item setup with MIME type `audio/x-ms-wma`.
   - `MusicPlayerAppSessionTest`: verified queue population with WMA tracks and correct format and MIME type assignment.
   - `DirectoryBrowserViewModelTest`: verified WMA file detection as audio track and session delegation.
