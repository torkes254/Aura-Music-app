package com.example.playback

import android.content.Context
import android.content.SharedPreferences
import com.example.data.cover.CoverArtResolver
import com.example.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Collections

class ShuffleQueueManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _queueState = MutableStateFlow(PlayerState())
    val queueState: StateFlow<PlayerState> = _queueState.asStateFlow()

    private var songMap: MutableMap<Long, Song> = mutableMapOf()
    private var contextSongIds: MutableList<Long> = mutableListOf()
    private var remainingBag: MutableList<Long> = mutableListOf()
    private var history: MutableList<Long> = mutableListOf()
    private var historyIndex: Int = -1
    private var persistedSongId: Long = -1L
    private var persistedPositionMs: Long = 0L

    companion object {
        private const val PREFS_NAME = "aura_music_queue_prefs"
        private const val KEY_CURRENT_SONG_ID = "current_song_id"
        private const val KEY_POSITION_MS = "position_ms"
        private const val KEY_IS_SHUFFLE = "is_shuffle_enabled"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_CONTEXT_IDS = "context_song_ids"
        private const val KEY_BAG_IDS = "remaining_bag_ids"
        private const val KEY_HISTORY_IDS = "history_song_ids"
        private const val KEY_HISTORY_INDEX = "history_index"

        @Volatile
        private var INSTANCE: ShuffleQueueManager? = null

        fun getInstance(context: Context): ShuffleQueueManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShuffleQueueManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        val isShuffle = prefs.getBoolean(KEY_IS_SHUFFLE, false)
        val repeatMode = prefs.getInt(KEY_REPEAT_MODE, 0)
        historyIndex = prefs.getInt(KEY_HISTORY_INDEX, -1)
        persistedSongId = prefs.getLong(KEY_CURRENT_SONG_ID, -1L)
        persistedPositionMs = prefs.getLong(KEY_POSITION_MS, 0L)

        val contextStr = prefs.getString(KEY_CONTEXT_IDS, "") ?: ""
        if (contextStr.isNotBlank()) {
            contextSongIds = contextStr.split(",").mapNotNull { it.trim().toLongOrNull() }.toMutableList()
        }

        val bagStr = prefs.getString(KEY_BAG_IDS, "") ?: ""
        if (bagStr.isNotBlank()) {
            remainingBag = bagStr.split(",").mapNotNull { it.trim().toLongOrNull() }.toMutableList()
        }

        val histStr = prefs.getString(KEY_HISTORY_IDS, "") ?: ""
        if (histStr.isNotBlank()) {
            history = histStr.split(",").mapNotNull { it.trim().toLongOrNull() }.toMutableList()
        }

        _queueState.update {
            it.copy(
                isShuffleEnabled = isShuffle,
                repeatMode = repeatMode,
                currentPositionMs = persistedPositionMs
            )
        }
    }

    private fun persistState() {
        val currentId = _queueState.value.currentSong?.id ?: persistedSongId
        val pos = _queueState.value.currentPositionMs
        prefs.edit()
            .putLong(KEY_CURRENT_SONG_ID, currentId)
            .putLong(KEY_POSITION_MS, pos)
            .putBoolean(KEY_IS_SHUFFLE, _queueState.value.isShuffleEnabled)
            .putInt(KEY_REPEAT_MODE, _queueState.value.repeatMode)
            .putString(KEY_CONTEXT_IDS, contextSongIds.joinToString(","))
            .putString(KEY_BAG_IDS, remainingBag.joinToString(","))
            .putString(KEY_HISTORY_IDS, history.joinToString(","))
            .putInt(KEY_HISTORY_INDEX, historyIndex)
            .apply()
    }

    @Synchronized
    fun updateLibrary(
        allSongs: List<Song>,
        isCurrentTrackPlaying: Boolean = false,
        allKnownSongs: List<Song> = emptyList()
    ) {
        val allowedMap = allSongs.associateBy { it.id }.toMutableMap()
        val allKnownMap = allKnownSongs.associateBy { it.id }

        // If currently playing song is from an unchecked folder, allow it to finish
        val currentSong = _queueState.value.currentSong
        if (isCurrentTrackPlaying && currentSong != null && !allowedMap.containsKey(currentSong.id)) {
            val fallback = allKnownMap[currentSong.id] ?: currentSong
            allowedMap[currentSong.id] = fallback
        }

        songMap = allowedMap
        val allowedIds = allSongs.map { it.id }.toSet()

        contextSongIds = contextSongIds.filter { it in allowedIds }.toMutableList()
        remainingBag = remainingBag.filter { it in allowedIds }.toMutableList()

        if (contextSongIds.isEmpty() && allSongs.isNotEmpty()) {
            contextSongIds = allSongs.map { it.id }.toMutableList()
        } else {
            val newIds = allSongs.map { it.id }.filter { it !in contextSongIds }
            if (newIds.isNotEmpty()) {
                contextSongIds.addAll(newIds)
                if (_queueState.value.isShuffleEnabled) {
                    remainingBag.addAll(newIds)
                }
            }
        }

        if (_queueState.value.currentSong == null && persistedSongId != -1L) {
            val restored = allowedMap[persistedSongId] ?: allKnownMap[persistedSongId]
            if (restored != null) {
                val art = CoverArtResolver.getInstance(context).getArtwork(restored)
                _queueState.update {
                    it.copy(
                        currentSong = restored,
                        currentPositionMs = persistedPositionMs,
                        durationMs = restored.durationMs,
                        artworkPath = art
                    )
                }
            }
        }

        updateQueueDisplay()
        persistState()
    }

    @Synchronized
    fun updateArtwork(path: String?) {
        _queueState.update { it.copy(artworkPath = path) }
    }

    @Synchronized
    fun removeSongs(songIds: Set<Long>) {
        if (songIds.isEmpty()) return

        val currentSong = _queueState.value.currentSong
        val isCurrentDeleted = currentSong != null && currentSong.id in songIds

        songIds.forEach { songMap.remove(it) }
        contextSongIds.removeAll(songIds)
        remainingBag.removeAll(songIds)
        history.removeAll(songIds)

        if (historyIndex >= history.size) {
            historyIndex = history.size - 1
        }

        if (isCurrentDeleted) {
            val nextSong = if (contextSongIds.isNotEmpty()) nextTrack() else null
            if (nextSong == null) {
                _queueState.update {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        currentPositionMs = 0L,
                        durationMs = 0L,
                        artworkPath = null
                    )
                }
            }
        }

        updateQueueDisplay()
        persistState()
    }

    @Synchronized
    fun playSong(song: Song, contextSongs: List<Song> = emptyList()): Song {
        songMap[song.id] = song
        if (contextSongs.isNotEmpty()) {
            contextSongs.forEach { songMap[it.id] = it }
            contextSongIds = contextSongs.map { it.id }.toMutableList()
        } else if (!contextSongIds.contains(song.id)) {
            contextSongIds.add(song.id)
        }

        if (_queueState.value.isShuffleEnabled) {
            remainingBag = contextSongIds.filter { it != song.id }.toMutableList()
            Collections.shuffle(remainingBag)
        }

        if (historyIndex >= 0 && historyIndex < history.size - 1) {
            history = history.subList(0, historyIndex + 1).toMutableList()
        }
        history.add(song.id)
        historyIndex = history.size - 1

        val art = CoverArtResolver.getInstance(context).getArtwork(song)
        _queueState.update {
            it.copy(
                currentSong = song,
                currentPositionMs = 0L,
                durationMs = song.durationMs,
                artworkPath = art
            )
        }

        updateQueueDisplay()
        persistState()
        return song
    }

    @Synchronized
    fun startShuffleAll(songs: List<Song>): Song? {
        if (songs.isEmpty()) return null
        songs.forEach { songMap[it.id] = it }
        contextSongIds = songs.map { it.id }.toMutableList()

        val shuffled = contextSongIds.toMutableList()
        Collections.shuffle(shuffled)
        val firstSongId = shuffled.removeAt(0)
        remainingBag = shuffled

        _queueState.update { it.copy(isShuffleEnabled = true) }

        val firstSong = songMap[firstSongId] ?: songs.first()
        history.clear()
        history.add(firstSongId)
        historyIndex = 0

        val art = CoverArtResolver.getInstance(context).getArtwork(firstSong)
        _queueState.update {
            it.copy(
                currentSong = firstSong,
                currentPositionMs = 0L,
                durationMs = firstSong.durationMs,
                artworkPath = art
            )
        }

        updateQueueDisplay()
        persistState()
        return firstSong
    }

    @Synchronized
    fun getFullOrderedQueue(): List<Song> {
        val currentSong = _queueState.value.currentSong
        return if (_queueState.value.isShuffleEnabled) {
            val result = mutableListOf<Song>()
            val seenIds = mutableSetOf<Long>()
            val currentId = currentSong?.id

            for (i in 0 until historyIndex) {
                if (i in history.indices) {
                    val hId = history[i]
                    if (seenIds.add(hId)) {
                        songMap[hId]?.let { result.add(it) }
                    }
                }
            }
            if (currentId != null && seenIds.add(currentId)) {
                songMap[currentId]?.let { result.add(it) }
            }
            for (id in remainingBag) {
                if (seenIds.add(id)) {
                    songMap[id]?.let { result.add(it) }
                }
            }
            for (id in contextSongIds) {
                if (seenIds.add(id)) {
                    songMap[id]?.let { result.add(it) }
                }
            }
            if (result.isNotEmpty()) result else contextSongIds.mapNotNull { songMap[it] }
        } else {
            val list = contextSongIds.mapNotNull { songMap[it] }
            if (list.isNotEmpty()) list else currentSong?.let { listOf(it) } ?: emptyList()
        }
    }

    @Synchronized
    fun getCurrentQueueIndex(): Int {
        val currentSong = _queueState.value.currentSong ?: return 0
        val queue = getFullOrderedQueue()
        val idx = queue.indexOfFirst { it.id == currentSong.id }
        return if (idx >= 0) idx else 0
    }

    @Synchronized
    fun nextTrack(isManualSkip: Boolean = false): Song? {
        val state = _queueState.value
        val repeatMode = state.repeatMode

        // Repeat One only applies when track ends naturally, never on manual skip commands
        if (!isManualSkip && repeatMode == 1 && state.currentSong != null) {
            return state.currentSong
        }

        if (historyIndex < history.size - 1) {
            historyIndex++
            val nextId = history[historyIndex]
            val song = songMap[nextId]
            if (song != null) {
                val art = CoverArtResolver.getInstance(context).getArtwork(song)
                _queueState.update { it.copy(currentSong = song, currentPositionMs = 0L, durationMs = song.durationMs, artworkPath = art) }
                updateQueueDisplay()
                persistState()
                return song
            }
        }

        val nextId: Long? = if (state.isShuffleEnabled) {
            if (remainingBag.isEmpty()) {
                val available = contextSongIds.filter { it != state.currentSong?.id }.toMutableList()
                if (available.isNotEmpty()) {
                    Collections.shuffle(available)
                    remainingBag = available
                    remainingBag.removeAt(0)
                } else null
            } else {
                remainingBag.removeAt(0)
            }
        } else {
            val currId = state.currentSong?.id
            val currIdx = if (currId != null) contextSongIds.indexOf(currId) else -1
            if (currIdx == -1 && contextSongIds.isNotEmpty()) {
                contextSongIds[0]
            } else if (currIdx in contextSongIds.indices && currIdx < contextSongIds.size - 1) {
                contextSongIds[currIdx + 1]
            } else if ((repeatMode == 2 || isManualSkip) && contextSongIds.isNotEmpty()) {
                contextSongIds[0]
            } else null
        }

        if (nextId != null) {
            val song = songMap[nextId]
            if (song != null) {
                history.add(nextId)
                historyIndex = history.size - 1
                val art = CoverArtResolver.getInstance(context).getArtwork(song)
                _queueState.update { it.copy(currentSong = song, currentPositionMs = 0L, durationMs = song.durationMs, artworkPath = art) }
                updateQueueDisplay()
                persistState()
                return song
            }
        }

        return null
    }

    @Synchronized
    fun previousTrack(currentPositionMs: Long = _queueState.value.currentPositionMs): Song? {
        val state = _queueState.value
        val currentSong = state.currentSong ?: return null

        // If more than 3 seconds into the song, restart it
        if (currentPositionMs > 3000L) {
            _queueState.update { it.copy(currentPositionMs = 0L) }
            persistState()
            return currentSong
        }

        // Otherwise go to the previous song in history
        if (historyIndex > 0) {
            historyIndex--
            val prevId = history[historyIndex]
            val song = songMap[prevId]
            if (song != null) {
                val art = CoverArtResolver.getInstance(context).getArtwork(song)
                _queueState.update { it.copy(currentSong = song, currentPositionMs = 0L, durationMs = song.durationMs, artworkPath = art) }
                updateQueueDisplay()
                persistState()
                return song
            }
        }

        val currId = currentSong.id
        val currIdx = contextSongIds.indexOf(currId)
        if (currIdx > 0) {
            val prevId = contextSongIds[currIdx - 1]
            val song = songMap[prevId]
            if (song != null) {
                val art = CoverArtResolver.getInstance(context).getArtwork(song)
                _queueState.update { it.copy(currentSong = song, currentPositionMs = 0L, durationMs = song.durationMs, artworkPath = art) }
                updateQueueDisplay()
                persistState()
                return song
            }
        }

        _queueState.update { it.copy(currentPositionMs = 0L) }
        persistState()
        return currentSong
    }

    @Synchronized
    fun toggleShuffle(): Boolean {
        val newShuffle = !_queueState.value.isShuffleEnabled
        _queueState.update { it.copy(isShuffleEnabled = newShuffle) }

        if (newShuffle) {
            val currentId = _queueState.value.currentSong?.id
            remainingBag = contextSongIds.filter { it != currentId }.toMutableList()
            Collections.shuffle(remainingBag)
        } else {
            remainingBag.clear()
        }

        updateQueueDisplay()
        persistState()
        return newShuffle
    }

    @Synchronized
    fun toggleRepeat(): Int {
        val nextMode = (_queueState.value.repeatMode + 1) % 3
        _queueState.update { it.copy(repeatMode = nextMode) }
        persistState()
        return nextMode
    }

    @Synchronized
    fun updatePosition(positionMs: Long, durationMs: Long = 0L) {
        persistedPositionMs = positionMs
        _queueState.update {
            it.copy(
                currentPositionMs = positionMs,
                durationMs = if (durationMs > 0) durationMs else it.durationMs
            )
        }
        prefs.edit().putLong(KEY_POSITION_MS, positionMs).apply()
    }

    @Synchronized
    fun setIsPlaying(isPlaying: Boolean) {
        _queueState.update { it.copy(isPlaying = isPlaying) }
    }

    fun getCurrentSong(): Song? = _queueState.value.currentSong

    fun getCurrentPositionMs(): Long = _queueState.value.currentPositionMs

    private fun updateQueueDisplay() {
        val fullQueue = contextSongIds.mapNotNull { songMap[it] }
        val upNext = if (_queueState.value.isShuffleEnabled) {
            remainingBag.mapNotNull { songMap[it] }
        } else {
            val currId = _queueState.value.currentSong?.id
            val currIdx = if (currId != null) contextSongIds.indexOf(currId) else -1
            if (currIdx >= 0 && currIdx < contextSongIds.size - 1) {
                contextSongIds.subList(currIdx + 1, contextSongIds.size).mapNotNull { songMap[it] }
            } else emptyList()
        }

        _queueState.update {
            it.copy(
                queue = fullQueue,
                upNextSongs = upNext
            )
        }
    }
}
