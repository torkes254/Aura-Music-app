package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.RecentlyAddedTimeframe
import com.example.data.local.VideoFileInfo
import com.example.model.Playlist
import com.example.model.RecentlyAddedGrouper
import com.example.model.Song
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.data.backup.BackupMode
import com.example.data.backup.BackupSummary
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.ExportBackupDialog
import com.example.ui.components.FixCoverDialog
import com.example.ui.components.ImportSummaryDialog
import com.example.ui.components.MiniPlayer
import com.example.ui.components.MusicFoldersSheet
import com.example.ui.components.NowPlayingScreen
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.SmartPlaylistDialog
import com.example.ui.components.SuggestPlaylistsSheet
import com.example.ui.components.VideoScopeDialog
import com.example.ui.screens.BackgroundVideoScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val backgroundVideoState by viewModel.backgroundVideoState.collectAsState()

    var selectedTab by remember { mutableStateOf("Songs") }
    val allTabs = listOf("Songs", "Recently added", "Albums", "Artists", "Playlists")
    val visibleTabs = remember(uiState.hiddenTabs) {
        allTabs.filter { it == "Songs" || it !in uiState.hiddenTabs }
    }

    LaunchedEffect(visibleTabs) {
        if (selectedTab !in visibleTabs) {
            selectedTab = "Songs"
        }
    }

    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var songForPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showBackgroundVideoScreen by remember { mutableStateOf(false) }
    var showMusicFoldersSheet by remember { mutableStateOf(false) }
    var showSmartPlaylistDialog by remember { mutableStateOf(false) }
    var showSuggestPlaylistsSheet by remember { mutableStateOf(false) }
    var targetPlaylistForCover by remember { mutableStateOf<Playlist?>(null) }

    val playlistCoverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            targetPlaylistForCover?.let { pl ->
                viewModel.setPlaylistCustomCover(pl.id, uri)
            }
        }
        targetPlaylistForCover = null
    }

    // Video picking state & warning/scope dialogs
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoFileInfo by remember { mutableStateOf<VideoFileInfo?>(null) }
    var pendingVideoSongIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showVideoSizeWarningDialog by remember { mutableStateOf(false) }
    var showVideoScopeDialog by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileInfo = viewModel.inspectVideoUri(uri)
            pendingVideoUri = uri
            pendingVideoFileInfo = fileInfo
            if (fileInfo.isOver50MB) {
                showVideoSizeWarningDialog = true
            } else {
                showVideoScopeDialog = true
            }
        } else {
            pendingVideoSongIds = emptyList()
        }
    }

    // Delete intent sender launcher for Android 11+ MediaStore batch delete / Android 10 RecoverableSecurityException
    val deleteIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onBatchDeleteSystemConfirmed()
        } else {
            viewModel.onBatchDeleteCancelled()
        }
    }

    // Backup & Restore state & launchers
    val autoBackupState by viewModel.autoBackupState.collectAsState()
    val scope = rememberCoroutineScope()

    var showExportBackupDialog by remember { mutableStateOf(false) }
    var exportIncludeMedia by remember { mutableStateOf(true) }
    var exportMediaSizeBytes by remember { mutableStateOf(0L) }

    var showImportSummaryDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var isImportInspecting by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackup(uri, exportIncludeMedia) { }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
            isImportInspecting = true
            scope.launch {
                val summary = viewModel.inspectBackup(uri)
                isImportInspecting = false
                if (summary != null) {
                    pendingImportSummary = summary
                    showImportSummaryDialog = true
                } else {
                    viewModel.setUserMessage("Failed to inspect backup file. Invalid format.")
                }
            }
        }
    }

    val autoBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Ignore if not supported
            }
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val displayName = docId.substringAfterLast(':').ifEmpty { "Selected folder" }
            viewModel.setAutoBackupFolder(uri, displayName)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Active video for current song
    val activeVideoPath = remember(playerState.currentSong?.id, backgroundVideoState) {
        backgroundVideoState.activeVideoForSong(playerState.currentSong?.id)
    }

    // Check permissions
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[audioPermission] == true
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(audioGranted)

        if (!audioGranted) {
            val permissionsToRequest = mutableListOf(audioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Back handling - selection mode and screens
    BackHandler(enabled = showBackgroundVideoScreen || uiState.isSelectionMode || isNowPlayingExpanded || uiState.selectedAlbum != null || uiState.selectedArtist != null || uiState.selectedPlaylist != null || isSearchActive) {
        when {
            showBackgroundVideoScreen -> showBackgroundVideoScreen = false
            uiState.isSelectionMode -> viewModel.clearSelection()
            isNowPlayingExpanded -> isNowPlayingExpanded = false
            uiState.selectedAlbum != null -> viewModel.selectAlbum(null)
            uiState.selectedArtist != null -> viewModel.selectArtist(null)
            uiState.selectedPlaylist != null -> viewModel.selectPlaylist(null)
            isSearchActive -> {
                isSearchActive = false
                viewModel.setSearchQuery("")
            }
        }
    }

    val currentSongId = playerState.currentSong?.id

    val query = uiState.searchQuery.trim().lowercase()
    val filteredSongs = remember(query, uiState.songs) {
        if (query.isEmpty()) {
            uiState.songs
        } else {
            uiState.songs.filter {
                it.title.lowercase().contains(query) ||
                        it.artist.lowercase().contains(query) ||
                        it.album.lowercase().contains(query)
            }
        }
    }

    val filteredAlbums = remember(query, uiState.albums) {
        if (query.isEmpty()) {
            uiState.albums
        } else {
            uiState.albums.filter {
                it.title.lowercase().contains(query) ||
                        it.artist.lowercase().contains(query)
            }
        }
    }

    val filteredArtists = remember(query, uiState.artists) {
        if (query.isEmpty()) {
            uiState.artists
        } else {
            uiState.artists.filter {
                it.name.lowercase().contains(query)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(bottom = if (playerState.currentSong != null && !isNowPlayingExpanded) 76.dp else 16.dp)
                    .testTag("app_snackbar_host")
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar / Selection Top Bar
                if (uiState.selectedAlbum == null && uiState.selectedArtist == null && uiState.selectedPlaylist == null) {
                    if (uiState.isSelectionMode) {
                        val currentVisibleSongs = when (selectedTab) {
                            "Recently added" -> RecentlyAddedGrouper.filterByTimeframe(filteredSongs, uiState.recentlyAddedTimeframe)
                            else -> filteredSongs
                        }
                        SelectionTopBar(
                            selectedCount = uiState.selectedSongIds.size,
                            onSelectAllClick = { viewModel.selectAllVisible(currentVisibleSongs) },
                            onDeleteClick = { viewModel.requestDeleteSelected() },
                            onCancelClick = { viewModel.clearSelection() },
                            onSetVideoClick = {
                                pendingVideoSongIds = uiState.selectedSongIds.toList()
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSearchActive) {
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Search songs, albums, artists...") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            isSearchActive = false
                                            viewModel.setSearchQuery("")
                                        }) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close Search")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("search_text_field")
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Aura Music",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { isSearchActive = true },
                                        modifier = Modifier.testTag("open_search_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = TextSecondary
                                        )
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { showSettingsSheet = true },
                                            modifier = Modifier.testTag("open_settings_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Settings",
                                                tint = TextSecondary
                                            )
                                        }
                                        if (uiState.folders.any { it.isNew }) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(top = 6.dp, end = 6.dp)
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                                    .testTag("settings_has_new_badge_dot")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Main Content
                if (!uiState.hasPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Storage Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aura Music needs permission to scan and play audio files stored on your device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    val permissionsToRequest = mutableListOf(audioPermission)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_permission_button")
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                } else {
                    when {
                        uiState.selectedAlbum != null -> {
                            AlbumDetailScreen(
                                album = uiState.selectedAlbum!!,
                                songs = uiState.songs,
                                currentSongId = currentSongId,
                                isPlaying = playerState.isPlaying,
                                onBackClick = { viewModel.selectAlbum(null) },
                                onSongClick = { song -> viewModel.playSong(song, uiState.songs.filter { it.albumId == uiState.selectedAlbum!!.id }) },
                                onPlayAllClick = {
                                    val albumSongs = uiState.songs.filter { it.albumId == uiState.selectedAlbum!!.id }
                                    viewModel.playAll(albumSongs)
                                },
                                onShuffleClick = {
                                    val albumSongs = uiState.songs.filter { it.albumId == uiState.selectedAlbum!!.id }
                                    viewModel.shuffleAll(albumSongs)
                                },
                                onAddToPlaylistClick = { song -> songForPlaylistDialog = song },
                                coverArtMap = uiState.coverArtMap,
                                onFixCoverClick = { song -> viewModel.openFixCoverDialog(song) },
                                songVideos = backgroundVideoState.songVideos,
                                onSetBackgroundVideoClick = { song ->
                                    pendingVideoSongIds = listOf(song.id)
                                    videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
                                onRemoveBackgroundVideoClick = { song -> viewModel.removeSongBackgroundVideo(song.id) },
                                isSelectionMode = uiState.isSelectionMode,
                                selectedSongIds = uiState.selectedSongIds,
                                onToggleSelect = { song -> viewModel.toggleSongSelection(song.id) },
                                onStartSelection = { song -> viewModel.startSelection(song.id) },
                                onDeleteSongClick = { song -> viewModel.requestDeleteSongs(listOf(song)) },
                                onSelectAllClick = {
                                    val albumSongs = uiState.songs.filter { it.albumId == uiState.selectedAlbum!!.id || it.album.equals(uiState.selectedAlbum!!.title, ignoreCase = true) }
                                    viewModel.selectAllVisible(albumSongs)
                                },
                                onDeleteSelectedClick = { viewModel.requestDeleteSelected() },
                                onCancelSelectionClick = { viewModel.clearSelection() },
                                onSetVideoClick = {
                                    pendingVideoSongIds = uiState.selectedSongIds.toList()
                                    videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                }
                            )
                        }

                        uiState.selectedArtist != null -> {
                            ArtistDetailScreen(
                                artist = uiState.selectedArtist!!,
                                songs = uiState.songs,
                                currentSongId = currentSongId,
                                isPlaying = playerState.isPlaying,
                                onBackClick = { viewModel.selectArtist(null) },
                                onSongClick = { song -> viewModel.playSong(song, uiState.songs.filter { it.artist.equals(uiState.selectedArtist!!.name, ignoreCase = true) }) },
                                onPlayAllClick = {
                                    val artistSongs = uiState.songs.filter { it.artist.equals(uiState.selectedArtist!!.name, ignoreCase = true) }
                                    viewModel.playAll(artistSongs)
                                },
                                onShuffleClick = {
                                    val artistSongs = uiState.songs.filter { it.artist.equals(uiState.selectedArtist!!.name, ignoreCase = true) }
                                    viewModel.shuffleAll(artistSongs)
                                },
                                onAddToPlaylistClick = { song -> songForPlaylistDialog = song },
                                coverArtMap = uiState.coverArtMap,
                                onFixCoverClick = { song -> viewModel.openFixCoverDialog(song) },
                                songVideos = backgroundVideoState.songVideos,
                                onSetBackgroundVideoClick = { song ->
                                    pendingVideoSongIds = listOf(song.id)
                                    videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
                                onRemoveBackgroundVideoClick = { song -> viewModel.removeSongBackgroundVideo(song.id) },
                                isSelectionMode = uiState.isSelectionMode,
                                selectedSongIds = uiState.selectedSongIds,
                                onToggleSelect = { song -> viewModel.toggleSongSelection(song.id) },
                                onStartSelection = { song -> viewModel.startSelection(song.id) },
                                onDeleteSongClick = { song -> viewModel.requestDeleteSongs(listOf(song)) },
                                onSelectAllClick = {
                                    val artistSongs = uiState.songs.filter { it.artist.equals(uiState.selectedArtist!!.name, ignoreCase = true) }
                                    viewModel.selectAllVisible(artistSongs)
                                },
                                onDeleteSelectedClick = { viewModel.requestDeleteSelected() },
                                onCancelSelectionClick = { viewModel.clearSelection() },
                                onSetVideoClick = {
                                    pendingVideoSongIds = uiState.selectedSongIds.toList()
                                    videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                }
                            )
                        }

                        uiState.selectedPlaylist != null -> {
                            PlaylistDetailScreen(
                                playlist = uiState.selectedPlaylist!!,
                                songs = uiState.selectedPlaylistSongs,
                                currentSongId = currentSongId,
                                isPlaying = playerState.isPlaying,
                                onBackClick = { viewModel.selectPlaylist(null) },
                                onSongClick = { song -> viewModel.playSong(song, uiState.selectedPlaylistSongs) },
                                onPlayAllClick = { viewModel.playAll(uiState.selectedPlaylistSongs) },
                                onShuffleClick = { viewModel.shuffleAll(uiState.selectedPlaylistSongs) },
                                onAddToPlaylistClick = { song -> songForPlaylistDialog = song },
                                onRemoveFromPlaylistClick = { song -> viewModel.removeSongFromPlaylist(uiState.selectedPlaylist!!.id, song.id) },
                                coverArtMap = uiState.coverArtMap,
                                onChangeCoverClick = {
                                    targetPlaylistForCover = uiState.selectedPlaylist
                                    playlistCoverPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onRemoveCoverClick = {
                                    viewModel.removePlaylistCustomCover(uiState.selectedPlaylist!!.id)
                                },
                                onConvertToRegularClick = {
                                    viewModel.convertSmartToRegularPlaylist(uiState.selectedPlaylist!!.id)
                                },
                                onDeletePlaylistClick = {
                                    val plId = uiState.selectedPlaylist!!.id
                                    viewModel.deletePlaylist(plId)
                                },
                                songVideos = backgroundVideoState.songVideos,
                                onSetBackgroundVideoClick = { song ->
                                    pendingVideoSongIds = listOf(song.id)
                                    videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
                                onRemoveBackgroundVideoClick = { song -> viewModel.removeSongBackgroundVideo(song.id) }
                            )
                        }

                        else -> {
                            // Tabs
                            val currentTabIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)
                            TabRow(
                                selectedTabIndex = currentTabIndex,
                                containerColor = DarkBackground,
                                contentColor = TextPrimary,
                                indicator = { tabPositions ->
                                    if (tabPositions.isNotEmpty() && currentTabIndex in tabPositions.indices) {
                                        TabRowDefaults.SecondaryIndicator(
                                            Modifier.tabIndicatorOffset(tabPositions[currentTabIndex]),
                                            color = MaterialTheme.colorScheme.primary,
                                            height = 2.5.dp
                                        )
                                    }
                                },
                                divider = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(DarkSurfaceBorder)
                                    )
                                }
                            ) {
                                visibleTabs.forEach { title ->
                                    Tab(
                                        selected = selectedTab == title,
                                        onClick = { selectedTab = title },
                                        text = {
                                            Text(
                                                text = title,
                                                fontWeight = if (selectedTab == title) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                        },
                                        modifier = Modifier.testTag("tab_$title")
                                    )
                                }
                            }

                            when (selectedTab) {
                                "Songs" -> SongsScreen(
                                    songs = filteredSongs,
                                    currentSongId = currentSongId,
                                    isPlaying = playerState.isPlaying,
                                    isScanning = uiState.isScanning,
                                    isShuffleEnabled = playerState.isShuffleEnabled,
                                    onSongClick = { song -> viewModel.playSong(song, filteredSongs) },
                                    onPlayAllClick = { viewModel.playAll(filteredSongs) },
                                    onShuffleAllClick = { viewModel.shuffleAll(filteredSongs) },
                                    onAddToPlaylistClick = { song -> songForPlaylistDialog = song },
                                    onRescanClick = { viewModel.scanLibrary() },
                                    onGenerateSampleTracksClick = { viewModel.generateSampleTracks() },
                                    coverArtMap = uiState.coverArtMap,
                                    onFixCoverClick = { song -> viewModel.openFixCoverDialog(song) },
                                    songVideos = backgroundVideoState.songVideos,
                                    onSetBackgroundVideoClick = { song ->
                                        pendingVideoSongIds = listOf(song.id)
                                        videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                    },
                                    onRemoveBackgroundVideoClick = { song -> viewModel.removeSongBackgroundVideo(song.id) },
                                    isSelectionMode = uiState.isSelectionMode,
                                    selectedSongIds = uiState.selectedSongIds,
                                    onToggleSelect = { song -> viewModel.toggleSongSelection(song.id) },
                                    onStartSelection = { song -> viewModel.startSelection(song.id) },
                                    onDeleteSongClick = { song -> viewModel.requestDeleteSongs(listOf(song)) }
                                )

                                "Recently added" -> {
                                    val filteredRecentSongs = RecentlyAddedGrouper.filterByTimeframe(
                                        filteredSongs,
                                        uiState.recentlyAddedTimeframe
                                    )
                                    RecentlyAddedScreen(
                                        songs = filteredSongs,
                                        timeframe = uiState.recentlyAddedTimeframe,
                                        currentSongId = currentSongId,
                                        isPlaying = playerState.isPlaying,
                                        isScanning = uiState.isScanning,
                                        onSongClick = { song -> viewModel.playSong(song, filteredRecentSongs) },
                                        onPlayAllClick = { viewModel.playAll(filteredRecentSongs) },
                                        onShuffleAllClick = { viewModel.shuffleAll(filteredRecentSongs) },
                                        onAddToPlaylistClick = { song -> songForPlaylistDialog = song },
                                        onRescanClick = { viewModel.scanLibrary() },
                                        onGenerateSampleTracksClick = { viewModel.generateSampleTracks() },
                                        onSelectTimeframe = { tf -> viewModel.setRecentlyAddedTimeframe(tf) },
                                        coverArtMap = uiState.coverArtMap,
                                        onFixCoverClick = { song -> viewModel.openFixCoverDialog(song) },
                                        songVideos = backgroundVideoState.songVideos,
                                        onSetBackgroundVideoClick = { song ->
                                            pendingVideoSongIds = listOf(song.id)
                                            videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                        },
                                        onRemoveBackgroundVideoClick = { song -> viewModel.removeSongBackgroundVideo(song.id) },
                                        isSelectionMode = uiState.isSelectionMode,
                                        selectedSongIds = uiState.selectedSongIds,
                                        onToggleSelect = { song -> viewModel.toggleSongSelection(song.id) },
                                        onStartSelection = { song -> viewModel.startSelection(song.id) },
                                        onDeleteSongClick = { song -> viewModel.requestDeleteSongs(listOf(song)) }
                                    )
                                }

                                "Albums" -> AlbumsScreen(
                                    albums = filteredAlbums,
                                    onAlbumClick = { album -> viewModel.selectAlbum(album) },
                                    coverArtMap = uiState.coverArtMap
                                )

                                "Artists" -> ArtistsScreen(
                                    artists = filteredArtists,
                                    onArtistClick = { artist -> viewModel.selectArtist(artist) }
                                )

                                "Playlists" -> PlaylistsScreen(
                                    playlists = uiState.playlists,
                                    getPlaylistSongs = { pl -> viewModel.getPlaylistSongs(pl) },
                                    onPlaylistClick = { pl -> viewModel.selectPlaylist(pl) },
                                    onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                    onOpenSmartPlaylistDialog = { showSmartPlaylistDialog = true },
                                    onOpenSuggestSheet = { showSuggestPlaylistsSheet = true },
                                    onChangeCoverClick = { pl ->
                                        targetPlaylistForCover = pl
                                        playlistCoverPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onRemoveCoverClick = { pl ->
                                        viewModel.removePlaylistCustomCover(pl.id)
                                    },
                                    onConvertToRegularClick = { pl ->
                                        viewModel.convertSmartToRegularPlaylist(pl.id)
                                    },
                                    onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                                    coverArtMap = uiState.coverArtMap
                                )
                            }
                        }
                    }
                }
            }

            // Bottom MiniPlayer
            if (playerState.currentSong != null && !isNowPlayingExpanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    MiniPlayer(
                        playerState = playerState,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onSkipNextClick = { viewModel.seekToNext() },
                        onExpand = { isNowPlayingExpanded = true },
                        coverArtMap = uiState.coverArtMap
                    )
                }
            }

            // Full Screen Now Playing Overlay
            AnimatedVisibility(
                visible = isNowPlayingExpanded && playerState.currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                NowPlayingScreen(
                    playerState = playerState,
                    onCollapse = { isNowPlayingExpanded = false },
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onPreviousClick = { viewModel.seekToPrevious() },
                    onNextClick = { viewModel.seekToNext() },
                    onSeekTo = { pos -> viewModel.seekTo(pos) },
                    onToggleRepeat = { viewModel.toggleRepeatMode() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onSelectQueueItem = { qSong -> viewModel.playSong(qSong) },
                    coverArtMap = uiState.coverArtMap,
                    videoPath = activeVideoPath,
                    showLargeAlbumArtSetting = backgroundVideoState.showLargeAlbumArtWithVideo,
                    videoDimming = backgroundVideoState.videoDimming,
                    onSetBackgroundVideoClick = { song ->
                        pendingVideoSongIds = listOf(song.id)
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onRemoveBackgroundVideoClick = { song ->
                        viewModel.removeSongBackgroundVideo(song.id)
                    }
                )
            }

            // Add To Playlist Dialog
            if (songForPlaylistDialog != null) {
                AddToPlaylistDialog(
                    song = songForPlaylistDialog!!,
                    playlists = uiState.playlists,
                    onDismiss = { songForPlaylistDialog = null },
                    onAddToPlaylist = { playlistId ->
                        viewModel.addSongToPlaylist(playlistId, songForPlaylistDialog!!.id)
                    },
                    onCreatePlaylistAndAdd = { name ->
                        viewModel.createPlaylistAndAddSong(name, songForPlaylistDialog!!.id)
                    }
                )
            }

            // Fix Cover Dialog
            if (uiState.songForFixCover != null) {
                FixCoverDialog(
                    song = uiState.songForFixCover!!,
                    onDismiss = { viewModel.closeFixCoverDialog() },
                    onSearchOnline = { album, artist, title ->
                        viewModel.searchCoverOnline(album, artist, title)
                    },
                    onPickLocalImage = { uri ->
                        val currentSong = uiState.songForFixCover
                        if (currentSong != null) {
                            viewModel.setCustomCoverArt(currentSong.album, currentSong.artist, uri)
                        }
                    },
                    isSearching = uiState.isSearchingCoverOnline
                )
            }

            // Delete Confirmation Dialog
            if (uiState.showDeleteConfirmDialog && uiState.songsPendingDeletion.isNotEmpty()) {
                val count = uiState.songsPendingDeletion.size
                val dialogTitle = if (count == 1) "Delete song?" else "Delete $count songs?"
                val dialogText = if (count == 1) {
                    "Delete 1 song from your phone? This can't be undone."
                } else {
                    "Delete $count songs from your phone? This can't be undone."
                }
                AlertDialog(
                    onDismissRequest = { viewModel.cancelPendingDelete() },
                    title = {
                        Text(
                            text = dialogTitle,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    },
                    text = {
                        Text(
                            text = dialogText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.confirmDeletePendingSongs { intentSenderRequest ->
                                    deleteIntentSenderLauncher.launch(intentSenderRequest)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.testTag("confirm_delete_button")
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.cancelPendingDelete() },
                            modifier = Modifier.testTag("cancel_delete_button")
                        ) {
                            Text("Cancel")
                        }
                    },
                    modifier = Modifier.testTag("delete_confirm_dialog")
                )
            }

            // 50 MB Video Warning Dialog
            if (showVideoSizeWarningDialog && pendingVideoUri != null) {
                val fileInfo = pendingVideoFileInfo
                val sizeText = fileInfo?.sizeFormatted ?: "> 50 MB"
                AlertDialog(
                    onDismissRequest = {
                        showVideoSizeWarningDialog = false
                        pendingVideoUri = null
                        pendingVideoSongIds = emptyList()
                        pendingVideoFileInfo = null
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Large Video Warning")
                        }
                    },
                    text = {
                        Text(
                            text = "The selected video \"${fileInfo?.displayName ?: "video.mp4"}\" is $sizeText, which exceeds 50 MB.\n\nCopying large videos into private app storage keeps them working if the original is moved, but uses device storage. Would you like to proceed anyway?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showVideoSizeWarningDialog = false
                                showVideoScopeDialog = true
                            },
                            modifier = Modifier.testTag("confirm_large_video_button")
                        ) {
                            Text("Use Video")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showVideoSizeWarningDialog = false
                                pendingVideoUri = null
                                pendingVideoSongIds = emptyList()
                                pendingVideoFileInfo = null
                            },
                            modifier = Modifier.testTag("cancel_large_video_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Video Scope Dialog
            if (showVideoScopeDialog && pendingVideoUri != null) {
                val targetSong = if (pendingVideoSongIds.isNotEmpty()) {
                    uiState.allDiscoveredSongs.find { it.id == pendingVideoSongIds.first() }
                } else {
                    playerState.currentSong ?: uiState.songs.firstOrNull()
                }
                VideoScopeDialog(
                    targetSongCount = if (pendingVideoSongIds.size > 1) pendingVideoSongIds.size else 1,
                    targetSongTitle = targetSong?.title,
                    onChooseScope = { applyToAllSongs ->
                        val uri = pendingVideoUri!!
                        if (applyToAllSongs) {
                            viewModel.setGlobalBackgroundVideo(uri)
                        } else {
                            if (pendingVideoSongIds.isNotEmpty()) {
                                viewModel.setSongsBackgroundVideo(pendingVideoSongIds, uri)
                            } else if (targetSong != null) {
                                viewModel.setSongBackgroundVideo(targetSong.id, uri)
                            }
                        }
                        if (uiState.isSelectionMode) {
                            viewModel.clearSelection()
                        }
                        showVideoScopeDialog = false
                        pendingVideoUri = null
                        pendingVideoSongIds = emptyList()
                        pendingVideoFileInfo = null
                    },
                    onDismiss = {
                        showVideoScopeDialog = false
                        pendingVideoUri = null
                        pendingVideoSongIds = emptyList()
                        pendingVideoFileInfo = null
                    }
                )
            }

            // Settings Sheet
            if (showSettingsSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false },
                    sheetState = sheetState,
                    containerColor = DarkBackground
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            IconButton(
                                onClick = { showSettingsSheet = false },
                                modifier = Modifier.testTag("close_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Settings > Now Playing > Background video Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurfaceContainerHigh)
                                .padding(16.dp)
                                .testTag("now_playing_settings_card")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Now Playing",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Background video tappable setting row (opens Background video screen)
                            Surface(
                                onClick = {
                                    showBackgroundVideoScreen = true
                                    showSettingsSheet = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = DarkSurfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_background_video_row")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Background video",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        val globalSet = backgroundVideoState.globalVideoPath != null
                                        val songCount = backgroundVideoState.songVideos.size
                                        val statusDescription = when {
                                            globalSet && songCount > 0 -> "All songs video set • $songCount song video(s)"
                                            globalSet -> "All songs video set"
                                            songCount > 0 -> "$songCount song(s) have their own video"
                                            else -> "None set (tap to configure)"
                                        }
                                        Text(
                                            text = statusDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (globalSet || songCount > 0) MaterialTheme.colorScheme.primary else TextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = "Open Background video screen",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = DarkSurfaceBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Toggle: Show large album art (off by default when video is set)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Show large album art",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Show album art card over background video (off by default)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = backgroundVideoState.showLargeAlbumArtWithVideo,
                                    onCheckedChange = { checked ->
                                        viewModel.setShowLargeAlbumArtWithVideo(checked)
                                    },
                                    modifier = Modifier.testTag("toggle_show_large_album_art")
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = DarkSurfaceBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Slider: Video dimming (0 - 70%, default 20%)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Video dimming",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${(backgroundVideoState.videoDimming * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("video_dimming_label")
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Adjusts dark overlay intensity over background video (0%–70%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Slider(
                                    value = backgroundVideoState.videoDimming,
                                    onValueChange = { newVal ->
                                        viewModel.setVideoDimming(newVal)
                                    },
                                    valueRange = 0f..0.70f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("slider_video_dimming")
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = DarkSurfaceBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Toggle: Turn off when battery saver is on
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Turn off on Battery Saver",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (backgroundVideoState.isBatterySaverActive)
                                            "Battery saver active — video paused"
                                        else
                                            "Pauses background video when Battery Saver is on",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (backgroundVideoState.isBatterySaverActive && backgroundVideoState.disableOnBatterySaver)
                                            Color(0xFFF59E0B)
                                        else
                                            TextSecondary
                                    )
                                }
                                Switch(
                                    checked = backgroundVideoState.disableOnBatterySaver,
                                    onCheckedChange = { checked ->
                                        viewModel.setDisableVideoOnBatterySaver(checked)
                                    },
                                    modifier = Modifier.testTag("toggle_battery_saver_video")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Music Folders Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurfaceContainerHigh)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Music Folders",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (uiState.folders.any { it.isNew }) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                                .testTag("settings_folders_new_badge"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "NEW",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${uiState.enabledFolders.size} / ${uiState.folders.size} active",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Choose which folders songs load from. Songs from unchecked folders are excluded from all tabs, search, and the shuffle bag.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    showSettingsSheet = false
                                    showMusicFoldersSheet = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_music_folders_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Configure Music Folders")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Recently Added Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurfaceContainerHigh)
                                .padding(16.dp)
                                .testTag("recently_added_settings_card")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recently Added",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Choose which songs appear in the Recently added tab by date added. Only the last 30 days are shown by default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RecentlyAddedTimeframe.values().forEach { tf ->
                                    val isSelected = uiState.recentlyAddedTimeframe == tf
                                    if (isSelected) {
                                        Button(
                                            onClick = { viewModel.setRecentlyAddedTimeframe(tf) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("setting_timeframe_${tf.days}")
                                        ) {
                                            Text(
                                                text = tf.label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { viewModel.setRecentlyAddedTimeframe(tf) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("setting_timeframe_${tf.days}")
                                        ) {
                                            Text(
                                                text = tf.label,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Hide tabs Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurfaceContainerHigh)
                                .padding(16.dp)
                                .testTag("hide_tabs_card")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Hide tabs",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Toggle tabs to hide or show them in the top navigation bar. Songs always remains visible.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            listOf("Recently added", "Albums", "Artists", "Playlists").forEach { tabName ->
                                val isHidden = tabName in uiState.hiddenTabs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tabName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                    Switch(
                                        checked = isHidden,
                                        onCheckedChange = { checked ->
                                            viewModel.setTabHidden(tabName, checked)
                                        },
                                        modifier = Modifier.testTag("toggle_hide_$tabName")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Cover Art Resolver Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurfaceContainerHigh)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Cover Art Resolver",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${uiState.coverArtMap.size} / ${uiState.albums.size} albums",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Resolves artwork in order: embedded audio tags -> local folder images -> iTunes Search API (upscaled to 600x600).",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    viewModel.fetchMissingCovers()
                                    showSettingsSheet = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fetch_missing_covers_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetch Missing Covers")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Backup & Restore Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurfaceContainerHigh)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Backup & Restore",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                                if (uiState.unlinkedSongCount > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "${uiState.unlinkedSongCount} unlinked",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Export or import playlists, smart rules, custom covers, background videos, settings, and play counts to an offline .zip file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        exportMediaSizeBytes = viewModel.computeBackupMediaSize()
                                        showExportBackupDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("export_backup_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export")
                                }

                                OutlinedButton(
                                    onClick = {
                                        importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("import_backup_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import")
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = DarkSurfaceBorder.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Auto Backup Section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto backup",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Every 7 days, retains last 3 backups",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = autoBackupState.isEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled && autoBackupState.folderUri == null) {
                                            autoBackupFolderLauncher.launch(null)
                                        }
                                        viewModel.setAutoBackupEnabled(enabled)
                                    },
                                    modifier = Modifier.testTag("auto_backup_switch")
                                )
                            }

                            if (autoBackupState.isEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    onClick = { autoBackupFolderLauncher.launch(null) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Backup Folder",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = autoBackupState.folderDisplayName ?: "Tap to choose folder",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (autoBackupState.folderUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Last backup: ${autoBackupState.formattedLastBackup}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )

                                    TextButton(
                                        onClick = { viewModel.runAutoBackupNow() },
                                        enabled = autoBackupState.folderUri != null
                                    ) {
                                        Text("Back up now", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Library Maintenance Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurfaceContainerHigh)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Library Maintenance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            FilledTonalButton(
                                onClick = {
                                    viewModel.scanLibrary()
                                    showSettingsSheet = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rescan_library_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rescan Device Library")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            FilledTonalButton(
                                onClick = {
                                    viewModel.generateSampleTracks()
                                    showSettingsSheet = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("generate_sample_tracks_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Sample Synth Audio")
                            }
                        }
                    }
                }
            }

            // Music Folders Sheet
            if (showMusicFoldersSheet) {
                MusicFoldersSheet(
                    folders = uiState.folders,
                    onToggleFolder = { path, isChecked ->
                        viewModel.toggleFolder(path, isChecked)
                    },
                    onSelectAll = {
                        viewModel.selectAllFolders()
                    },
                    onSelectNone = {
                        viewModel.selectNoFolders()
                    },
                    onDismiss = {
                        viewModel.acknowledgeNewFolders()
                        showMusicFoldersSheet = false
                    }
                )
            }

            // Smart Playlist Dialog
            if (showSmartPlaylistDialog) {
                SmartPlaylistDialog(
                    allSongs = uiState.allDiscoveredSongs,
                    enabledFolders = uiState.enabledFolders,
                    onDismiss = { showSmartPlaylistDialog = false },
                    onCreateSmartPlaylist = { name, matchAll, rules, exclusions ->
                        viewModel.createSmartPlaylist(name, matchAll, rules, exclusions)
                        showSmartPlaylistDialog = false
                    }
                )
            }

            // Suggested Playlists Sheet
            if (showSuggestPlaylistsSheet) {
                val suggestions = remember(uiState.allDiscoveredSongs, uiState.enabledFolders, uiState.playlists) {
                    viewModel.getSuggestedPlaylists()
                }
                SuggestPlaylistsSheet(
                    suggestions = suggestions,
                    onDismiss = { showSuggestPlaylistsSheet = false },
                    onAddSuggestion = { item ->
                        viewModel.createSmartPlaylist(
                            name = item.name,
                            matchAllRules = true,
                            rules = listOf(item.rule),
                            exclusionKeywords = ""
                        )
                    }
                )
            }

            // Export Backup Dialog
            if (showExportBackupDialog) {
                ExportBackupDialog(
                    mediaSizeBytes = exportMediaSizeBytes,
                    includeMedia = exportIncludeMedia,
                    onToggleIncludeMedia = { exportIncludeMedia = it },
                    onConfirmExport = {
                        showExportBackupDialog = false
                        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
                        val filename = "AuraMusic_Backup_${sdf.format(Date())}.zip"
                        exportBackupLauncher.launch(filename)
                    },
                    onDismiss = { showExportBackupDialog = false }
                )
            }

            // Import Backup Summary Dialog
            if (showImportSummaryDialog && pendingImportSummary != null) {
                ImportSummaryDialog(
                    summary = pendingImportSummary!!,
                    onChooseMode = { mode: BackupMode ->
                        val uri = pendingImportUri
                        showImportSummaryDialog = false
                        pendingImportSummary = null
                        pendingImportUri = null
                        if (uri != null) {
                            viewModel.importBackup(uri, mode) { }
                        }
                    },
                    onDismiss = {
                        showImportSummaryDialog = false
                        pendingImportUri = null
                        pendingImportSummary = null
                    }
                )
            }

            // Background Video Screen (full screen overlay)
            AnimatedVisibility(
                visible = showBackgroundVideoScreen,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                BackgroundVideoScreen(
                    globalVideoPath = backgroundVideoState.globalVideoPath,
                    songVideos = backgroundVideoState.songVideos,
                    allSongs = uiState.allDiscoveredSongs,
                    onBackClick = { showBackgroundVideoScreen = false },
                    onChangeGlobalVideoClick = {
                        pendingVideoSongIds = emptyList()
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onRemoveGlobalVideoClick = {
                        viewModel.removeGlobalBackgroundVideo()
                    },
                    onChangeSongVideoClick = { song ->
                        pendingVideoSongIds = listOf(song.id)
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onRemoveSongVideoClick = { song ->
                        viewModel.removeSongBackgroundVideo(song.id)
                    }
                )
            }
        }
    }
}
