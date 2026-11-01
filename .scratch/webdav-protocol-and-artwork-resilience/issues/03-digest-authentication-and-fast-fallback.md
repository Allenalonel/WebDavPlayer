# 03: Integrated HTTP Digest Authentication and Fast Fallback

**What to build:** Native support for connecting and streaming from WebDAV servers enforcing HTTP Digest Authentication (RFC 7616 / RFC 2617), such as default Synology DSM WebDAV configurations, combined with Happy Eyeballs fast fallback for dual-stack IPv4/IPv6 home networks. When a server returns an HTTP 401 challenge with a `WWW-Authenticate: Digest` header, the HTTP client automatically computes the MD5 digest response with `cnonce` and `nc` counters, replaying the request with valid `Authorization: Digest` credentials. If credentials are invalid, the client terminates gracefully after 1 retry, avoiding infinite loops.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] OkHttp client stack implements RFC 7616 / RFC 2617 Digest challenge parsing (`realm`, `nonce`, `qop`, `opaque`, `algorithm`) and response calculation
- [x] Digest authentication supports both `qop="auth"` and legacy qop-less Digest schemes with MD5 hashing
- [x] Monotonic nonce counters (`nc`) and cryptographically secure client nonces (`cnonce`) are generated for each challenge response
- [x] Client terminates immediately after one failed digest authorization attempt, preventing infinite 401 retry loops
- [x] `fastFallback = true` is enabled on both standard and streaming OkHttp clients to race IPv4 and IPv6 connections on dual-stack networks
- [x] Unit tests in `WebDavClientTest` with `MockWebServer` verify successful Digest authentication handshake, streaming client compatibility, and loop termination on bad credentials

## Answer
实现了完整的 RFC 7616 / RFC 2617 HTTP 摘要认证器 `WebDavDigestAuthenticator`，支持解析 `realm`、`nonce`、`qop`、`opaque` 与 `algorithm` 指令，并能自适应计算支持 `qop="auth"` 的带有单调 `nc` 与安全随机 `cnonce` 的 MD5 摘要凭据，及向下兼容无 qop 的传统 Digest。当 outgoing 请求中携带的 Digest Nonce 与 401 质询一致时立即返回 null 中断，彻底杜绝凭据错误导致的 401 无限重试死循环。通过 `FastFallbackDns` 在常规与流媒体两套连接池上实现 RFC 8305 Happy Eyeballs 双栈地址竞速与快速回退（`fastFallback = true`）。在 `WebDavDigestAuthenticatorTest` 与 `WebDavClientTest` 中结合官方 RFC 2617 测试向量与 `MockWebServer` 进行了全场景单测与握手回归，全部通过。
