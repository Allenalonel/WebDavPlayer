# 02: Modern Server Cards and Instant Switching

**What to build:**
A modernized Server Management view with MD3 elevated cards clearly displaying protocol badges, host URLs, and active status indicators. Tapping any server card immediately designates it as the `Active Server` and seamlessly transitions the user back to the root of the [Browser] tab to start listening without extra manual clicks.

**Blocked by:** 01: Scaffold Navigation and Docked Mini-Player

**Status:** ready-for-agent

- [ ] WebDAV server items rendered as MD3 elevated cards with clear protocol (HTTP/HTTPS) badges, active status highlight, and host summaries.
- [ ] Tapping a server card activates that server as the `Active Server` and immediately switches the navigation tab to the [Browser] tab at root directory.
- [ ] Server card edit, test connection, and delete actions are neatly grouped with responsive state feedback (connection testing spinners and status chips).
- [ ] Automated tests verify that tapping a server sets it active and triggers navigation to the browser root.
