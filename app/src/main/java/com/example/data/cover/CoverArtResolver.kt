package com.example.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CoverArtResolver(private val context: Context) {

    private val pickedDir = File(context.filesDir, "picked_cover_art").apply { mkdirs() }
    private val embeddedDir = File(context.filesDir, "embedded_cover_art").apply { mkdirs() }
    private val onlineDir = File(context.filesDir, "online_cover_art").apply { mkdirs() }
    private val legacyDir = File(context.filesDir, "cover_art").apply { mkdirs() }

    private val noEmbeddedArtPrefs = context.getSharedPreferences("aura_music_no_embedded_art", Context.MODE_PRIVATE)
    private val noEmbeddedArtSongIds = ConcurrentHashMap.newKeySet<Long>().apply {
        val saved = noEmbeddedArtPrefs.getStringSet("ids", emptySet()) ?: emptySet()
        for (s in saved) {
            s.toLongOrNull()?.let { add(it) }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val memoryCache = ConcurrentHashMap<String, String>()
    private val _coverArtMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val coverArtMap: StateFlow<Map<String, String>> = _coverArtMap.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var backgroundResolveJob: Job? = null

    private fun markNoEmbeddedArt(songId: Long) {
        if (noEmbeddedArtSongIds.add(songId)) {
            scope.launch(Dispatchers.IO) {
                val set = noEmbeddedArtSongIds.map { it.toString() }.toSet()
                noEmbeddedArtPrefs.edit().putStringSet("ids", set).apply()
            }
        }
    }

    companion object {
        private const val TAG = "CoverArtResolver"

        @Volatile
        private var INSTANCE: CoverArtResolver? = null

        fun getInstance(context: Context): CoverArtResolver {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CoverArtResolver(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getAlbumKey(album: String, artist: String): String {
            return "${album.trim().lowercase()}:::${artist.trim().lowercase()}"
        }

        fun getSongKey(songId: Long): String {
            return "song_$songId"
        }
    }

    init {
        loadExistingCaches()
    }

    fun refreshCustomArt() {
        memoryCache.clear()
        loadExistingCaches()
    }

    private fun loadExistingCaches() {
        // Priority 4 (legacy/online) first, then Priority 2 (embedded), then Priority 1 (picked) overrides all
        val map = mutableMapOf<String, String>()

        fun scanDir(dir: File, isSongId: Boolean = false) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isFile && file.length() > 0 && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")) {
                    val baseName = file.nameWithoutExtension
                    if (isSongId || baseName.startsWith("song_")) {
                        map[baseName] = file.absolutePath
                        memoryCache[baseName] = file.absolutePath
                    } else {
                        val key = baseName.replace("___", ":::")
                        map[key] = file.absolutePath
                        memoryCache[key] = file.absolutePath
                    }
                }
            }
        }

        // 4. Online & legacy cache
        scanDir(legacyDir)
        scanDir(onlineDir)

        // 2. Embedded cache
        scanDir(embeddedDir, isSongId = true)

        // 1. Picked art (highest priority, overwrites map keys)
        scanDir(pickedDir)

        _coverArtMap.value = map
    }

    /**
     * The ONE shared artwork resolution function.
     * Priority:
     * 1. Art I picked in Fix Cover Art
     * 2. Embedded art in the file
     * 3. Image in the song's folder
     * 4. Cached online art
     * 5. Placeholder (null)
     *
     * A cached "lookup failed" must never override embedded or picked art.
     */
    fun getArtwork(song: Song): String? {
        val albumKey = getAlbumKey(song.album, song.artist)
        val songKey = getSongKey(song.id)

        // Check if already in memory cache with valid file
        val cached = memoryCache[songKey] ?: memoryCache[albumKey]
        if (cached != null && File(cached).exists() && File(cached).length() > 0) {
            // But verify if a higher priority picked art exists
            val picked = getPickedArt(song.id, albumKey)
            if (picked != null) {
                updateMap(songKey, picked)
                updateMap(albumKey, picked)
                return picked
            }
            return cached
        }

        // 1. Art I picked in Fix Cover Art
        val pickedPath = getPickedArt(song.id, albumKey)
        if (pickedPath != null) {
            updateMap(songKey, pickedPath)
            updateMap(albumKey, pickedPath)
            return pickedPath
        }

        // 2. Embedded art in the file
        val embeddedPath = getOrExtractEmbeddedArt(song)
        if (embeddedPath != null) {
            updateMap(songKey, embeddedPath)
            updateMap(albumKey, embeddedPath)
            return embeddedPath
        }

        // 3. Image in the song's folder
        val folderPath = getFolderArt(song)
        if (folderPath != null) {
            updateMap(songKey, folderPath)
            updateMap(albumKey, folderPath)
            return folderPath
        }

        // 4. Cached online art
        val onlinePath = getCachedOnlineArt(albumKey)
        if (onlinePath != null) {
            updateMap(songKey, onlinePath)
            updateMap(albumKey, onlinePath)
            return onlinePath
        }

        // 5. Placeholder
        return null
    }

    /**
     * Shared function for Album/Artist views.
     */
    fun getArtwork(album: String, artist: String): String? {
        val albumKey = getAlbumKey(album, artist)

        // 1. Picked art
        val pickedFile = File(pickedDir, "${albumKey.replace(":::", "___")}.jpg")
        if (pickedFile.exists() && pickedFile.length() > 0) return pickedFile.absolutePath

        // In-memory or cached map
        val existing = memoryCache[albumKey]
        if (existing != null && File(existing).exists() && File(existing).length() > 0) {
            return existing
        }

        // 4. Cached online art
        val onlinePath = getCachedOnlineArt(albumKey)
        if (onlinePath != null) return onlinePath

        return null
    }

    /**
     * Fast cache-only lookup for queue building:
     * Memory cache and already-cached files only: NO MediaMetadataRetriever and NO directory scanning.
     */
    fun getArtworkCacheOnly(song: Song): String? {
        val albumKey = getAlbumKey(song.album, song.artist)
        val songKey = getSongKey(song.id)

        // 1. In-memory cache check
        val cached = memoryCache[songKey] ?: memoryCache[albumKey]
        if (cached != null) {
            val f = File(cached)
            if (f.exists() && f.length() > 0) {
                val picked = getPickedArt(song.id, albumKey)
                if (picked != null) return picked
                return cached
            }
        }

        // 2. Picked art (direct file existence check without folder listing)
        val picked = getPickedArt(song.id, albumKey)
        if (picked != null) {
            updateMap(songKey, picked)
            updateMap(albumKey, picked)
            return picked
        }

        // 3. Pre-existing cached embedded file
        val embeddedFile = File(embeddedDir, "song_${song.id}.jpg")
        if (embeddedFile.exists() && embeddedFile.length() > 0) {
            updateMap(songKey, embeddedFile.absolutePath)
            return embeddedFile.absolutePath
        }

        // 4. Pre-existing cached online file
        val onlinePath = getCachedOnlineArt(albumKey)
        if (onlinePath != null) {
            updateMap(albumKey, onlinePath)
            return onlinePath
        }

        return null
    }

    // 1. Picked art lookup
    private fun getPickedArt(songId: Long, albumKey: String): String? {
        val songSpecific = File(pickedDir, "song_${songId}.jpg")
        if (songSpecific.exists() && songSpecific.length() > 0) {
            return songSpecific.absolutePath
        }

        val albumFile = File(pickedDir, "${albumKey.replace(":::", "___")}.jpg")
        if (albumFile.exists() && albumFile.length() > 0) {
            return albumFile.absolutePath
        }

        return null
    }

    // 2. Embedded art extraction & disk caching
    private fun getOrExtractEmbeddedArt(song: Song): String? {
        if (noEmbeddedArtSongIds.contains(song.id)) {
            return null
        }

        val cached = File(embeddedDir, "song_${song.id}.jpg")
        if (cached.exists() && cached.length() > 0) {
            return cached.absolutePath
        }

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, song.contentUri)
            val pictureBytes = retriever.embeddedPicture
            retriever.release()
            if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                FileOutputStream(cached).use { out ->
                    out.write(pictureBytes)
                }
                return cached.absolutePath
            } else {
                markNoEmbeddedArt(song.id)
            }
        } catch (e: Exception) {
            // No embedded art in file or unreadable
            markNoEmbeddedArt(song.id)
        }

        return null
    }

    // 3. Image in the song's folder
    private fun getFolderArt(song: Song): String? {
        val folderDir: File? = when {
            song.dataPath != null -> File(song.dataPath).parentFile
            song.contentUri.scheme == "file" -> song.contentUri.path?.let { File(it).parentFile }
            else -> null
        }

        if (folderDir != null && folderDir.exists() && folderDir.isDirectory) {
            val standardNames = listOf(
                "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
                "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
                "album.jpg", "album.jpeg", "album.png", "album.webp",
                "artwork.jpg", "artwork.png", "front.jpg", "front.png"
            )
            for (name in standardNames) {
                val f = File(folderDir, name)
                if (f.exists() && f.isFile && f.length() > 0) {
                    return f.absolutePath
                }
            }

            // Fallback: any image in the directory
            val anyImage = folderDir.listFiles { f ->
                f.isFile && f.length() > 0 && f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
            }?.firstOrNull()
            if (anyImage != null) {
                return anyImage.absolutePath
            }
        }

        return null
    }

    // 4. Cached online art lookup
    private fun getCachedOnlineArt(albumKey: String): String? {
        val safeKey = albumKey.replace(":::", "___")
        val onlineFile = File(onlineDir, "$safeKey.jpg")
        if (onlineFile.exists() && onlineFile.length() > 0) {
            return onlineFile.absolutePath
        }
        val legacyFile = File(legacyDir, "$safeKey.jpg")
        if (legacyFile.exists() && legacyFile.length() > 0) {
            return legacyFile.absolutePath
        }
        return null
    }

    /**
     * Resolves artwork for the library in the background on Dispatchers.IO, in small batches,
     * updating the UI as results arrive.
     */
    fun resolveLibraryCoversInBackground(songs: List<Song>, batchSize: Int = 10) {
        backgroundResolveJob?.cancel()
        backgroundResolveJob = scope.launch(Dispatchers.IO) {
            val chunks = songs.chunked(batchSize)
            for (chunk in chunks) {
                for (song in chunk) {
                    val songKey = getSongKey(song.id)
                    val albumKey = getAlbumKey(song.album, song.artist)
                    if (memoryCache.containsKey(songKey) || memoryCache.containsKey(albumKey)) {
                        continue
                    }
                    getArtwork(song)
                }
                delay(50)
            }
        }
    }

    /**
     * Resolves and pre-caches artwork for a list of songs asynchronously.
     */
    fun precacheArtwork(songs: List<Song>) {
        resolveLibraryCoversInBackground(songs)
    }

    suspend fun resolveCoverForSong(song: Song): String? = withContext(Dispatchers.IO) {
        val resolved = getArtwork(song)
        if (resolved != null) {
            return@withContext resolved
        }

        // If no picked, embedded, folder, or cached online art, try online search
        val onlinePath = searchOnlineArtwork(song.album, song.artist, song.title)
        if (onlinePath != null) {
            updateMap(getSongKey(song.id), onlinePath)
            updateMap(getAlbumKey(song.album, song.artist), onlinePath)
            return@withContext onlinePath
        }

        null
    }

    suspend fun searchOnlineArtwork(album: String, artist: String, trackTitle: String? = null): String? = withContext(Dispatchers.IO) {
        val cleaned = CoverArtTagCleaner.clean(trackTitle ?: album, artist)
        val query = if (album.isNotBlank() && !album.equals("Unknown Album", ignoreCase = true)) {
            "${cleaned.artist} ${album}"
        } else {
            "${cleaned.artist} ${cleaned.title}"
        }

        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            return@withContext null
        }

        val url = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=5"
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@withContext null

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val itunesArtist = item.optString("artistName", "")
                val itunesTitle = item.optString("trackName", "")
                val artworkUrl100 = item.optString("artworkUrl100", "")

                if (artworkUrl100.isNotBlank()) {
                    val closeMatch = FuzzyMatcher.isCloseMatch(
                        targetArtist = cleaned.artist,
                        targetTitle = cleaned.title,
                        candidateArtist = itunesArtist,
                        candidateTitle = itunesTitle
                    )
                    if (closeMatch || results.length() == 1) {
                        val highResUrl = artworkUrl100.replace("100x100bb.jpg", "600x600bb.jpg")
                        val albumKey = getAlbumKey(album, artist)
                        val downloaded = downloadAndSaveOnlineImage(albumKey, highResUrl)
                        if (downloaded != null) {
                            return@withContext downloaded
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online artwork fetch failed: ${e.message}")
        }

        null
    }

    /**
     * Saves art picked by the user in Fix Cover Art.
     * Highest priority (Priority 1) - immediately updates all screens.
     */
    fun saveCustomArtwork(album: String, artist: String, imageUri: Uri, song: Song? = null): String? {
        val albumKey = getAlbumKey(album, artist)
        try {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return null

                // Save to album-level picked art
                val albumFile = File(pickedDir, "${albumKey.replace(":::", "___")}.jpg")
                FileOutputStream(albumFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }

                // If song is provided, also save song-specific picked art
                if (song != null) {
                    val songFile = File(pickedDir, "song_${song.id}.jpg")
                    FileOutputStream(songFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    updateMap(getSongKey(song.id), songFile.absolutePath)
                }

                updateMap(albumKey, albumFile.absolutePath)
                return albumFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom picked artwork", e)
        }
        return null
    }

    private fun downloadAndSaveOnlineImage(albumKey: String, imageUrl: String): String? {
        try {
            val req = Request.Builder().url(imageUrl).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val bytes = resp.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val file = File(onlineDir, "${albumKey.replace(":::", "___")}.jpg")
                    FileOutputStream(file).use { out ->
                        out.write(bytes)
                    }
                    updateMap(albumKey, file.absolutePath)
                    return file.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading online artwork $imageUrl", e)
        }
        return null
    }

    private fun updateMap(key: String, path: String) {
        memoryCache[key] = path
        val map = _coverArtMap.value.toMutableMap()
        map[key] = path
        _coverArtMap.value = map
    }
}
