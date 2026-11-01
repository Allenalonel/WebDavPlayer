# 03: Strip armeabi-v7a and Enforce 64-Bit ABI Filter

**What to build:**
Restrict the packaged native CPU architecture strictly to 64-bit `arm64-v8a` in `app/build.gradle.kts`. Remove `armeabi-v7a` from `defaultConfig.ndk.abiFilters`, immediately cutting the native `.so` library footprint by 50% (~9 MB compressed / 18.1 MB uncompressed). Ensure native debug symbols are stripped cleanly during release packaging.

**Blocked by:** 02

**Status:** completed

- [x] Update `defaultConfig.ndk.abiFilters` in `app/build.gradle.kts` to include only `listOf("arm64-v8a")`.
- [x] Verify that Gradle external native build configurations (`CMakeLists.txt`) only compile and link targets for `arm64-v8a`.
- [x] Confirm that packaging options retain legacy packaging or compression settings without packaging empty 32-bit directories.
- [x] Validate that debug builds continue to support local 64-bit emulator/device deployments without friction.
