# 01: RFC 3986 Path Decoding and Symmetric Plus Sign Resilience

**What to build:** End-to-end support for audio tracks and directories containing literal plus signs (`+`), spaces, and Unicode characters in their names. When WebDAV multistatus XML responses contain resource URLs, paths are percent-decoded strictly according to RFC 3986 (preserving literal `+` instead of replacing them with spaces). When requests are made back to the server, spaces are encoded as `%20` and literal `+` as `%2B`, ensuring 100% path round-trip symmetry and completely eliminating false HTTP 404 errors during audio streaming and directory browsing.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] WebDAV XML parsing uses an RFC 3986 compliant path percent-decoder that converts `%XX` hex sequences into UTF-8 characters without converting literal `+` characters into spaces
- [ ] Malformed percent sequences in paths are handled safely without throwing runtime exceptions
- [ ] URL resolution for remote files and directories encodes spaces as `%20` and literal `+` as `%2B`
- [ ] Full unit tests in `WebDavXmlParserTest` verify paths containing `+`, `%20`, Chinese characters, and parentheses are preserved verbatim
- [ ] Full unit tests in `WebDavServerUrlResolutionTest` verify resolved file URLs preserve `+` as `%2B` and spaces as `%20`
