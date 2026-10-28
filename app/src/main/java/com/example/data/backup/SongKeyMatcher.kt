package com.example.data.backup

import com.example.model.Song
import java.io.File
import kotlin.math.abs

object SongKeyMatcher {

    fun extractFileName(song: Song): String {
        if (!song.dataPath.isNullOrBlank()) {
            try {
                val f = File(song.dataPath)
                if (f.name.isNotBlank()) return f.name
            } catch (ignored: Exception) {}
        }
        val safeTitle = song.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
        return if (safeTitle.isNotBlank()) "$safeTitle.mp3" else "song_${song.id}.mp3"
    }

    fun extractRelativePath(song: Song): String {
        val fileName = extractFileName(song)
        val folder = song.folder.trim().trim('/')
        return if (folder.isNotBlank()) "$folder/$fileName" else fileName
    }

    fun createSongRef(
        idInBackup: Int,
        song: Song,
        playCount: Int = 0,
        videoPathInZip: String? = null
    ): BackupSongRef {
        return BackupSongRef(
            idInBackup = idInBackup,
            relativePath = extractRelativePath(song),
            fileName = extractFileName(song),
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationMs = song.durationMs,
            playCount = playCount,
            videoPathInZip = videoPathInZip
        )
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun isDurationClose(d1: Long, d2: Long, toleranceMs: Long = 3000L): Boolean {
        if (d1 <= 0L || d2 <= 0L) return true
        return abs(d1 - d2) <= toleranceMs
    }

    /**
     * Finds the best matching local song using the hierarchy:
     * 1. Relative folder path + file name + duration (within 3s)
     * 2. File name + duration (within 3s)
     * 3. Title + Artist + duration (within 3s)
     * 4. Title + Artist (normalized)
     * 5. Title + duration (within 2s)
     */
    fun findBestMatch(ref: BackupSongRef, localSongs: List<Song>): Song? {
        val normRefRelative = normalizeText(ref.relativePath)
        val normRefFile = normalizeText(ref.fileName)
        val normRefTitle = normalizeText(ref.title)
        val normRefArtist = normalizeText(ref.artist)

        // 1. Primary: Relative folder path + file name + duration
        val matchRelativeAndDuration = localSongs.find { song ->
            val songRelative = normalizeText(extractRelativePath(song))
            songRelative == normRefRelative && isDurationClose(song.durationMs, ref.durationMs, 3000L)
        }
        if (matchRelativeAndDuration != null) return matchRelativeAndDuration

        // 2. File name + duration
        val matchFileNameAndDuration = localSongs.find { song ->
            val songFile = normalizeText(extractFileName(song))
            songFile == normRefFile && isDurationClose(song.durationMs, ref.durationMs, 3000L)
        }
        if (matchFileNameAndDuration != null) return matchFileNameAndDuration

        // 3. Title + Artist + duration
        if (normRefTitle.isNotEmpty()) {
            val matchTitleArtistDuration = localSongs.find { song ->
                val songTitle = normalizeText(song.title)
                val songArtist = normalizeText(song.artist)
                songTitle == normRefTitle && songArtist == normRefArtist && isDurationClose(song.durationMs, ref.durationMs, 3000L)
            }
            if (matchTitleArtistDuration != null) return matchTitleArtistDuration
        }

        // 4. Title + Artist fallback
        if (normRefTitle.isNotEmpty() && normRefArtist.isNotEmpty()) {
            val matchTitleArtist = localSongs.find { song ->
                val songTitle = normalizeText(song.title)
                val songArtist = normalizeText(song.artist)
                songTitle == normRefTitle && songArtist == normRefArtist
            }
            if (matchTitleArtist != null) return matchTitleArtist
        }

        // 5. Title + duration fallback
        if (normRefTitle.isNotEmpty()) {
            val matchTitleDuration = localSongs.find { song ->
                val songTitle = normalizeText(song.title)
                songTitle == normRefTitle && isDurationClose(song.durationMs, ref.durationMs, 2000L)
            }
            if (matchTitleDuration != null) return matchTitleDuration
        }

        return null
    }
}
