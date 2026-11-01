# Spec: WebDAV Protocol Hardening, Path Encoding Compliance, and Artwork Request Resilience

Status: ready-for-agent

## Problem Statement

Users streaming music from home NAS devices (such as Synology DSM, QNAP, TrueNAS) and private WebDAV servers encounter three distinct classes of protocol and performance failures:

1. **Path Encoding & Broken Audio Playback for Tracks with Plus Signs (`+`)**:
   Standard form-based URL decoders mistakenly treat the `+` character in URL paths as a space (`' '`), in violation of RFC 3986. When a user has songs or folders named with a literal plus sign (for example, `1+1.mp3`, `Bonus+Track.flac`, or `AC+DC/`), WebDAV multistatus XML parsing incorrectly translates the path to spaces. When the player subsequently attempts to stream or fetch metadata using the corrupted path, the WebDAV server returns HTTP 404 Not Found, causing the track to fail playback or be erroneously marked inactive.

2. **Network Request Storm from Blind External Cover Probing**:
   When browsing music directories where tracks lack embedded ID3/Vorbis cover art, the metadata resolution engine naively sends HTTP Range requests probing for candidate cover files (`$basename.jpg`, `$basename.png`, `${filename}.jpg`, `${filename}.png`) for every track sequentially. For an album with 30 tracks, this triggers up to 120 consecutive HTTP 404 requests to the remote server, even though the directory's file manifest was already retrieved during the initial directory listing. This flood of 404 errors exhausts the HTTP connection pool, wastes mobile data, causes noticeable UI latency, and can trigger anti-flood/anti-CC rate limiting on personal NAS servers, locking the user out.

3. **Inability to Authenticate with Digest-Only Servers & Dual-Stack Delays**:
   Popular private cloud solutions like Synology DSM recommend or enforce HTTP Digest Authentication (RFC 7616 / RFC 2617) to prevent plaintext credential exposure over unencrypted local networks. Currently, the player only supports HTTP Basic Authentication. When connected to a Digest-protected server, the connection test and all directory requests fail with HTTP 401 Unauthorized. Furthermore, on IPv4/IPv6 dual-stack home networks, connection handshakes can stall for 10-15 seconds if IPv6 routing is non-functional without Happy Eyeballs fast fallback enabled.

## Solution

1. **RFC 3986 Compliant URI Path Decoding and Symmetric Encoding**:
   - Replace form-based URL decoding with a dedicated RFC 3986 path percent-decoder that preserves literal `+` characters and decodes `%XX` hex sequences strictly as UTF-8 octets.
   - Symmetrically ensure that URI path construction encodes literal spaces as `%20` and literal `+` characters as `%2B`, ensuring 100% path round-trip fidelity between server XML listings and client requests.

2. **In-Memory Directory Manifest Pre-Filtering for Artwork Resolution**:
   - Thread the already-retrieved directory file listing from directory browsing and enrichment down to the metadata resolution pipeline.
   - Prior to making any network call for candidate artwork files, check in memory against the known directory file manifest using case-insensitive matching. If a candidate file is not present in the directory, skip it with zero network requests.
   - Retain an in-memory negative cache for folder-level probes and preserve standalone track resolution fallback when parent directory manifests are not supplied.

3. **Integrated HTTP Digest Authentication & Fast Fallback**:
   - Implement an RFC 7616 / RFC 2617 compliant Digest Authenticator interceptor inside the remote HTTP client stack.
   - Automatically parse `WWW-Authenticate: Digest` challenge headers (extracting `realm`, `nonce`, `qop`, `opaque`, and `algorithm`), compute standard digest responses with client nonce (`cnonce`) and nonce counter (`nc`), and replay requests with standard `Authorization: Digest` credentials.
   - Implement strict retry guardrails to prevent infinite 401 retry loops upon invalid credentials.
   - Enable OkHttp `fastFallback = true` across both browsing and media streaming connection pools to provide Happy Eyeballs dual-stack address racing.

