# 03: Industrial LRC Normalizer and Bilingual Model

**What to build:** Refactor `LrcParser` and the `LyricLine` / `Lyrics` domain models to implement industry-standard LRC processing: expand leading chorus timestamps across the timeline, strip inline word/syllable timestamps without line duplication, deduplicate adjacent identical lines, and merge same-timestamp translation pairs into a structured `LyricLine` with `mainText` and `translation`.

**Blocked by:** None (can start in parallel with 01)

**Status:** ready-for-agent

- [ ] Extend `LyricLine` domain model with `translation: String? = null` while preserving backward compatibility.
- [ ] Refactor `LrcParser` to distinguish leading timestamps from inline timestamps:
  - Leading consecutive timestamps (`[01:00.00][02:30.00] Chorus`) generate separate `LyricLine` entries at their respective timeline points.
  - Inline timestamps inside the line (`[01:00.00] Word1 [01:00.50] Word2` or `<01:00.50>`) are stripped from the text and do NOT create duplicate lines.
- [ ] Implement deduplication: remove consecutive identical text lines whose timestamps are within a 300ms window.
- [ ] Implement bilingual lyric merging: when consecutive lines have identical or nearly identical (<300ms) timestamps, merge the second line as the `translation` of the first rather than treating it as a disjoint line.
- [ ] Add comprehensive unit tests in `LrcParserTest` covering:
  - Inline word-by-word karaoke timestamps without sentence repetition.
  - Standard multi-timestamp repeated choruses.
  - Bilingual paired lyrics merging into `LyricLine(mainText, translation)`.
  - Duplicate identical timestamp deduplication.
