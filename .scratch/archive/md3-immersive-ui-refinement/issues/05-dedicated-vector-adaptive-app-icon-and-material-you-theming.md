# 05: Dedicated Vector Adaptive App Icon and Material You Theming

**What to build:** Design and configure a custom vector adaptive launcher icon for WebDavPlayer featuring a minimalist vinyl record and streaming cloud motif. Provide full compatibility with Android 13+ Material You themed dynamic coloring via a dedicated monochrome icon layer. Update the application manifest to replace the default system icon.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Vector adaptive icon drawables (background, foreground, and monochrome) are created under `res/mipmap-anydpi-v26/` and associated density directories.
- [x] The foreground artwork depicts a clean, recognizable combination of vinyl record grooves and cloud streaming waves.
- [x] The monochrome layer supports Android 13+ Material You wallpaper-based theme coloring.
- [x] `AndroidManifest.xml` references the new launcher icon and round launcher icon.
- [x] All icon resources compile cleanly with AAPT2 and render properly across launcher shapes.
