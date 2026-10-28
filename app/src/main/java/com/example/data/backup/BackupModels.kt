package com.example.data.backup

import com.example.data.local.RecentlyAddedTimeframe
import com.example.model.SmartPlaylistRule
import com.example.model.SmartRuleType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BackupMode {
    MERGE,
    REPLACE
}

data class BackupSongRef(
    val idInBackup: Int,
    val relativePath: String,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val playCount: Int = 0,
    val videoPathInZip: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("idInBackup", idInBackup)
        put("relativePath", relativePath)
        put("fileName", fileName)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("durationMs", durationMs)
        put("playCount", playCount)
        if (videoPathInZip != null) put("videoPathInZip", videoPathInZip)
    }

    companion object {
        fun fromJson(obj: JSONObject): BackupSongRef {
            return BackupSongRef(
                idInBackup = obj.optInt("idInBackup", 0),
                relativePath = obj.optString("relativePath", ""),
                fileName = obj.optString("fileName", ""),
                title = obj.optString("title", ""),
                artist = obj.optString("artist", ""),
                album = obj.optString("album", ""),
                durationMs = obj.optLong("durationMs", 0L),
                playCount = obj.optInt("playCount", 0),
                videoPathInZip = if (obj.has("videoPathInZip") && !obj.isNull("videoPathInZip")) {
                    obj.optString("videoPathInZip")
                } else null
            )
        }
    }
}

data class BackupPlaylist(
    val id: Long,
    val name: String,
    val songRefs: List<Int>,
    val isSmart: Boolean,
    val matchAllRules: Boolean,
    val rules: List<SmartPlaylistRule>,
    val exclusionKeywords: String,
    val customCoverPathInZip: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("songRefs", JSONArray(songRefs))
        put("isSmart", isSmart)
        put("matchAllRules", matchAllRules)
        put("exclusionKeywords", exclusionKeywords)
        if (customCoverPathInZip != null) put("customCoverPathInZip", customCoverPathInZip)

        val rArr = JSONArray()
        for (r in rules) {
            val rObj = JSONObject()
            rObj.put("type", r.type.name)
            rObj.put("value", r.value)
            rArr.put(rObj)
        }
        put("rules", rArr)
    }

    companion object {
        fun fromJson(obj: JSONObject): BackupPlaylist {
            val songRefsArr = obj.optJSONArray("songRefs")
            val songRefs = mutableListOf<Int>()
            if (songRefsArr != null) {
                for (i in 0 until songRefsArr.length()) {
                    songRefs.add(songRefsArr.getInt(i))
                }
            }

            val rulesList = mutableListOf<SmartPlaylistRule>()
            val rulesArr = obj.optJSONArray("rules")
            if (rulesArr != null) {
                for (i in 0 until rulesArr.length()) {
                    val rObj = rulesArr.getJSONObject(i)
                    val typeStr = rObj.optString("type", "")
                    val ruleType = try { SmartRuleType.valueOf(typeStr) } catch (e: Exception) { null }
                    if (ruleType != null) {
                        rulesList.add(SmartPlaylistRule(type = ruleType, value = rObj.optString("value", "")))
                    }
                }
            }

            return BackupPlaylist(
                id = obj.optLong("id", 0L),
                name = obj.optString("name", "Playlist"),
                songRefs = songRefs,
                isSmart = obj.optBoolean("isSmart", false),
                matchAllRules = obj.optBoolean("matchAllRules", true),
                rules = rulesList,
                exclusionKeywords = obj.optString("exclusionKeywords", ""),
                customCoverPathInZip = if (obj.has("customCoverPathInZip") && !obj.isNull("customCoverPathInZip")) {
                    obj.optString("customCoverPathInZip")
                } else null
            )
        }
    }
}

data class BackupCoverRef(
    val albumKey: String,
    val songRefId: Int? = null,
    val imagePathInZip: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("albumKey", albumKey)
        if (songRefId != null) put("songRefId", songRefId)
        put("imagePathInZip", imagePathInZip)
    }

    companion object {
        fun fromJson(obj: JSONObject): BackupCoverRef {
            return BackupCoverRef(
                albumKey = obj.optString("albumKey", ""),
                songRefId = if (obj.has("songRefId") && !obj.isNull("songRefId")) obj.optInt("songRefId") else null,
                imagePathInZip = obj.optString("imagePathInZip", "")
            )
        }
    }
}

