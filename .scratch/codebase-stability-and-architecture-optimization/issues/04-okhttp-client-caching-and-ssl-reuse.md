# 04: Cached OkHttpClient and SSL Session Reuse

**What to build:** Network requests targeting the same WebDAV server reuse existing configured `OkHttpClient` instances and connection pools, eliminating redundant `SSLContext` initializations and reducing TLS handshake overhead.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] Inside `OkHttpWebDavClient`, introduce an internal thread-safe client cache keyed by server connection parameters (id, endpointUrl, username, allowSelfSigned).
- [x] Subsequent calls to `testConnection`, `listDirectory`, `fetchRange`, and `fetchText` for the same server configuration reuse the cached client instance.
- [x] If a server's credentials, address, or certificate trust settings change, or when server cache is invalidated, the cached client is cleared and re-created cleanly.
- [x] Unit tests in `WebDavClientTest` verify client instance reuse across calls and cache invalidation.
- [x] All existing mock and live connection tests continue to pass.
