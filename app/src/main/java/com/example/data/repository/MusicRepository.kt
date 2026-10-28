package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.cover.CoverArtResolver
import com.example.data.local.PlayCountManager
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Playlist
import com.example.model.SmartPlaylistRule
import com.example.model.SmartRuleType
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class MusicRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "aura_music_playlists_prefs",
        Context.MODE_PRIVATE
    )

    private val coverArtResolver = CoverArtResolver.getInstance(context)
    val coverArtMap: StateFlow<Map<String, String>> = coverArtResolver.coverArtMap

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())

    companion object {
        private const val TAG = "MusicRepository"
        private const val KEY_PLAYLISTS_JSON = "saved_playlists"

        @Volatile
        private var INSTANCE: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadPlaylists()
    }

    fun getCoverArtFlow(): StateFlow<Map<String, String>> = coverArtResolver.coverArtMap

    fun getPlaylists(): StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private fun loadPlaylists() {
        val jsonStr = prefs.getString(KEY_PLAYLISTS_JSON, null)
        if (jsonStr == null) {
            _playlists.value = emptyList()
            return
        }
        val list = mutableListOf<Playlist>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getLong("id")
                val name = obj.getString("name")
                val songIdsArr = obj.optJSONArray("songIds")
                val songIds = mutableListOf<Long>()
                if (songIdsArr != null) {
                    for (j in 0 until songIdsArr.length()) {
                        songIds.add(songIdsArr.getLong(j))
                    }
                }
                val customCoverPath = if (obj.has("customCoverPath") && !obj.isNull("customCoverPath")) {
                    obj.optString("customCoverPath", "").takeIf { it.isNotBlank() }
                } else null
                val isSmart = obj.optBoolean("isSmart", false)
                val matchAllRules = obj.optBoolean("matchAllRules", true)
                val exclusionKeywords = obj.optString("exclusionKeywords", "")

                val rules = mutableListOf<SmartPlaylistRule>()
                val rulesArr = obj.optJSONArray("rules")
                if (rulesArr != null) {
                    for (k in 0 until rulesArr.length()) {
                        val rObj = rulesArr.getJSONObject(k)
                        val typeStr = rObj.getString("type")
                        val ruleType = try { SmartRuleType.valueOf(typeStr) } catch (e: Exception) { null }
                        if (ruleType != null) {
                            rules.add(SmartPlaylistRule(type = ruleType, value = rObj.optString("value", "")))
                        }
                    }
                }

                list.add(
                    Playlist(
                        id = id,
                        name = name,
                        songIds = songIds,
                        customCoverPath = customCoverPath,
                        isSmart = isSmart,
                        matchAllRules = matchAllRules,
                        rules = rules,
                        exclusionKeywords = exclusionKeywords
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading playlists", e)
        }
        _playlists.value = list
    }

    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        val arr = JSONArray()
        for (pl in list) {
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            val sArr = JSONArray()
            pl.songIds.forEach { sArr.put(it) }
            obj.put("songIds", sArr)
            if (pl.customCoverPath != null) {
                obj.put("customCoverPath", pl.customCoverPath)
            } else {
                obj.put("customCoverPath", JSONObject.NULL)
            }
            obj.put("isSmart", pl.isSmart)
            obj.put("matchAllRules", pl.matchAllRules)
            obj.put("exclusionKeywords", pl.exclusionKeywords)

            val rArr = JSONArray()
            for (r in pl.rules) {
                val rObj = JSONObject()
                rObj.put("type", r.type.name)
                rObj.put("value", r.value)
                rArr.put(rObj)
            }
            obj.put("rules", rArr)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS_JSON, arr.toString()).apply()
    }

    fun createPlaylist(name: String): Long {
        val newId = System.currentTimeMillis()
        val newList = _playlists.value + Playlist(id = newId, name = name, songIds = emptyList())
        savePlaylists(newList)
        return newId
    }

    fun createSmartPlaylist(
        name: String,
        matchAllRules: Boolean,
        rules: List<SmartPlaylistRule>,
        exclusionKeywords: String
    ): Long {
        val newId = System.currentTimeMillis()
        val smartPl = Playlist(
            id = newId,
            name = name,
            songIds = emptyList(),
            isSmart = true,
            matchAllRules = matchAllRules,
            rules = rules,
            exclusionKeywords = exclusionKeywords
        )
        val newList = _playlists.value + smartPl
        savePlaylists(newList)
        return newId
    }

    fun convertSmartToRegularPlaylist(playlistId: Long, currentSongIds: List<Long>) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(
                    isSmart = false,
                    songIds = currentSongIds,
                    rules = emptyList(),
                    exclusionKeywords = ""
                )
            } else pl
        }
        savePlaylists(updated)
    }

    fun setPlaylistCustomCover(playlistId: Long, coverPath: String) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(customCoverPath = coverPath)
            } else pl
        }
        savePlaylists(updated)
    }

    fun removePlaylistCustomCover(playlistId: Long) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(customCoverPath = null)
            } else pl
        }
        savePlaylists(updated)
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                if (songId !in pl.songIds) {
                    pl.copy(songIds = pl.songIds + songId)
                } else pl
            } else pl
        }
        savePlaylists(updated)
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(songIds = pl.songIds - songId)
            } else pl
        }
        savePlaylists(updated)
    }

    fun deletePlaylist(playlistId: Long) {
        savePlaylists(_playlists.value.filter { it.id != playlistId })
    }

    fun replaceAllPlaylists(list: List<Playlist>) {
        savePlaylists(list)
    }

    fun updatePlaylistsWithRelinkedSongs(relinkedMap: Map<Long, Long>) {
        if (relinkedMap.isEmpty()) return
        val updated = _playlists.value.map { pl ->
            val mappedSongIds = pl.songIds.map { id -> relinkedMap[id] ?: id }
            pl.copy(songIds = mappedSongIds)
        }
        savePlaylists(updated)
    }

    fun removeSongsFromPlaylists(songIds: Set<Long>) {
        val updated = _playlists.value.map { pl ->
            val remaining = pl.songIds.filter { it !in songIds }
            pl.copy(songIds = remaining)
        }
        savePlaylists(updated)
    }

    suspend fun deleteSongsFromStorage(songs: List<Song>): Pair<Set<Long>, List<Uri>> = withContext(Dispatchers.IO) {
        val deletedIds = mutableSetOf<Long>()
        val mediaStoreUris = mutableListOf<Uri>()

        for (song in songs) {
            if (song.contentUri.scheme == "file") {
                try {
                    val f = File(song.contentUri.path ?: "")
                    if (f.exists()) f.delete()
                    deletedIds.add(song.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed deleting file: ${song.contentUri}", e)
                }
            } else {
                mediaStoreUris.add(song.contentUri)
                try {
                    val count = context.contentResolver.delete(song.contentUri, null, null)
                    if (count > 0) {
                        deletedIds.add(song.id)
                    }
                } catch (e: Exception) {
                    // Might throw RecoverableSecurityException or require createDeleteRequest on Android 10+
                }
            }
        }
        Pair(deletedIds, mediaStoreUris)
    }

    fun extractFolder(relativePath: String?, dataPath: String?): String {
        if (!relativePath.isNullOrBlank()) {
            val trimmed = relativePath.trim()
            val formatted = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            return formatted.trimStart('/')
        }
        if (!dataPath.isNullOrBlank()) {
            try {
                val file = File(dataPath)
                val parent = file.parentFile
                if (parent != null) {
                    val segments = parent.absolutePath.split(File.separator)
                    val musicIdx = segments.indexOfLast { it.equals("Music", ignoreCase = true) || it.equals("Download", ignoreCase = true) }
                    if (musicIdx >= 0) {
                        val sub = segments.subList(musicIdx, segments.size).joinToString("/")
                        return if (sub.endsWith("/")) sub else "$sub/"
                    }
                    return "${parent.name}/"
                }
            } catch (ignored: Exception) {}
        }
        return "Music/"
    }

    suspend fun queryAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val playCounts = PlayCountManager.getInstance(context).getPlayCounts()
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.Audio.Media.GENRE)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try { it.getColumnIndex(MediaStore.Audio.Media.GENRE) } catch (e: Exception) { -1 }
                } else -1

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown Title"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val albumId = it.getLong(albumIdCol)
                    val durationMs = it.getLong(durationCol)
                    val track = it.getInt(trackCol)
                    val year = it.getInt(yearCol)
                    val dataPath = it.getString(dataCol)
                    val dateAdded = if (dateAddedCol >= 0) it.getLong(dateAddedCol) else (System.currentTimeMillis() / 1000L)
                    val relPath = if (relPathCol >= 0) it.getString(relPathCol) else null
                    val genre = if (genreCol >= 0) {
                        try { it.getString(genreCol) } catch (e: Exception) { null }
                    } else null

                    val folder = extractFolder(relPath, dataPath)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    if (durationMs > 1000) {
                        songs.add(
                            Song(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                albumId = albumId,
                                durationMs = durationMs,
                                contentUri = contentUri,
                                trackNumber = track,
                                year = year,
                                folder = folder,
                                dateAdded = if (dateAdded > 0) dateAdded else (System.currentTimeMillis() / 1000L),
                                dataPath = dataPath,
                                genre = genre,
                                playCount = playCounts[id] ?: 0
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore", e)
        }

        // Also check if app-generated sample tracks exist in filesDir/sample_audio
        val sampleDir = File(context.filesDir, "sample_audio")
        if (sampleDir.exists()) {
            val sampleOffsets = mapOf(
                "Neon_Sunrise" to -3600L, // 1 hour ago (Today)
                "Midnight_Drive" to -93600L, // 26 hours ago (Yesterday)
                "Retro_Pulse" to -3L * 86400L, // 3 days ago (This week)
                "Starlight_Voyage" to -14L * 86400L, // 14 days ago (This month)
                "Cyber_Horizon" to -45L * 86400L // 45 days ago (Earlier)
            )
            val sampleGenres = mapOf(
                "Neon_Sunrise" to "Synthwave",
                "Midnight_Drive" to "Nightcore",
                "Retro_Pulse" to "Chiptune",
                "Starlight_Voyage" to "Electronic",
                "Cyber_Horizon" to "Lo-Fi"
            )
            val nowSec = System.currentTimeMillis() / 1000L
            val sampleFiles = sampleDir.listFiles() ?: emptyArray()
            for ((idx, file) in sampleFiles.withIndex()) {
                if (file.extension == "wav") {
                    val fakeId = 990000L + idx
                    val baseName = file.nameWithoutExtension
                    val offset = sampleOffsets[baseName] ?: (-idx * 86400L)
                    val song = Song(
                        id = fakeId,
                        title = baseName.replace("_", " "),
                        artist = "Aura Synth",
                        album = "Chiptune Dreams",
                        albumId = 9991L,
                        durationMs = 15000L,
                        contentUri = Uri.fromFile(file),
                        folder = "Music/AuraSynth/",
                        dateAdded = nowSec + offset,
                        genre = sampleGenres[baseName] ?: "Chiptune",
                        playCount = playCounts[fakeId] ?: 0
                    )
                    if (songs.none { it.title == song.title }) {
                        songs.add(song)
                    }
                }
            }
        }

        songs
    }

    suspend fun resolveCover(song: Song): String? {
        return coverArtResolver.resolveCoverForSong(song)
    }

    suspend fun searchOnlineCover(album: String, artist: String, title: String?): String? {
        return coverArtResolver.searchOnlineArtwork(album, artist, title)
    }

    fun saveCustomCover(album: String, artist: String, uri: Uri): String? {
        return coverArtResolver.saveCustomArtwork(album, artist, uri)
    }

    suspend fun generateSampleAudioFiles(): List<Song> = withContext(Dispatchers.IO) {
        val sampleDir = File(context.filesDir, "sample_audio").apply { mkdirs() }
        val nowSec = System.currentTimeMillis() / 1000L
        val sampleTracks = listOf(
            SampleTrackDef("Neon_Sunrise", 440.0, 554.37, nowSec - 3600L), // 1 hour ago (Today)
            SampleTrackDef("Midnight_Drive", 523.25, 659.25, nowSec - 93600L), // Yesterday
            SampleTrackDef("Retro_Pulse", 329.63, 392.00, nowSec - 3L * 86400L), // 3 days ago (This week)
            SampleTrackDef("Starlight_Voyage", 349.23, 440.00, nowSec - 14L * 86400L), // 14 days ago (This month)
            SampleTrackDef("Cyber_Horizon", 261.63, 329.63, nowSec - 45L * 86400L) // 45 days ago (Earlier)
        )

        val songs = mutableListOf<Song>()
        for ((idx, track) in sampleTracks.withIndex()) {
            val file = File(sampleDir, "${track.name}.wav")
            if (!file.exists()) {
                writeWavTone(file, track.freq1, track.freq2, 10.0)
            }
                    val songId = 990000L + idx
                    songs.add(
                        Song(
                            id = songId,
                            title = track.name.replace("_", " "),
                            artist = "Aura Synth",
                            album = "Chiptune Dreams",
                            albumId = 9991L,
                            durationMs = 10000L,
                            contentUri = Uri.fromFile(file),
                            folder = "Music/AuraSynth/",
                            dateAdded = track.dateAdded,
                            dataPath = file.absolutePath,
                            genre = if (track.name.contains("Drive")) "Nightcore" else "Synthwave",
                            playCount = PlayCountManager.getInstance(context).getPlayCount(songId)
                        )
                    )
        }
        songs
    }

    private data class SampleTrackDef(
        val name: String,
        val freq1: Double,
        val freq2: Double,
        val dateAdded: Long
    )

    private fun writeWavTone(file: File, freq1: Double, freq2: Double, durationSec: Double) {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationSec).toInt()
        val sampleBuffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val sampleVal = (0.5 * sin(2.0 * PI * freq1 * t) + 0.5 * sin(2.0 * PI * freq2 * t))
            sampleBuffer[i] = (sampleVal * 32767.0 * 0.7).toInt().toShort()
        }

        val totalDataLen = numSamples * 2
        val totalAudioLen = totalDataLen + 36

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalAudioLen)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // subchunk 1 size
            header.putShort(1.toShort()) // audio format PCM = 1
            header.putShort(1.toShort()) // num channels = 1
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // byte rate
            header.putShort(2.toShort()) // block align
            header.putShort(16.toShort()) // bits per sample
            header.put("data".toByteArray())
            header.putInt(totalDataLen)

            out.write(header.array())

            val byteBuf = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in sampleBuffer) {
                byteBuf.putShort(s)
            }
            out.write(byteBuf.array())
        }
    }
}
