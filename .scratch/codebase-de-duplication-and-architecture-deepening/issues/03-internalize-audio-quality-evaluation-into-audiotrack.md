# 03: Internalize Audio Quality Evaluation into AudioTrack

**What to build:** First-class audio quality evaluation capabilities built directly into `AudioTrack` and `RemoteFile` models, eliminating UI-bound business algorithms and enabling cross-screen audio quality display.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Audio quality level categorization (`LOSSLESS`, `HIGH_QUALITY`, `STANDARD`, `COMPRESSED`) is defined as a domain enumeration.
- [ ] Bitrate extraction from filename patterns and bitrate estimation from file size and track duration are internalized into `AudioTrack` and `RemoteFile`.
- [ ] Quality badge data and summary formatting become intrinsic domain properties of `AudioTrack`.
- [ ] `AudioQualityBadgeHelper` is pruned into a lightweight presentation composable mapping domain quality levels to Material Design 3 Badge styling.
- [ ] Unit tests for quality evaluation run cleanly against domain models without requiring Android UI framework context.
