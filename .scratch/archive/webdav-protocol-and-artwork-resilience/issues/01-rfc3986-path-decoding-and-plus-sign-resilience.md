# 01: RFC 3986 Path Decoding and Symmetric Plus Sign Resilience

**What to build:** End-to-end support for audio tracks and directories containing literal plus signs (`+`), spaces, and Unicode characters in their names. When WebDAV multistatus XML responses contain resource URLs, paths are percent-decoded strictly according to RFC 3986 (preserving literal `+` instead of replacing them with spaces). When requests are made back to the server, spaces are encoded as `%20` and literal `+` as `%2B`, ensuring 100% path round-trip symmetry and completely eliminating false HTTP 404 errors during audio streaming and directory browsing.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] WebDAV XML parsing uses an RFC 3986 compliant path percent-decoder that converts `%XX` hex sequences into UTF-8 characters without converting literal `+` characters into spaces
- [x] Malformed percent sequences in paths are handled safely without throwing runtime exceptions
- [x] URL resolution for remote files and directories encodes spaces as `%20` and literal `+` as `%2B`
- [x] Full unit tests in `WebDavXmlParserTest` verify paths containing `+`, `%20`, Chinese characters, and parentheses are preserved verbatim
- [x] Full unit tests in `WebDavServerUrlResolutionTest` verify resolved file URLs preserve `+` as `%2B` and spaces as `%20`

## Answer
实现了 RFC 3986 规范的 URI 路径百分号解码器 `WebDavXmlParser.decodeRfc3986Path`，保留字面量 `+`，仅将 `%XX` 十六进制转义序列转换为连续 UTF-8 字节并安全转换为字符串；针对残缺或非法 `%` 模式（如 `100%_Love.mp3`、`%2` 等）不抛出运行时异常，保留原始字面文本。在 `WebDavServer.resolveFileUrl` 中增强了对目录路径结尾斜杠的保留，并严格确保空格编码为 `%20`、加号编码为 `%2B`；`OkHttpWebDavClient.buildDirectoryUrl` 统一切换至 `WebDavServer.resolveFileUrl` 消除路径编码不一致。在 `WebDavXmlParserTest` 和 `WebDavServerUrlResolutionTest` 中补充了针对 `+`、`%20`、中文与括号的全面单测并全部通过。
