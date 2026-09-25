# 01: Project Scaffold and WebDAV Server Management

**What to build:** The user can launch the app, view a list of configured WebDAV servers, add a new server with custom connection parameters (display name, URL/host, port, path prefix, username, password, and a toggle to allow self-signed SSL certificates), test the server connection with visual feedback, edit an existing server, delete a server, and select an active server for subsequent browsing. All server configurations are persisted in a local Room database.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Android application project builds cleanly targeting Min SDK 29 with Jetpack Compose, Material 3, and Kotlin Coroutines.
- [x] Room database stores `WebDavServerEntity` (id, name, url, port, pathPrefix, username, password, allowSelfSigned, isDefault).
- [x] Server management UI displays existing servers, an "Add Server" button, and connection status indicator.
- [x] "Add/Edit Server" dialog supports entering server address, credentials, and toggling self-signed SSL trust.
- [x] "Test Connection" button executes an authenticated WebDAV PROPFIND request against the endpoint and displays success/error.
- [x] Selecting a server sets it as the `Active Server` for the application session.
- [x] Deleting a server removes it from the database with confirmation.
- [x] Unit/Integration tests verify server CRUD operations and OkHttp connection handling (including self-signed SSL certificate bypass) using MockWebServer.