## User Stories

1. As a listener with tracks containing `+` in their filenames (e.g. `1+1.mp3`), I want the player to correctly resolve and stream the audio, so that I do not encounter false HTTP 404 playback errors.
2. As a listener browsing folders with `+` in their directory names (e.g. `Pop+Rock/`), I want the directory browser to list and navigate subdirectories without path corruption, so that I can access all my music albums.
3. As a listener with tracks featuring `+` characters in their tags or filenames, I want audio metadata and embedded covers to load accurately, so that track details display properly in the player interface.
4. As a mobile listener on metered data, I want the player to avoid sending dozens of pointless HTTP requests for cover files that do not exist on the server, so that my mobile bandwidth is conserved.
5. As a NAS owner with automated security firewalls, I want the player to not trigger 120 consecutive HTTP 404 errors when scanning a folder of music, so that my device's IP address is not auto-banned by the server's intrusion prevention system.
6. As a listener entering a directory with 50 tracks lacking embedded art, I want directory enrichment to complete swiftly without network contention, so that track durations and titles populate without UI freezing.
7. As a listener whose directory contains a valid external `cover.jpg`, I want the player to identify and fetch that cover while skipping non-existent track-specific filenames, so that artwork displays with minimal latency.
8. As a listener whose directory contains track-specific artwork matching the song name (e.g. `Track1.jpg`), I want the player to fetch only that specific file without probing for alternatives, so that custom artwork is honored efficiently.
9. As a Synology DSM user whose WebDAV server requires Digest Authentication, I want to connect and stream music successfully, so that I do not have to downgrade my NAS security to insecure Basic Authentication.
10. As a user configuring a new WebDAV server with Digest Authentication, I want the connection test in server settings to succeed immediately, so that I can confirm server reachability.
11. As a user on a Digest-authenticated server who inputs incorrect credentials, I want the player to fail cleanly with an authentication error after one challenge attempt, so that the client does not get stuck in an infinite 401 retry loop.
12. As a listener on a dual-stack home network where IPv6 internet connectivity is slow or broken, I want OkHttp fast fallback to race IPv4 and IPv6, so that initial streaming start-up does not stall for 10-15 seconds.
13. As a listener playing CUE virtual tracks residing in folders with special characters, I want CUE sheet discovery and association to succeed reliably, so that virtual tracks are playable without errors.
14. As a user searching my library for tracks with special characters in their paths, I want search result clicks to correctly play the track, so that library navigation remains robust across all characters.

## Implementation Decisions

- **RFC 3986 Path Percent-Decoder**:
  - Implement a dedicated URI path percent-decoding algorithm within the remote parsing subsystem.
  - The decoder scans percent-encoded tokens (`%XX`) and converts consecutive valid hex octets into UTF-8 characters.
  - Literal `+` characters must be preserved verbatim as `+` and never converted into spaces (unlike `application/x-www-form-urlencoded` form decoders).
  - Malformed percent sequences must be safely preserved as literal text rather than throwing runtime exceptions.
  - In URL resolution, segments will continue to encode spaces as `%20` and literal `+` as `%2B`, creating exact bijective symmetry.

- **Directory-Aware Metadata Resolution Seam**:
  - Deepen the metadata resolution contract to optionally accept a collection of known directory files (`directoryFiles: Collection<RemoteFile>?`).
  - When directory files are supplied, construct an in-memory normalized filename lookup set (case-insensitive on ASCII letters to support case-varying WebDAV implementations).
  - During track-specific and folder-level candidate artwork probing, verify candidate existence against the in-memory lookup set prior to dispatching any remote range request.
  - If a candidate filename is not in the directory manifest, bypass network probing completely with zero round-trips.
  - If directory files are null (e.g. ad-hoc single-track metadata resolution from a cross-server playlist), gracefully preserve the network-probing fallback.
  - In the directory repository, pass the remote directory's file list directly into the metadata repository's resolution method.