data class BackupSettings(
    val hiddenTabs: Set<String> = emptySet(),
    val enabledFolders: Set<String> = emptySet(),
    val knownFolders: Set<String> = emptySet(),
    val recentlyAddedDays: Int = RecentlyAddedTimeframe.LAST_30_DAYS.days,
    val disableOnBatterySaver: Boolean = true,
    val showLargeAlbumArtWithVideo: Boolean = false,
    val videoDimming: Float = 0.20f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hiddenTabs", JSONArray(hiddenTabs))
        put("enabledFolders", JSONArray(enabledFolders))
        put("knownFolders", JSONArray(knownFolders))
        put("recentlyAddedDays", recentlyAddedDays)
        put("disableOnBatterySaver", disableOnBatterySaver)
        put("showLargeAlbumArtWithVideo", showLargeAlbumArtWithVideo)
        put("videoDimming", videoDimming.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject?): BackupSettings {
            if (obj == null) return BackupSettings()

            val hiddenTabs = mutableSetOf<String>()
            obj.optJSONArray("hiddenTabs")?.let { arr ->
                for (i in 0 until arr.length()) hiddenTabs.add(arr.getString(i))
            }

            val enabledFolders = mutableSetOf<String>()
            obj.optJSONArray("enabledFolders")?.let { arr ->
                for (i in 0 until arr.length()) enabledFolders.add(arr.getString(i))
            }

            val knownFolders = mutableSetOf<String>()
            obj.optJSONArray("knownFolders")?.let { arr ->
                for (i in 0 until arr.length()) knownFolders.add(arr.getString(i))
            }

            return BackupSettings(
                hiddenTabs = hiddenTabs,
                enabledFolders = enabledFolders,
                knownFolders = knownFolders,
                recentlyAddedDays = obj.optInt("recentlyAddedDays", RecentlyAddedTimeframe.LAST_30_DAYS.days),
                disableOnBatterySaver = obj.optBoolean("disableOnBatterySaver", true),
                showLargeAlbumArtWithVideo = obj.optBoolean("showLargeAlbumArtWithVideo", false),
                videoDimming = obj.optDouble("videoDimming", 0.20).toFloat()
            )
        }
    }
}

data class BackupManifest(
    val version: Int = CURRENT_VERSION,
    val appVersion: String = "1.0",
    val createdAt: Long = System.currentTimeMillis(),
    val includesMedia: Boolean = true,
    val settings: BackupSettings = BackupSettings(),
    val songs: List<BackupSongRef> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val pickedCovers: List<BackupCoverRef> = emptyList(),
    val globalVideoPathInZip: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("appVersion", appVersion)
        put("createdAt", createdAt)
        put("includesMedia", includesMedia)
        put("settings", settings.toJson())

        val sArr = JSONArray()
        songs.forEach { sArr.put(it.toJson()) }
        put("songs", sArr)

        val pArr = JSONArray()
        playlists.forEach { pArr.put(it.toJson()) }
        put("playlists", pArr)

        val cArr = JSONArray()
        pickedCovers.forEach { cArr.put(it.toJson()) }
        put("pickedCovers", cArr)

        if (globalVideoPathInZip != null) {
            put("globalVideoPathInZip", globalVideoPathInZip)
        }
    }

    companion object {
        const val CURRENT_VERSION = 1

        fun fromJson(obj: JSONObject): BackupManifest {
            val songsList = mutableListOf<BackupSongRef>()
            obj.optJSONArray("songs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    songsList.add(BackupSongRef.fromJson(arr.getJSONObject(i)))
                }
            }

            val playlistsList = mutableListOf<BackupPlaylist>()
            obj.optJSONArray("playlists")?.let { arr ->
                for (i in 0 until arr.length()) {
                    playlistsList.add(BackupPlaylist.fromJson(arr.getJSONObject(i)))
                }
            }

            val coversList = mutableListOf<BackupCoverRef>()
            obj.optJSONArray("pickedCovers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    coversList.add(BackupCoverRef.fromJson(arr.getJSONObject(i)))
                }
            }

            val globalVideo = if (obj.has("globalVideoPathInZip") && !obj.isNull("globalVideoPathInZip")) {
                obj.optString("globalVideoPathInZip")
            } else null

            return BackupManifest(
                version = obj.optInt("version", CURRENT_VERSION),
                appVersion = obj.optString("appVersion", "1.0"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                includesMedia = obj.optBoolean("includesMedia", true),
                settings = BackupSettings.fromJson(obj.optJSONObject("settings")),
                songs = songsList,
                playlists = playlistsList,
                pickedCovers = coversList,
                globalVideoPathInZip = globalVideo
            )
        }
    }
}

data class BackupSummary(
    val version: Int,
    val createdAt: Long,
    val includesMedia: Boolean,
    val playlistCount: Int,
    val totalSongRefCount: Int,
    val matchedSongCount: Int,
    val unlinkedSongCount: Int,
    val pickedCoverCount: Int,
    val hasGlobalVideo: Boolean,
    val songVideoCount: Int,
    val manifest: BackupManifest
) {
    val formattedDate: String
        get() = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(createdAt))
}
