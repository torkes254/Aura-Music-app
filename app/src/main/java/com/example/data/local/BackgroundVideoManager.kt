package com.example.data.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.PowerManager
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class VideoFileInfo(
    val sizeBytes: Long,
    val displayName: String
) {
    val sizeFormatted: String
        get() {
            val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
            return String.format("%.1f MB", mb)
        }
    val isOver50MB: Boolean
        get() = sizeBytes > 50L * 1024L * 1024L
}

data class BackgroundVideoState(
    val globalVideoPath: String? = null,
    val songVideos: Map<Long, String> = emptyMap(),
    val disableOnBatterySaver: Boolean = true,
    val isBatterySaverActive: Boolean = false,
    val showLargeAlbumArtWithVideo: Boolean = false,
    val videoDimming: Float = 0.20f
) {
    fun activeVideoForSong(songId: Long?): String? {
        if (disableOnBatterySaver && isBatterySaverActive) {
            return null
        }
        val songSpecific = songId?.let { songVideos[it] }
        if (songSpecific != null && File(songSpecific).exists()) {
            return songSpecific
        }
        if (globalVideoPath != null && File(globalVideoPath).exists()) {
            return globalVideoPath
        }
        return null
    }

    fun hasCustomVideo(songId: Long): Boolean {
        val path = songVideos[songId] ?: return false
        return File(path).exists()
    }
}

class BackgroundVideoManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val videosDir = File(context.filesDir, "background_videos").apply { mkdirs() }

    private val _videoState = MutableStateFlow(BackgroundVideoState())
    val videoState: StateFlow<BackgroundVideoState> = _videoState.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                updateBatterySaverStatus()
            }
        }
    }

    companion object {
        private const val TAG = "BackgroundVideoManager"
        private const val PREFS_NAME = "aura_music_video_prefs"
        private const val KEY_GLOBAL_VIDEO = "global_video_path"
        private const val KEY_DISABLE_ON_BATTERY_SAVER = "disable_on_battery_saver"
        private const val KEY_SHOW_LARGE_ALBUM_ART = "show_large_album_art_with_video"
        private const val KEY_VIDEO_DIMMING = "video_dimming"
        private const val KEY_SONG_VIDEO_PREFIX = "song_video_"

        const val MAX_RECOMMENDED_SIZE_BYTES = 50L * 1024L * 1024L // 50 MB

        @Volatile
        private var INSTANCE: BackgroundVideoManager? = null

        fun getInstance(context: Context): BackgroundVideoManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackgroundVideoManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadPersistedState()
        try {
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register battery receiver", e)
        }
    }

    private fun loadPersistedState() {
        val globalPath = prefs.getString(KEY_GLOBAL_VIDEO, null)
            ?.takeIf { File(it).exists() }
        val disableOnBattery = prefs.getBoolean(KEY_DISABLE_ON_BATTERY_SAVER, true)
        val showLargeArt = prefs.getBoolean(KEY_SHOW_LARGE_ALBUM_ART, false)
        val dimming = prefs.getFloat(KEY_VIDEO_DIMMING, 0.20f)
        val isBatterySaver = powerManager?.isPowerSaveMode == true

        val songMap = mutableMapOf<Long, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_SONG_VIDEO_PREFIX) && value is String) {
                val songIdStr = key.removePrefix(KEY_SONG_VIDEO_PREFIX)
                val songId = songIdStr.toLongOrNull()
                if (songId != null && File(value).exists()) {
                    songMap[songId] = value
                }
            }
        }

        _videoState.value = BackgroundVideoState(
            globalVideoPath = globalPath,
            songVideos = songMap,
            disableOnBatterySaver = disableOnBattery,
            isBatterySaverActive = isBatterySaver,
            showLargeAlbumArtWithVideo = showLargeArt,
            videoDimming = dimming
        )
    }

    fun updateBatterySaverStatus() {
        val isBatterySaver = powerManager?.isPowerSaveMode == true
        _videoState.update { it.copy(isBatterySaverActive = isBatterySaver) }
    }

    fun setDisableOnBatterySaver(disable: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_ON_BATTERY_SAVER, disable).apply()
        _videoState.update { it.copy(disableOnBatterySaver = disable) }
    }

    fun setShowLargeAlbumArt(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LARGE_ALBUM_ART, show).apply()
        _videoState.update { it.copy(showLargeAlbumArtWithVideo = show) }
    }

    fun setVideoDimming(dimming: Float) {
        val clamped = dimming.coerceIn(0.0f, 0.70f)
        prefs.edit().putFloat(KEY_VIDEO_DIMMING, clamped).apply()
        _videoState.update { it.copy(videoDimming = clamped) }
    }

    fun inspectVideoUri(uri: Uri): VideoFileInfo {
        var size: Long = -1L
        var name = "video.mp4"

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query uri metadata", e)
        }

        if (size <= 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    size = afd.length
                }
            } catch (ignored: Exception) {}
        }

        if (size < 0L) size = 0L

        return VideoFileInfo(sizeBytes = size, displayName = name)
    }

    suspend fun saveVideoFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            videosDir.mkdirs()
            val tempFile = File(videosDir, "temp_${System.currentTimeMillis()}.tmp")
            val md = java.security.MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        md.update(buffer, 0, read)
                    }
                }
            } ?: return@withContext null

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext null
            }

            val hash = md.digest().joinToString("") { "%02x".format(it) }
            val finalFile = File(videosDir, "video_${hash}.mp4")
            if (finalFile.exists() && finalFile.length() > 0) {
                tempFile.delete()
            } else {
                if (finalFile.exists()) finalFile.delete()
                tempFile.renameTo(finalFile)
            }
            finalFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving video", e)
            null
        }
    }

    private fun cleanOrphanedVideoFile(path: String?) {
        if (path == null) return
        val isStillGloballyUsed = _videoState.value.globalVideoPath == path
        val isStillUsedBySongs = _videoState.value.songVideos.values.contains(path)
        if (!isStillGloballyUsed && !isStillUsedBySongs) {
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun setGlobalVideo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val savedPath = saveVideoFromUri(uri) ?: return@withContext false
        val oldPath = _videoState.value.globalVideoPath
        prefs.edit().putString(KEY_GLOBAL_VIDEO, savedPath).apply()
        _videoState.update { it.copy(globalVideoPath = savedPath) }
        cleanOrphanedVideoFile(oldPath)
        true
    }

    fun setGlobalVideoPathDirect(path: String) {
        val oldPath = _videoState.value.globalVideoPath
        prefs.edit().putString(KEY_GLOBAL_VIDEO, path).apply()
        _videoState.update { it.copy(globalVideoPath = path) }
        cleanOrphanedVideoFile(oldPath)
    }

    fun applyImportedSongVideos(map: Map<Long, String>, replace: Boolean) {
        val editor = prefs.edit()
        val updatedMap = if (replace) {
            // Remove old song video prefs
            for (key in prefs.all.keys) {
                if (key.startsWith(KEY_SONG_VIDEO_PREFIX)) {
                    editor.remove(key)
                }
            }
            map.toMutableMap()
        } else {
            val cur = _videoState.value.songVideos.toMutableMap()
            cur.putAll(map)
            cur
        }

        for ((id, path) in updatedMap) {
            editor.putString("${KEY_SONG_VIDEO_PREFIX}$id", path)
        }
        editor.apply()
        _videoState.update { it.copy(songVideos = updatedMap) }
    }

    fun updateSongVideosWithRelinkedSongs(relinkedMap: Map<Long, Long>) {
        if (relinkedMap.isEmpty()) return
        val current = _videoState.value.songVideos.toMutableMap()
        val editor = prefs.edit()
        var changed = false
        for ((oldId, newId) in relinkedMap) {
            val path = current.remove(oldId)
            if (path != null) {
                current[newId] = path
                editor.remove("${KEY_SONG_VIDEO_PREFIX}$oldId")
                editor.putString("${KEY_SONG_VIDEO_PREFIX}$newId", path)
                changed = true
            }
        }
        if (changed) {
            editor.apply()
            _videoState.update { it.copy(songVideos = current) }
        }
    }

    fun removeGlobalVideo() {
        val oldPath = _videoState.value.globalVideoPath
        prefs.edit().remove(KEY_GLOBAL_VIDEO).apply()
        _videoState.update { it.copy(globalVideoPath = null) }
        cleanOrphanedVideoFile(oldPath)
    }

    suspend fun setSongVideo(songId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val savedPath = saveVideoFromUri(uri) ?: return@withContext false
        val oldPath = _videoState.value.songVideos[songId]
        prefs.edit().putString("${KEY_SONG_VIDEO_PREFIX}$songId", savedPath).apply()
        _videoState.update { state ->
            val newMap = state.songVideos.toMutableMap()
            newMap[songId] = savedPath
            state.copy(songVideos = newMap)
        }
        cleanOrphanedVideoFile(oldPath)
        true
    }

    suspend fun setSongsVideo(songIds: Collection<Long>, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) return@withContext false
        val savedPath = saveVideoFromUri(uri) ?: return@withContext false
        val editor = prefs.edit()
        val oldPaths = mutableListOf<String?>()
        for (id in songIds) {
            oldPaths.add(_videoState.value.songVideos[id])
            editor.putString("${KEY_SONG_VIDEO_PREFIX}$id", savedPath)
        }
        editor.apply()
        _videoState.update { state ->
            val newMap = state.songVideos.toMutableMap()
            for (id in songIds) {
                newMap[id] = savedPath
            }
            state.copy(songVideos = newMap)
        }
        oldPaths.forEach { cleanOrphanedVideoFile(it) }
        true
    }

    fun removeSongVideo(songId: Long) {
        val oldPath = _videoState.value.songVideos[songId]
        prefs.edit().remove("${KEY_SONG_VIDEO_PREFIX}$songId").apply()
        _videoState.update { state ->
            val newMap = state.songVideos.toMutableMap()
            newMap.remove(songId)
            state.copy(songVideos = newMap)
        }
        cleanOrphanedVideoFile(oldPath)
    }

    fun removeSongsVideo(songIds: Collection<Long>) {
        val editor = prefs.edit()
        val oldPaths = mutableListOf<String?>()
        for (id in songIds) {
            oldPaths.add(_videoState.value.songVideos[id])
            editor.remove("${KEY_SONG_VIDEO_PREFIX}$id")
        }
        editor.apply()
        _videoState.update { state ->
            val newMap = state.songVideos.toMutableMap()
            songIds.forEach { newMap.remove(it) }
            state.copy(songVideos = newMap)
        }
        oldPaths.forEach { cleanOrphanedVideoFile(it) }
    }

    fun clearAllSongVideos() {
        val oldPaths = _videoState.value.songVideos.values.toList()
        for (songId in _videoState.value.songVideos.keys) {
            prefs.edit().remove("${KEY_SONG_VIDEO_PREFIX}$songId").apply()
        }
        _videoState.update { it.copy(songVideos = emptyMap()) }
        oldPaths.forEach { cleanOrphanedVideoFile(it) }
    }

    fun release() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (ignored: Exception) {}
    }
}