- **RFC 7616 / RFC 2617 Digest Authentication Interceptor**:
  - Create a cohesive `WebDavDigestAuthenticator` adhering to OkHttp's `Authenticator` interface.
  - When an HTTP 401 response contains a `WWW-Authenticate` header starting with `Digest`, parse challenge directives: `realm`, `nonce`, `qop`, `opaque`, and `algorithm` (supporting `MD5` as standard, with fallback for unset/MD5-sess).
  - Generate a secure cryptographically random 16-byte client nonce (`cnonce`) and maintain a monotonic nonce counter (`nc = 00000001`).
  - Calculate `HA1 = MD5(username:realm:password)`, `HA2 = MD5(method:digestUri)`, and final response `MD5(HA1:nonce:nc:cnonce:qop:HA2)` (or `MD5(HA1:nonce:HA2)` for legacy servers without qop).
  - Build the standard `Authorization: Digest ...` header and retry the request.
  - If an outgoing request already carries an `Authorization: Digest` header with the same nonce, abort further retries by returning null, preventing infinite loops on invalid credentials.
  - Integrate this authenticator into both the general WebDAV OkHttpClient and the streaming OkHttpClient.

- **OkHttp Fast Fallback (RFC 8305 Happy Eyeballs)**:
  - Explicitly enable `.fastFallback(true)` on both base OkHttpClient and streaming base OkHttpClient builders.
  - This allows concurrent or rapid sequential racing of IPv4 and IPv6 addresses returned by DNS, mitigating long connection timeouts on dual-stack home networks.

## Testing Decisions

- **Test Against Public Seams and Observable Behavior**:
  - Tests will not assert on private helper names or implementation details; tests will exercise public API contracts with deterministic inputs and assert on observable outputs.
  - **Path Decoding & URL Symmetry**:
    - Exercise the multistatus XML parser with XML containing filenames with `+`, `%20`, Chinese/Unicode characters, and parentheses. Assert that the resulting `RemoteDirectory` contains exact matching file and directory paths.
    - Exercise `WebDavServer.resolveFileUrl` with paths containing `+` and spaces, asserting that the generated URL retains valid encoded form (`%2B` and `%20`).
  - **Artwork Probe Filtering**:
    - Exercise `TrackMetadataResolver` and `TrackMetadataRepository` using a Mock WebDAV client.
    - Supply a directory of 10 tracks without embedded art and specify only `cover.jpg` in the directory file list.
    - Assert that zero HTTP requests are issued for `${trackName}.jpg` or `${trackName}.png`, exactly one request is issued for `cover.jpg`, and thumbnail cache is populated.
  - **Digest Authentication**:
    - Exercise `OkHttpWebDavClient` with `MockWebServer` dispatching 401 Digest challenges.
    - Verify that the client sends a correctly computed `Authorization: Digest` header on the subsequent request and completes connection and listing.
    - Verify that supplying invalid credentials causes the authenticator to give up after exactly one retry without entering an infinite loop.
  - **End-to-End Pipeline & Packaging Gates**:
    - Ensure all existing end-to-end tests pass without regression and verify that APK size and DEX budgets remain compliant under `verifyReleasePackaging`.

## Out of Scope

- Implementing NTLM or Kerberos authentication schemes.
- Uploading or modifying files on the WebDAV server (PUT, MKCOL, DELETE), as the application remains a dedicated read-only media player.
- Disk caching of full audio tracks (per ADR-0002 pure streaming zero-disk-cache constraint).

## Further Notes

- The filename comparison in the artwork manifest pre-filter should use case-insensitive matching (`String.equals(..., ignoreCase = true)`) because Windows-based WebDAV servers (IIS) and some Samba/SMB-backed WebDAV servers treat file extensions and names case-insensitively, while Linux servers are case-sensitive.
- All newly added components will be covered by deterministic unit tests run via `./gradlew testDebugUnitTest`.
