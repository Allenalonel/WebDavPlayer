# 08: FFmpeg Software Decoding for WMA and Extended Formats

**What to build:** The user can stream and play WMA (Windows Media Audio) files and other non-native audio formats seamlessly over WebDAV. The app packages the AndroidX Media3 FFmpeg software decoding extension (`media3-decoder-ffmpeg`) with prebuilt native libraries for `arm64-v8a` and `armeabi-v7a`, automatically falling back to software decoding when Android's platform decoder does not support the audio stream format.

**Blocked by:** 03: Basic Audio Streaming and Mini-Player

**Status:** ready-for-agent

- [ ] FFmpeg native shared libraries (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`) are configured for Android ABIs (`arm64-v8a`, `armeabi-v7a`).
- [ ] `DefaultRenderersFactory` is configured with `setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)` to enable FFmpeg audio rendering.
- [ ] WebDAV file browser recognizes `.wma` files as valid audio tracks and includes them in directory playback queues.
- [ ] ExoPlayer successfully decodes and plays WMA audio streams via the FFmpeg extension without audio stutter or crashes.
- [ ] Tests verify renderer factory configuration and software decoder instantiation for WMA MIME types (`audio/x-ms-wma`).
