package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class CoverArtRecord(
    val albumKey: String,
    val artworkPath: String?,
    val status: String, // "SUCCESS" or "FAILED"
    val timestamp: Long
)

class PlaylistDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    private val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val playlistSongsMap = mutableMapOf<Long, MutableStateFlow<List<Long>>>()
    private val coverArtFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        try {
            readableDatabase.setForeignKeyConstraintsEnabled(true)
            refreshPlaylistsSync()
            refreshCoverArtSync()
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error initializing playlist database", e)
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_items (
                playlist_id INTEGER NOT NULL,
                song_id INTEGER NOT NULL,
                order_index INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (playlist_id, song_id),
                FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cover_art (
                album_key TEXT PRIMARY KEY,
                artwork_path TEXT,
                status TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cover_art (
                    album_key TEXT PRIMARY KEY,
                    artwork_path TEXT,
                    status TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureSchema(db)
    }

    private fun ensureSchema(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cover_art (
                    album_key TEXT PRIMARY KEY,
                    artwork_path TEXT,
                    status TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error verifying database schema", e)
        }
    }

    private fun refreshPlaylistsSync() {
        val list = mutableListOf<Playlist>()
        try {
            val query = """
                SELECT p.id, p.name, p.created_at, COUNT(pi.song_id) as song_count
                FROM playlists p
                LEFT JOIN playlist_items pi ON p.id = pi.playlist_id
                GROUP BY p.id
                ORDER BY p.created_at DESC
            """.trimIndent()

            readableDatabase.rawQuery(query, null).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow("id")
                val nameCol = cursor.getColumnIndexOrThrow("name")
                val createdCol = cursor.getColumnIndexOrThrow("created_at")
                val countCol = cursor.getColumnIndexOrThrow("song_count")

                while (cursor.moveToNext()) {
                    list.add(
                        Playlist(
                            id = cursor.getLong(idCol),
                            name = cursor.getString(nameCol),
                            songCount = cursor.getInt(countCol),
                            createdAt = cursor.getLong(createdCol)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error querying playlists", e)
        }
        playlistsFlow.value = list
    }

    private fun refreshPlaylistSongsSync(playlistId: Long) {
        val songIds = mutableListOf<Long>()
        try {
            val query = "SELECT song_id FROM playlist_items WHERE playlist_id = ? ORDER BY order_index ASC"
            readableDatabase.rawQuery(query, arrayOf(playlistId.toString())).use { cursor ->
                val songIdCol = cursor.getColumnIndexOrThrow("song_id")
                while (cursor.moveToNext()) {
                    songIds.add(cursor.getLong(songIdCol))
                }
            }
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error querying playlist songs", e)
        }
        val flow = playlistSongsMap.getOrPut(playlistId) { MutableStateFlow(emptyList()) }
        flow.value = songIds
    }

    private fun refreshCoverArtSync() {
        val map = mutableMapOf<String, String>()
        try {
            val query = "SELECT album_key, artwork_path FROM cover_art WHERE status = 'SUCCESS' AND artwork_path IS NOT NULL"
            readableDatabase.rawQuery(query, null).use { cursor ->
                val keyCol = cursor.getColumnIndexOrThrow("album_key")
                val pathCol = cursor.getColumnIndexOrThrow("artwork_path")
                while (cursor.moveToNext()) {
                    val key = cursor.getString(keyCol)
                    val path = cursor.getString(pathCol)
                    if (key != null && path != null) {
                        map[key] = path
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error querying cover art", e)
        }
        coverArtFlow.value = map
    }

    fun getAllPlaylistsFlow(): Flow<List<Playlist>> = playlistsFlow.asStateFlow()

    fun getSongIdsForPlaylistFlow(playlistId: Long): Flow<List<Long>> {
        val flow = playlistSongsMap.getOrPut(playlistId) { MutableStateFlow(emptyList()) }
        refreshPlaylistSongsSync(playlistId)
        return flow.asStateFlow()
    }

    fun getCoverArtFlow(): Flow<Map<String, String>> = coverArtFlow.asStateFlow()

    fun getCoverArtRecord(albumKey: String): CoverArtRecord? {
        try {
            val query = "SELECT album_key, artwork_path, status, timestamp FROM cover_art WHERE album_key = ?"
            readableDatabase.rawQuery(query, arrayOf(albumKey)).use { cursor ->
                if (cursor.moveToFirst()) {
                    return CoverArtRecord(
                        albumKey = cursor.getString(cursor.getColumnIndexOrThrow("album_key")),
                        artworkPath = cursor.getString(cursor.getColumnIndexOrThrow("artwork_path")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error fetching cover art record", e)
        }
        return null
    }

    fun saveCoverArtRecord(albumKey: String, artworkPath: String?, status: String) {
        try {
            val values = ContentValues().apply {
                put("album_key", albumKey)
                put("artwork_path", artworkPath)
                put("status", status)
                put("timestamp", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict(
                "cover_art",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            refreshCoverArtSync()
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error saving cover art record", e)
        }
    }

    fun getCoverArtStats(): Pair<Int, Int> {
        var successCount = 0
        var failedCount = 0
        try {
            val query = "SELECT status, COUNT(*) as cnt FROM cover_art GROUP BY status"
            readableDatabase.rawQuery(query, null).use { cursor ->
                val statusCol = cursor.getColumnIndexOrThrow("status")
                val countCol = cursor.getColumnIndexOrThrow("cnt")
                while (cursor.moveToNext()) {
                    val status = cursor.getString(statusCol)
                    val cnt = cursor.getInt(countCol)
                    if (status == "SUCCESS") successCount = cnt
                    else if (status == "FAILED") failedCount = cnt
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(successCount, failedCount)
    }

    suspend fun insertPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put("name", name)
                put("created_at", System.currentTimeMillis())
            }
            val id = writableDatabase.insert("playlists", null, values)
            refreshPlaylistsSync()
            id
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error inserting playlist", e)
            -1L
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        try {
            writableDatabase.delete("playlists", "id = ?", arrayOf(playlistId.toString()))
            refreshPlaylistsSync()
            playlistSongsMap[playlistId]?.value = emptyList()
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error deleting playlist", e)
        }
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put("playlist_id", playlistId)
                put("song_id", songId)
                put("order_index", System.currentTimeMillis().toInt())
            }
            writableDatabase.insertWithOnConflict(
                "playlist_items",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
            )
            refreshPlaylistsSync()
            refreshPlaylistSongsSync(playlistId)
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error adding song to playlist", e)
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        try {
            writableDatabase.delete(
                "playlist_items",
                "playlist_id = ? AND song_id = ?",
                arrayOf(playlistId.toString(), songId.toString())
            )
            refreshPlaylistsSync()
            refreshPlaylistSongsSync(playlistId)
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error removing song from playlist", e)
        }
    }

    suspend fun removeSongsFromAllPlaylists(songIds: Set<Long>) = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) return@withContext
        try {
            val placeholders = songIds.joinToString(",") { "?" }
            val args = songIds.map { it.toString() }.toTypedArray()
            writableDatabase.delete("playlist_items", "song_id IN ($placeholders)", args)
            refreshPlaylistsSync()
            playlistSongsMap.keys.forEach { pId ->
                refreshPlaylistSongsSync(pId)
            }
        } catch (e: Exception) {
            Log.e("PlaylistDbHelper", "Error removing songs from all playlists", e)
        }
    }

    companion object {
        private const val DATABASE_NAME = "aura_music.db"
        private const val DATABASE_VERSION = 3

        @Volatile
        private var instance: PlaylistDatabaseHelper? = null

        fun getInstance(context: Context): PlaylistDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: PlaylistDatabaseHelper(context).also { instance = it }
            }
        }
    }
}
