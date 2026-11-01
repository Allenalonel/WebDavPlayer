# 03: Integrated HTTP Digest Authentication and Fast Fallback

**What to build:** Native support for connecting and streaming from WebDAV servers enforcing HTTP Digest Authentication (RFC 7616 / RFC 2617), such as default Synology DSM WebDAV configurations, combined with Happy Eyeballs fast fallback for dual-stack IPv4/IPv6 home networks. When a server returns an HTTP 401 challenge with a `WWW-Authenticate: Digest` header, the HTTP client automatically computes the MD5 digest response with `cnonce` and `nc` counters, replaying the request with valid `Authorization: Digest` credentials. If credentials are invalid, the client terminates gracefully after 1 retry, avoiding infinite loops.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] OkHttp client stack implements RFC 7616 / RFC 2617 Digest challenge parsing (`realm`, `nonce`, `qop`, `opaque`, `algorithm`) and response calculation
- [ ] Digest authentication supports both `qop="auth"` and legacy qop-less Digest schemes with MD5 hashing
- [ ] Monotonic nonce counters (`nc`) and cryptographically secure client nonces (`cnonce`) are generated for each challenge response
- [ ] Client terminates immediately after one failed digest authorization attempt, preventing infinite 401 retry loops
- [ ] `fastFallback = true` is enabled on both standard and streaming OkHttp clients to race IPv4 and IPv6 connections on dual-stack networks
- [ ] Unit tests in `WebDavClientTest` with `MockWebServer` verify successful Digest authentication handshake, streaming client compatibility, and loop termination on bad credentials
