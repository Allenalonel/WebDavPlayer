package com.webdav.player.data.cue

import com.webdav.player.domain.model.RemoteFile

/**
 * Domain helper for matching `.cue` sheets to their corresponding parent audio files in the same directory.
 */
object CueAssociationHelper {
    /**
     * Finds the corresponding audio file for a given CUE file among the candidate audio files in the same directory.
     *
     * Matching precedence:
     * 1. Exact match on referenced file name from CUE (e.g. from FILE directive)
     * 2. Base name match without extension from referenced file name (handles transcoded WAV -> FLAC/APE)
     * 3. Base name match without extension between cue file and audio file (e.g. Album.cue matches Album.flac)
     * 4. Single-pair fallback: If exactly 1 CUE file and 1 audio file exist in the directory, match them.
     * 5. Prefix match: if cue file name starts with audio file name base or vice versa
     */
    fun findMatchingAudioFile(
        cueFile: RemoteFile,
        audioFiles: List<RemoteFile>,
        referencedFileNames: List<String> = emptyList(),
        totalCueFilesCount: Int = 1,
    ): RemoteFile? {
        if (audioFiles.isEmpty()) return null

        // 1. Check referenced file names from CUE FILE directives
        for (ref in referencedFileNames) {
            val cleanRef = ref.trim().replace('\\', '/').substringAfterLast('/')
            // Exact match
            val exact = audioFiles.firstOrNull { it.name.equals(cleanRef, ignoreCase = true) }
            if (exact != null) return exact

            // Base name match with different extension (e.g. CUE says CDImage.wav, but file is CDImage.flac)
            val refBase = cleanRef.substringBeforeLast('.')
            val baseMatch =
                audioFiles.firstOrNull {
                    it.name.substringBeforeLast('.').equals(refBase, ignoreCase = true)
                }
            if (baseMatch != null) return baseMatch
        }

        // 2. Base name match between CUE file and audio file
        val cueBase = cueFile.name.substringBeforeLast('.')
        val sameBaseAudio =
            audioFiles.firstOrNull {
                it.name.substringBeforeLast('.').equals(cueBase, ignoreCase = true)
            }
        if (sameBaseAudio != null) return sameBaseAudio

        // 3. Fallback: If only 1 CUE file and 1 audio file exist in the folder, associate them
        if (totalCueFilesCount == 1 && audioFiles.size == 1) {
            return audioFiles.first()
        }

        // 4. Prefix match: if cue file name starts with audio file name base or vice versa
        val prefixMatch =
            audioFiles.firstOrNull {
                val audioBase = it.name.substringBeforeLast('.')
                cueBase.startsWith(audioBase, ignoreCase = true) || audioBase.startsWith(cueBase, ignoreCase = true)
            }
        if (prefixMatch != null) return prefixMatch

        return null
    }
}
