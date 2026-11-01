Status: completed

# Feature Specification: P1 Cover Art Pipeline & Industrial LRC Normalization

## Problem Statement

During audio playback and library browsing in WebDavPlayer, two critical functional issues degrade the listening experience:

1. **Cover Art Loading Failure & Cache Poisoning**:
   - **Range Truncation**: `TrackMetadataRepositoryImpl` uses a hard-coded 128KB HTTP Range request (`0L..131071L`). High-resolution embedded album covers in MP3 (ID3v2 APIC) and FLAC (PICTURE block) typically range from 200KB to 2MB. In MP3s, image bytes are truncated across the 128KB boundary, saving corrupt files that fail `BitmapFactory.decodeFile()`. In FLAC, `FlacParser` drops artwork whenever `dataLength > remainingBytes`.
   - **Room Cache Poisoning**: Once a track fails metadata resolution or returns null artwork, a row with `coverThumbnailPath = null` is written into the Room database. Subsequent visits filter out cached paths, preventing retry even under stable networks.
   - **Queue Metadata Disconnect**: When tapping a track in the directory browser, `playDirectoryTrack` instantiates `AudioTrack.fromRemoteFile` with `metadata = null`. The Room Flow (`getAllMetadataFlow`) does not re-emit if no DB writes occur, leaving `_sessionState.currentTrack` with `coverThumbnailPath = null` permanently.
   - **Lack of Folder Artwork**: Many NAS/WebDAV music collections store split or lossless tracks without embedded art, instead placing `cover.jpg`, `folder.jpg`, `front.jpg`, or `${track}.jpg` in the directory. Currently, no external artwork sniffing exists.

2. **Single-Line Lyric Repetition & Lack of Bilingual Alignment**:
   - **Inline Karaoke Timestamp Expansion**: Modern LRC files (such as those from NetEase, QQ Music, or Enhanced LRC tools) often embed syllable/word timestamps:
     `[01:23.45] 哪怕[01:24.00]现实[01:24.50]再残酷`
     `LrcParser` currently extracts all timestamps, removes them to get `"哪怕现实再残酷"`, and iterates through all matches, adding the same full sentence for each timestamp. This creates duplicate lines stacked vertically in the UI.
   - **Duplicate Timestamps**: Badly authored LRC files with duplicate identical tags `[00:10.00][00:10.00]` or tight adjacent duplicates (<300ms) are not deduplicated.
   - **Bilingual Lyrics**: Songs with translation lines at the same timestamp (e.g. English original and Chinese translation) are parsed as separate independent items, causing selection conflicts and awkward layout rather than structured dual-line rendering.

## Solution

Consolidate and deepen the artwork and lyrics pipelines into industrial-grade, resilient implementations:

1. **Dual-Source Artwork Pipeline & Adaptive 512KB Range**:
   - **Adaptive 512KB Range**: Enlarge the default metadata Range request to 512KB (`0..524287`). For files where ID3 header indicates `tagSize > 512KB`, perform a targeted second Range fetch to complete the tag without downloading audio data.
   - **Folder Artwork Sniffing**: When audio files in a directory lack embedded artwork, automatically probe the directory for `${filename}.jpg`, `cover.jpg`, `folder.jpg`, or `front.jpg`. Cache the scaled thumbnail in `CoverArtStorage` and associate it with the track metadata.
   - **Queue Metadata Propagation**: Update `DirectoryBrowserViewModel.onAudioTrackClicked` and `musicPlayerAppSession.playDirectoryTrack` to pass resolved metadata from `metadataMap`. If metadata was already cached in Room, queue tracks are created immediately with artwork and durations.
   - **Self-Healing Cache**: Differentiate between "successfully parsed without artwork" and "failed due to transient network/truncation error". Transient failures are not cached as permanent nulls, allowing automatic retry on subsequent directory visits.

2. **Industrial LRC Normalizer & Bilingual Alignment**:
   - **Leading Chorus Expansion**: Treat consecutive timestamps at the start of a line (e.g., `[01:00.00][02:30.00] Chorus`) as chorus repetitions, cloning the lyric line across each respective timestamp.
   - **Inline Word-Level Stripping**: Once actual lyric text starts, strip any subsequent timestamps (e.g., `<01:21.00>` or `[01:21.00]`) from the text without generating additional duplicate lines.
   - **Time & Text Deduplication**: Eliminate consecutive or adjacent lines with identical text whose timestamps differ by less than 300ms.
   - **Bilingual Lyric Model**: Evolve `LyricLine` into a rich model supporting `mainText: String` and optional `translation: String?`. When consecutive lines share identical timestamps (or <300ms difference), merge them into a single bilingual entry for clean dual-line UI rendering.

---

## User Stories

### Cover Art & Visual Immersion

1. As a listener browsing albums with high-resolution embedded covers (300KB-2MB), I want the cover to render clearly in both the track list and player views without decoding artifacts or truncation.
2. As a listener with a NAS music library using folder artwork (`cover.jpg` or `folder.jpg`), I want my tracks to display the folder's cover image even if individual audio files lack embedded ID3 tags.
3. As a listener tapping a song in the directory browser, I want the mini-player and full-screen player to immediately display the cover art, without remaining a blank musical note placeholder.
4. As a listener experiencing transient network dropouts while entering a folder, I want the app to retry fetching missing metadata on my next visit rather than permanently caching blank metadata.

### Synchronized Lyrics & Bilingual Display

5. As a listener singing along to karaoke or Enhanced LRC files with word-by-word timestamps, I want each lyric sentence to appear exactly once per verse, rather than seeing duplicate lines repeated for every word.
6. As a listener with standard multi-timestamp choruses (`[01:00.00][02:30.00] Chorus`), I want the chorus to appear and highlight correctly at both 1:00 and 2:30.
7. As a listener listening to foreign songs with bilingual LRC files, I want the original line and its translation to be neatly grouped into a single lyric card with distinct typographic hierarchy.
8. As a listener tapping a bilingual lyric line to seek, I want the playback position to jump precisely to that sentence's timestamp.

---

## Implementation Decisions

- **ADR-0009 Alignment**: Follow all decisions recorded in `docs/adr/0009-dual-source-artwork-and-bilingual-lrc-normalization.md`.
- **Zero Disk Audio Retention**: Only scaled thumbnails (`CoverArtStorage`) and parsed metadata (Room) are persisted locally. Raw audio frames and full-resolution images are released immediately after thumbnail generation.
- **Backward Compatibility**: `LyricLine` remains backward-compatible while providing `translation: String?`.
