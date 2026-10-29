package com.example.ui.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.app.RecoverableSecurityException
import android.content.Context
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BackgroundVideoManager
import com.example.data.local.BackgroundVideoState
import com.example.data.local.FolderPreferences
import com.example.data.local.PlaylistCoverManager
import com.example.data.local.PlaylistDatabaseHelper
import com.example.data.local.SmartPlaylistEvaluator
import com.example.data.local.SuggestedPlaylist
import com.example.data.local.TabPreferences
import com.example.data.local.VideoFileInfo
import com.example.data.repository.MusicRepository
import com.example.model.Album
import com.example.model.Artist
import com.example.model.MusicFolderItem
import com.example.model.Playlist
import com.example.model.SmartPlaylistRule
import com.example.model.Song
import com.example.data.backup.AutoBackupPreferences
import com.example.data.backup.AutoBackupState
import com.example.data.backup.AutoBackupWorker
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupMode
import com.example.data.backup.BackupSummary
import com.example.data.backup.ImportResult
import com.example.data.backup.UnlinkedSongManager
import com.example.data.local.PlayCountManager
import com.example.playback.MusicPlayerManager
import com.example.playback.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val allDiscoveredSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val folders: List<MusicFolderItem> = emptyList(),
    val enabledFolders: Set<String> = emptySet(),
    val hiddenTabs: Set<String> = emptySet(),
    val coverArtMap: Map<String, String> = emptyMap(),
    val selectedAlbum: Album? = null,
    val selectedArtist: Artist? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistSongs: List<Song> = emptyList(),
    val isScanning: Boolean = false,
    val hasPermission: Boolean = false,
    val searchQuery: String = "",
    val songForFixCover: Song? = null,
    val isSearchingCoverOnline: Boolean = false,
    val recentlyAddedTimeframe: com.example.data.local.RecentlyAddedTimeframe = com.example.data.local.RecentlyAddedTimeframe.LAST_30_DAYS,
    val isSelectionMode: Boolean = false,
    val selectedSongIds: Set<Long> = emptySet(),
    val songsPendingDeletion: List<Song> = emptyList(),
    val showDeleteConfirmDialog: Boolean = false,
    val unlinkedSongCount: Int = 0,
    val userMessage: String? = null
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    private val playlistDbHelper = PlaylistDatabaseHelper.getInstance(application)
    private val folderPreferences = FolderPreferences(application)
    private val tabPreferences = TabPreferences(application)
    private val recentlyAddedPreferences = com.example.data.local.RecentlyAddedPreferences(application)
    val backgroundVideoManager = BackgroundVideoManager.getInstance(application)
    val playerManager = MusicPlayerManager(application, viewModelScope)
    val backupManager = BackupManager.getInstance(application)
    val autoBackupPreferences = AutoBackupPreferences.getInstance(application)
    val autoBackupState: StateFlow<AutoBackupState> = autoBackupPreferences.state
    val unlinkedSongManager = UnlinkedSongManager.getInstance(application)

    val playerState: StateFlow<PlayerState> = playerManager.playerState
    val backgroundVideoState: StateFlow<BackgroundVideoState> = backgroundVideoManager.videoState

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var isObserverRegistered = false
    private var rescanJob: Job? = null
    private var scanJob: Job? = null
    private var mediaStoreChanged = true

    private val mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            mediaStoreChanged = true
            rescanJob?.cancel()
            rescanJob = viewModelScope.launch {
                delay(1200)
                scanLibrary()
            }
        }
    }

    init {
        // Load initial tab preferences and recently added timeframe
        _uiState.update {
            it.copy(
                hiddenTabs = tabPreferences.getHiddenTabs(),
                recentlyAddedTimeframe = recentlyAddedPreferences.getTimeframe()
            )
        }

        viewModelScope.launch {
            unlinkedSongManager.unlinkedCount.collect { count ->
                _uiState.update { it.copy(unlinkedSongCount = count) }
            }
        }

        // Observe playlists
        viewModelScope.launch {
            repository.getPlaylists().collect { playlists ->
                _uiState.update { state ->
                    val selPl = state.selectedPlaylist?.let { cur -> playlists.find { it.id == cur.id } }
                    val plSongs = selPl?.let { pl ->
                        SmartPlaylistEvaluator.evaluate(pl, state.allDiscoveredSongs, state.enabledFolders)
                    } ?: emptyList()
                    state.copy(playlists = playlists, selectedPlaylist = selPl, selectedPlaylistSongs = plSongs)
                }
            }
        }

        // Observe cover art map
        viewModelScope.launch {
            repository.getCoverArtFlow().collect { map ->
                _uiState.update { it.copy(coverArtMap = map) }
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasPermission = isGranted) }
        if (isGranted) {
            registerMediaStoreObserver()
            scanLibrary()
        }
    }

    private fun registerMediaStoreObserver() {
        if (!isObserverRegistered) {
            try {
                getApplication<Application>().contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    true,
                    mediaStoreObserver
                )
                isObserverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onAppResume() {
        backgroundVideoManager.updateBatterySaverStatus()
        // Skip rescan on resume unless MediaStore actually changed
        if (_uiState.value.hasPermission && mediaStoreChanged) {
            scanLibrary()
        }
    }

    fun scanLibrary() {
        // Allow only one scan at a time
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                val rawSongs = repository.queryAllSongs()
                processDiscoveredSongs(rawSongs)
                mediaStoreChanged = false
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun generateSampleTracks() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                repository.generateSampleAudioFiles()
                val rawSongs = repository.queryAllSongs()
                processDiscoveredSongs(rawSongs)
                mediaStoreChanged = false
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    private suspend fun processDiscoveredSongs(allSongs: List<Song>) = withContext(Dispatchers.Default) {
        val folderCounts = mutableMapOf<String, Int>()
        for (song in allSongs) {
            folderCounts[song.folder] = (folderCounts[song.folder] ?: 0) + 1
        }
        val discoveredFolders = folderCounts.keys.toList()

        val (folderItems, enabledFolders) = folderPreferences.reconcileFolders(discoveredFolders, folderCounts)
        val filteredSongs = allSongs.filter { it.folder in enabledFolders }

        // Derive albums and artists from allowed songs
        val albums = filteredSongs.groupBy { it.album to it.artist }.map { (key, group) ->
            Album(
                id = group.first().albumId,
                title = key.first,
                artist = key.second,
                songCount = group.size,
                year = group.first().year
            )
        }.sortedBy { it.title.lowercase() }

        val artists = filteredSongs.groupBy { it.artist }.map { (name, group) ->
            Artist(
                id = group.first().id,
                name = name,
                songCount = group.size,
                albumCount = group.map { it.album }.distinct().size
            )
        }.sortedBy { it.name.lowercase() }

        val currentSelectedPl = _uiState.value.selectedPlaylist
        val updatedPlSongs = currentSelectedPl?.let {
            SmartPlaylistEvaluator.evaluate(it, allSongs, enabledFolders)
        } ?: emptyList()

        val previousSongIds = _uiState.value.songs.map { it.id }
        val newSongIds = filteredSongs.map { it.id }
        val songListChanged = previousSongIds != newSongIds

        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    songs = filteredSongs,
                    allDiscoveredSongs = allSongs,
                    albums = albums,
                    artists = artists,
                    folders = folderItems,
                    enabledFolders = enabledFolders,
                    selectedPlaylistSongs = updatedPlSongs
                )
            }
        }

        // Only update the player queue if the song list actually changed and playback is idle
        if (songListChanged && !playerState.value.isPlaying) {
            playerManager.updateLibrary(
                allSongs = filteredSongs,
                isCurrentTrackPlaying = false,
                allKnownSongs = allSongs
            )
        }

        // Resolve artwork in background on Dispatchers.IO in small batches
        com.example.data.cover.CoverArtResolver.getInstance(getApplication()).resolveLibraryCoversInBackground(filteredSongs)

        // Check for any unlinked songs from backups that can now be linked
        val relinkedMap = mutableMapOf<Long, Long>()
        val relinkedCount = unlinkedSongManager.relinkWithDiscoveredSongs(allSongs) { syntheticId, realId ->
            relinkedMap[syntheticId] = realId
        }
        if (relinkedMap.isNotEmpty()) {
            repository.updatePlaylistsWithRelinkedSongs(relinkedMap)
            backgroundVideoManager.updateSongVideosWithRelinkedSongs(relinkedMap)
            PlayCountManager.getInstance(getApplication()).updatePlayCountsWithRelinkedSongs(relinkedMap)
            withContext(Dispatchers.Main) {
                setUserMessage("Re-linked $relinkedCount song(s) from backup!")
            }
        }
    }

    fun toggleFolder(folderPath: String, isChecked: Boolean) {
        val currentEnabled = _uiState.value.enabledFolders.toMutableSet()
        if (isChecked) {
            currentEnabled.add(folderPath)
        } else {
            currentEnabled.remove(folderPath)
        }

        val allCurrentFolders = _uiState.value.folders.map { it.path }.toSet()
        folderPreferences.updateFolderSelection(currentEnabled, allCurrentFolders)

        val updatedItems = _uiState.value.folders.map {
            if (it.path == folderPath) it.copy(isChecked = isChecked) else it
        }

        val allSongs = _uiState.value.allDiscoveredSongs
        val filteredSongs = allSongs.filter { it.folder in currentEnabled }

        val albums = filteredSongs.groupBy { it.album to it.artist }.map { (key, group) ->
            Album(
                id = group.first().albumId,
                title = key.first,
                artist = key.second,
                songCount = group.size,
                year = group.first().year
            )
        }.sortedBy { it.title.lowercase() }

        val artists = filteredSongs.groupBy { it.artist }.map { (name, group) ->
            Artist(
                id = group.first().id,
                name = name,
                songCount = group.size,
                albumCount = group.map { it.album }.distinct().size
            )
        }.sortedBy { it.name.lowercase() }

        val updatedPlSongs = _uiState.value.selectedPlaylist?.let {
            SmartPlaylistEvaluator.evaluate(it, allSongs, currentEnabled)
        } ?: emptyList()

        _uiState.update {
            it.copy(
                folders = updatedItems,
                enabledFolders = currentEnabled,
                songs = filteredSongs,
                albums = albums,
                artists = artists,
                selectedPlaylistSongs = updatedPlSongs
            )
        }

        playerManager.updateLibrary(
            allSongs = filteredSongs,
            isCurrentTrackPlaying = playerState.value.isPlaying,
            allKnownSongs = allSongs
        )
    }

    fun selectAllFolders() {
        val allFolders = _uiState.value.folders.map { it.path }.toSet()
        folderPreferences.updateFolderSelection(allFolders, allFolders)

        val updatedItems = _uiState.value.folders.map { it.copy(isChecked = true) }
        val allSongs = _uiState.value.allDiscoveredSongs

        val albums = allSongs.groupBy { it.album to it.artist }.map { (key, group) ->
            Album(
                id = group.first().albumId,
                title = key.first,
                artist = key.second,
                songCount = group.size,
                year = group.first().year
            )
        }.sortedBy { it.title.lowercase() }

        val artists = allSongs.groupBy { it.artist }.map { (name, group) ->
            Artist(
                id = group.first().id,
                name = name,
                songCount = group.size,
                albumCount = group.map { it.album }.distinct().size
            )
        }.sortedBy { it.name.lowercase() }

        val updatedPlSongs = _uiState.value.selectedPlaylist?.let {
            SmartPlaylistEvaluator.evaluate(it, allSongs, allFolders)
        } ?: emptyList()

        _uiState.update {
            it.copy(
                folders = updatedItems,
                enabledFolders = allFolders,
                songs = allSongs,
                albums = albums,
                artists = artists,
                selectedPlaylistSongs = updatedPlSongs
            )
        }

        playerManager.updateLibrary(
            allSongs = allSongs,
            isCurrentTrackPlaying = playerState.value.isPlaying,
            allKnownSongs = allSongs
        )
    }

    fun selectNoFolders() {
        val allFolders = _uiState.value.folders.map { it.path }.toSet()
        folderPreferences.updateFolderSelection(emptySet(), allFolders)

        val updatedItems = _uiState.value.folders.map { it.copy(isChecked = false) }

        val updatedPlSongs = _uiState.value.selectedPlaylist?.let {
            SmartPlaylistEvaluator.evaluate(it, _uiState.value.allDiscoveredSongs, emptySet())
        } ?: emptyList()

        _uiState.update {
            it.copy(
                folders = updatedItems,
                enabledFolders = emptySet(),
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                selectedPlaylistSongs = updatedPlSongs
            )
        }

        playerManager.updateLibrary(
            allSongs = emptyList(),
            isCurrentTrackPlaying = playerState.value.isPlaying,
            allKnownSongs = _uiState.value.allDiscoveredSongs
        )
    }

    fun acknowledgeNewFolders() {
        folderPreferences.acknowledgeNewFolders()
        val updated = _uiState.value.folders.map { it.copy(isNew = false) }
        _uiState.update { it.copy(folders = updated) }
    }

    fun setTabHidden(tabName: String, isHidden: Boolean) {
        tabPreferences.setTabHidden(tabName, isHidden)
        _uiState.update { it.copy(hiddenTabs = tabPreferences.getHiddenTabs()) }
    }

    fun setRecentlyAddedTimeframe(timeframe: com.example.data.local.RecentlyAddedTimeframe) {
        recentlyAddedPreferences.setTimeframe(timeframe)
        _uiState.update { it.copy(recentlyAddedTimeframe = timeframe) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectAlbum(album: Album?) {
        _uiState.update { it.copy(selectedAlbum = album) }
    }

    fun selectArtist(artist: Artist?) {
        _uiState.update { it.copy(selectedArtist = artist) }
    }

    fun selectPlaylist(playlist: Playlist?) {
        val plSongs = playlist?.let { pl ->
            SmartPlaylistEvaluator.evaluate(pl, _uiState.value.allDiscoveredSongs, _uiState.value.enabledFolders)
        } ?: emptyList()
        _uiState.update { it.copy(selectedPlaylist = playlist, selectedPlaylistSongs = plSongs) }
    }

    fun getPlaylistSongs(playlist: Playlist): List<Song> {
        return SmartPlaylistEvaluator.evaluate(
            playlist = playlist,
            allSongs = _uiState.value.allDiscoveredSongs,
            enabledFolders = _uiState.value.enabledFolders
        )
    }

    fun createPlaylist(name: String): Long = repository.createPlaylist(name)

    fun createSmartPlaylist(
        name: String,
        matchAllRules: Boolean,
        rules: List<SmartPlaylistRule>,
        exclusionKeywords: String
    ): Long {
        return repository.createSmartPlaylist(name, matchAllRules, rules, exclusionKeywords)
    }

    fun convertSmartToRegularPlaylist(playlistId: Long) {
        val targetPl = _uiState.value.playlists.find { it.id == playlistId } ?: return
        val currentSongs = getPlaylistSongs(targetPl)
        repository.convertSmartToRegularPlaylist(playlistId, currentSongs.map { it.id })
    }

    fun setPlaylistCustomCover(playlistId: Long, imageUri: Uri) {
        viewModelScope.launch {
            val coverPath = PlaylistCoverManager.getInstance(getApplication()).saveCustomCover(playlistId, imageUri)
            if (coverPath != null) {
                repository.setPlaylistCustomCover(playlistId, coverPath)
            }
        }
    }

    fun removePlaylistCustomCover(playlistId: Long) {
        PlaylistCoverManager.getInstance(getApplication()).removeCustomCover(playlistId)
        repository.removePlaylistCustomCover(playlistId)
    }

    fun getSuggestedPlaylists(): List<SuggestedPlaylist> {
        return SmartPlaylistEvaluator.suggestPlaylists(
            allSongs = _uiState.value.allDiscoveredSongs,
            enabledFolders = _uiState.value.enabledFolders,
            existingPlaylistNames = _uiState.value.playlists.map { it.name }.toSet()
        )
    }

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        val plId = repository.createPlaylist(name)
        repository.addSongToPlaylist(plId, songId)
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        repository.addSongToPlaylist(playlistId, songId)
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        repository.removeSongFromPlaylist(playlistId, songId)
    }

    fun deletePlaylist(playlistId: Long) {
        repository.deletePlaylist(playlistId)
        if (_uiState.value.selectedPlaylist?.id == playlistId) {
            selectPlaylist(null)
        }
    }

    // Cover Art
    fun openFixCoverDialog(song: Song) {
        _uiState.update { it.copy(songForFixCover = song) }
    }

    fun closeFixCoverDialog() {
        _uiState.update { it.copy(songForFixCover = null) }
    }

    fun searchCoverOnline(album: String, artist: String, title: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingCoverOnline = true) }
            repository.searchOnlineCover(album, artist, title)
            _uiState.update { it.copy(isSearchingCoverOnline = false, songForFixCover = null) }
        }
    }

    fun setCustomCoverArt(album: String, artist: String, uri: Uri) {
        val path = repository.saveCustomCover(album, artist, uri)
        playerManager.onArtworkChanged(album, artist, path)
        closeFixCoverDialog()
    }

    fun fetchMissingCovers() {
        viewModelScope.launch {
            for (song in _uiState.value.songs) {
                repository.resolveCover(song)
            }
        }
    }

    // Playback Controls
    fun playSong(song: Song, contextSongs: List<Song> = emptyList()) {
        playerManager.playSong(song, contextSongs)
    }

    fun playAll(songs: List<Song>) {
        playerManager.playAll(songs)
    }

    fun shuffleAll(songs: List<Song>) {
        playerManager.shuffleAll(songs)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekToNext() {
        playerManager.seekToNext()
    }

    fun seekToPrevious() {
        playerManager.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun toggleRepeatMode() {
        playerManager.toggleRepeatMode()
    }

    // Background Video Management
    fun inspectVideoUri(uri: Uri): VideoFileInfo = backgroundVideoManager.inspectVideoUri(uri)

    fun setGlobalBackgroundVideo(uri: Uri, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = backgroundVideoManager.setGlobalVideo(uri)
            onResult?.invoke(success)
        }
    }

    fun removeGlobalBackgroundVideo() {
        backgroundVideoManager.removeGlobalVideo()
    }

    fun setSongBackgroundVideo(songId: Long, uri: Uri, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = backgroundVideoManager.setSongVideo(songId, uri)
            onResult?.invoke(success)
        }
    }

    fun setSongsBackgroundVideo(songIds: Collection<Long>, uri: Uri, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = backgroundVideoManager.setSongsVideo(songIds, uri)
            onResult?.invoke(success)
        }
    }

    fun removeSongBackgroundVideo(songId: Long) {
        backgroundVideoManager.removeSongVideo(songId)
    }

    fun removeSongsBackgroundVideo(songIds: Collection<Long>) {
        backgroundVideoManager.removeSongsVideo(songIds)
    }

    fun setDisableVideoOnBatterySaver(disable: Boolean) {
        backgroundVideoManager.setDisableOnBatterySaver(disable)
    }

    fun setShowLargeAlbumArtWithVideo(show: Boolean) {
        backgroundVideoManager.setShowLargeAlbumArt(show)
    }

    fun setVideoDimming(dimming: Float) {
        backgroundVideoManager.setVideoDimming(dimming)
    }

    // --- Bulk Selection & Deletion ---

    fun startSelection(initialSongId: Long) {
        _uiState.update {
            it.copy(
                isSelectionMode = true,
                selectedSongIds = setOf(initialSongId)
            )
        }
    }

    fun toggleSongSelection(songId: Long) {
        _uiState.update { current ->
            val updated = current.selectedSongIds.toMutableSet()
            if (songId in updated) {
                updated.remove(songId)
            } else {
                updated.add(songId)
            }
            current.copy(
                selectedSongIds = updated,
                isSelectionMode = updated.isNotEmpty()
            )
        }
    }

    fun selectAllVisible(visibleSongs: List<Song>) {
        val allIds = visibleSongs.map { it.id }.toSet()
        _uiState.update {
            it.copy(
                selectedSongIds = allIds,
                isSelectionMode = allIds.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                isSelectionMode = false,
                selectedSongIds = emptySet()
            )
        }
    }

    fun requestDeleteSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        _uiState.update {
            it.copy(
                songsPendingDeletion = songs,
                showDeleteConfirmDialog = true
            )
        }
    }

    fun requestDeleteSelected(availableSongs: List<Song> = _uiState.value.songs) {
        val selectedIds = _uiState.value.selectedSongIds
        val toDelete = availableSongs.filter { it.id in selectedIds }
        requestDeleteSongs(toDelete)
    }

    fun cancelPendingDelete() {
        _uiState.update {
            it.copy(
                songsPendingDeletion = emptyList(),
                showDeleteConfirmDialog = false
            )
        }
    }

    fun confirmDeletePendingSongs(
        context: Context = getApplication(),
        onLaunchIntentSender: ((IntentSenderRequest) -> Unit)? = null
    ) {
        val pending = _uiState.value.songsPendingDeletion
        if (pending.isEmpty()) {
            cancelPendingDelete()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteConfirmDialog = false) }

            // Split into file-based/sample and MediaStore content URIs
            val fileSongs = pending.filter { it.contentUri.scheme == "file" }
            val mediaStoreSongs = pending.filter { it.contentUri.scheme != "file" }

            // Delete file-based directly
            fileSongs.forEach { song ->
                try {
                    val file = java.io.File(song.contentUri.path ?: "")
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (mediaStoreSongs.isEmpty()) {
                // All deleted directly
                finalizeDeletion(pending.map { it.id }.toSet())
                return@launch
            }

            // On Android 11+ (API 30+), request batch confirmation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && onLaunchIntentSender != null) {
                try {
                    val uris = mediaStoreSongs.map { it.contentUri }
                    val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
                    val request = IntentSenderRequest.Builder(pi.intentSender).build()
                    onLaunchIntentSender(request)
                    return@launch
                } catch (e: Exception) {
                    // Fall back to direct delete (e.g. in Robolectric or if exception occurs)
                }
            }

            // Android 10 (Q) RecoverableSecurityException or direct delete on older versions / fallback
            val deletedIds = fileSongs.map { it.id }.toMutableSet()
            for (song in mediaStoreSongs) {
                try {
                    val count = context.contentResolver.delete(song.contentUri, null, null)
                    if (count > 0) {
                        deletedIds.add(song.id)
                    }
                } catch (sec: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException && onLaunchIntentSender != null) {
                        val request = IntentSenderRequest.Builder(sec.userAction.actionIntent.intentSender).build()
                        onLaunchIntentSender(request)
                        return@launch
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            finalizeDeletion(if (deletedIds.isNotEmpty()) deletedIds else pending.map { it.id }.toSet())
        }
    }

    fun onBatchDeleteSystemConfirmed() {
        val pending = _uiState.value.songsPendingDeletion
        finalizeDeletion(pending.map { it.id }.toSet())
    }

    fun onBatchDeleteCancelled() {
        cancelPendingDelete()
    }

    fun finalizeDeletion(deletedSongIds: Set<Long>) {
        if (deletedSongIds.isEmpty()) {
            cancelPendingDelete()
            return
        }

        val count = deletedSongIds.size
        val msg = if (count == 1) "Deleted 1 song" else "Deleted $count songs"
        _uiState.update {
            it.copy(
                isSelectionMode = false,
                selectedSongIds = emptySet(),
                songsPendingDeletion = emptyList(),
                showDeleteConfirmDialog = false,
                userMessage = msg
            )
        }

        viewModelScope.launch {
            val remainingDiscovered = _uiState.value.allDiscoveredSongs.filter { it.id !in deletedSongIds }

            // Update playlists in repo and db
            repository.removeSongsFromPlaylists(deletedSongIds)
            playlistDbHelper.removeSongsFromAllPlaylists(deletedSongIds)

            // Remove custom videos
            deletedSongIds.forEach { id ->
                backgroundVideoManager.removeSongVideo(id)
            }

            // Update player & queue (skips track if current was deleted, or stops)
            playerManager.onSongsDeleted(deletedSongIds)

            // Recalculate albums, artists, folders
            processDiscoveredSongs(remainingDiscovered)
        }
    }

    fun setUserMessage(msg: String) {
        _uiState.update { it.copy(userMessage = msg) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun computeBackupMediaSize(): Long = backupManager.computeMediaSizeBytes()

    fun exportBackup(uri: Uri, includeMedia: Boolean, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val cr = getApplication<Application>().contentResolver
                cr.openOutputStream(uri)?.use { out ->
                    val result = backupManager.exportBackup(
                        outputStream = out,
                        includeMedia = includeMedia,
                        allDiscoveredSongs = _uiState.value.allDiscoveredSongs
                    )
                    if (result.isSuccess) {
                        setUserMessage("Backup exported successfully")
                        onComplete(true)
                    } else {
                        setUserMessage("Export failed: ${result.exceptionOrNull()?.message}")
                        onComplete(false)
                    }
                } ?: run {
                    setUserMessage("Failed to open destination file for export")
                    onComplete(false)
                }
            } catch (e: Exception) {
                setUserMessage("Export failed: ${e.message}")
                onComplete(false)
            }
        }
    }

    suspend fun inspectBackup(uri: Uri): BackupSummary? {
        return withContext(Dispatchers.IO) {
            try {
                val cr = getApplication<Application>().contentResolver
                cr.openInputStream(uri)?.use { ins ->
                    backupManager.inspectBackup(ins, _uiState.value.allDiscoveredSongs)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun importBackup(uri: Uri, mode: BackupMode, onComplete: (ImportResult) -> Unit) {
        viewModelScope.launch {
            try {
                val cr = getApplication<Application>().contentResolver
                val result = cr.openInputStream(uri)?.use { ins ->
                    backupManager.importBackup(ins, mode, _uiState.value.allDiscoveredSongs)
                } ?: ImportResult(false, "Failed reading backup file")

                if (result.success) {
                    _uiState.update {
                        it.copy(
                            hiddenTabs = tabPreferences.getHiddenTabs(),
                            recentlyAddedTimeframe = recentlyAddedPreferences.getTimeframe()
                        )
                    }
                    scanLibrary()
                    val note = if (result.unlinkedSongs > 0) {
                        " (${result.unlinkedSongs} songs not found, preserved in playlists)"
                    } else ""
                    setUserMessage("Backup imported: ${result.playlistsImported} playlist(s)$note")
                } else {
                    setUserMessage(result.message)
                }
                onComplete(result)
            } catch (e: Exception) {
                val err = ImportResult(false, "Import failed: ${e.message}")
                setUserMessage(err.message)
                onComplete(err)
            }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        autoBackupPreferences.setEnabled(enabled)
        if (enabled) {
            AutoBackupWorker.schedule(getApplication())
        } else {
            AutoBackupWorker.cancel(getApplication())
        }
    }

    fun setAutoBackupFolder(uri: Uri?, displayName: String?) {
        autoBackupPreferences.setFolder(uri, displayName)
        if (uri != null && autoBackupState.value.isEnabled) {
            AutoBackupWorker.schedule(getApplication())
        }
    }

    fun setAutoBackupIncludeMedia(include: Boolean) {
        autoBackupPreferences.setIncludeMedia(include)
    }

    fun runAutoBackupNow() {
        AutoBackupWorker.runOnceNow(getApplication())
        setUserMessage("Auto backup started")
    }

    override fun onCleared() {
        super.onCleared()
        if (isObserverRegistered) {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(mediaStoreObserver)
            } catch (ignored: Exception) {}
        }
        playerManager.release()
        backgroundVideoManager.release()
    }
}
