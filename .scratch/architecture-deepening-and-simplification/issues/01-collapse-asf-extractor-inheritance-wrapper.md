# 01: Collapse AsfExtractor Inheritance Wrapper

**What to build:** A single cohesive, deep `AsfExtractor` module that directly implements the Media3 `Extractor` interface, eliminating the redundant empty subclass inheritance layer so that all ASF container parsing, GUID specifications, seek map calculations, and native demuxing hooks reside in one cohesive class.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] The empty fifteen-line `AsfExtractor` subclass is removed and the underlying extractor implementation is consolidated directly as `AsfExtractor`.
- [x] All ASF GUID constants, container sniffing, seek point mapping, and native JNI bindings live in this single deep module without intermediate inheritance hops.
- [x] Media3 player engine extractor factories instantiate the consolidated `AsfExtractor` directly.
- [x] Unit tests in `AsfExtractorTest` run green against the unified extractor interface.
- [x] Full suite unit tests pass via `./gradlew.bat testDebugUnitTest` without regression.
