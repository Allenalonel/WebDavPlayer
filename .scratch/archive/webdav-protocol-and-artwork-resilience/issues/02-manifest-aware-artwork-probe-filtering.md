# 02: Manifest-Aware In-Memory Artwork Candidate Pre-Filtering

**What to build:** End-to-end elimination of blind HTTP 404 network storms when browsing albums where audio tracks lack embedded cover art. The metadata resolution pipeline receives the remote directory's file manifest (already retrieved during directory listing) and performs an in-memory case-insensitive lookup before dispatching remote requests for candidate artwork (`$basename.jpg`, `${filename}.png`, `cover.jpg`, etc.). Candidate files that do not exist on the remote server are skipped with zero network requests, reducing the remote probe volume from dozens of 404s to at most 1 successful fetch, while preserving fallback probing when directory manifests are absent.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Metadata resolution pipeline accepts an optional collection of known directory files
- [x] Candidate artwork filenames are checked against the in-memory directory file manifest using case-insensitive comparison prior to making HTTP requests
- [x] Non-existent artwork files generate zero network requests and zero HTTP 404 responses
- [x] Directory repository passes retrieved directory file lists into metadata enrichment
- [x] Standalone track metadata queries without a directory manifest retain resilient probing fallback
- [x] Unit tests in `TrackMetadataResolverTest` and `TrackMetadataRepositoryTest` assert that directories of tracks without embedded art issue zero HTTP requests for missing files and at most 1 request for valid external covers

## Answer
在元数据解析接缝（`TrackMetadataRepository`、`TrackMetadataRepositoryImpl`、`DefaultTrackMetadataResolver`）中引入了可选的目录文件清单参数 `directoryFiles: Collection<RemoteFile>?`。在发起任何外置候选封面探测前，先基于已知文件清单构建忽略大小写的内存查找表（`manifestMap`），对候选文件名（`$basename.jpg`、`cover.jpg` 等）进行预过滤，若文件不存在则直接在内存跳过并设置哨兵，杜绝盲目发送 HTTP Range 请求产生 404 网络风暴；命中匹配时保留服务端真实文件名大小写发起精准拉取；当未提供清单（`directoryFiles == null`）时无缝保留既有容错探测回退机制。在 `DirectoryRepositoryImpl.enrichDirectoryContent` 中透传 `directory.files`。在 `TrackMetadataResolverTest`、`TrackMetadataRepositoryTest` 与 `DirectoryRepositoryTest` 中补全端到端单测并全部验证通过。
