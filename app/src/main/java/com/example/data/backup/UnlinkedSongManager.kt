package com.example.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.example.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class UnlinkedSongManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _unlinkedCount = MutableStateFlow(0)
    val unlinkedCount: StateFlow<Int> = _unlinkedCount.asStateFlow()

    init {
        _unlinkedCount.value = loadUnlinked().size
    }

    @Synchronized
    fun saveUnlinkedSongs(songs: List<Pair<Long, BackupSongRef>>, replaceExisting: Boolean) {
        val existingMap = if (replaceExisting) {
            mutableMapOf()
        } else {
            loadUnlinked().associateBy({ it.first }, { it.second }).toMutableMap()
        }

        for ((syntheticId, ref) in songs) {
            existingMap[syntheticId] = ref
        }

        persist(existingMap.toList())
    }

    @Synchronized
    fun getUnlinkedSongs(): List<Pair<Long, BackupSongRef>> {
        return loadUnlinked()
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().remove(KEY_UNLINKED_JSON).apply()
        _unlinkedCount.value = 0
    }

    /**
     * Checks all pending unlinked songs against newly discovered local songs.
     * If any match is found, invokes onSongRelinked(syntheticId, realSong.id)
     * and removes it from the unlinked registry.
     */
    @Synchronized
    fun relinkWithDiscoveredSongs(
        discoveredSongs: List<Song>,
        onSongRelinked: (syntheticId: Long, realId: Long) -> Unit
    ): Int {
        val currentUnlinked = loadUnlinked().toMutableList()
        if (currentUnlinked.isEmpty()) return 0

        var relinkedCount = 0
        val remaining = mutableListOf<Pair<Long, BackupSongRef>>()

        for ((syntheticId, ref) in currentUnlinked) {
            val matchedSong = SongKeyMatcher.findBestMatch(ref, discoveredSongs)
            if (matchedSong != null) {
                onSongRelinked(syntheticId, matchedSong.id)
                relinkedCount++
            } else {
                remaining.add(Pair(syntheticId, ref))
            }
        }

        if (relinkedCount > 0) {
            persist(remaining)
        }

        return relinkedCount
    }

    private fun persist(list: List<Pair<Long, BackupSongRef>>) {
        val arr = JSONArray()
        for ((syntheticId, ref) in list) {
            val item = JSONObject()
            item.put("syntheticId", syntheticId)
            item.put("ref", ref.toJson())
            arr.put(item)
        }
        prefs.edit().putString(KEY_UNLINKED_JSON, arr.toString()).apply()
        _unlinkedCount.value = list.size
    }

    private fun loadUnlinked(): List<Pair<Long, BackupSongRef>> {
        val jsonStr = prefs.getString(KEY_UNLINKED_JSON, null) ?: return emptyList()
        val result = mutableListOf<Pair<Long, BackupSongRef>>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val syntheticId = item.getLong("syntheticId")
                val ref = BackupSongRef.fromJson(item.getJSONObject("ref"))
                result.add(Pair(syntheticId, ref))
            }
        } catch (ignored: Exception) {}
        return result
    }

    companion object {
        private const val PREFS_NAME = "aura_music_unlinked_songs_prefs"
        private const val KEY_UNLINKED_JSON = "unlinked_songs_data"

        @Volatile
        private var INSTANCE: UnlinkedSongManager? = null

        fun getInstance(context: Context): UnlinkedSongManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnlinkedSongManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
