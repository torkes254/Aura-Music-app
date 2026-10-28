package com.example.model

enum class SmartRuleType(val label: String) {
    TITLE_CONTAINS("Title contains"),
    ARTIST_CONTAINS("Artist contains"),
    ARTIST_EQUALS("Artist equals"),
    ALBUM_CONTAINS("Album contains"),
    FOLDER_CONTAINS("Folder contains"),
    GENRE_CONTAINS("Genre contains"),
    ADDED_WITHIN_DAYS("Added within last N days"),
    MOST_PLAYED("Most played (top songs)")
}

data class SmartPlaylistRule(
    val type: SmartRuleType,
    val value: String = ""
)

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val songCount: Int = songIds.size,
    val customCoverPath: String? = null,
    val isSmart: Boolean = false,
    val matchAllRules: Boolean = true,
    val rules: List<SmartPlaylistRule> = emptyList(),
    val exclusionKeywords: String = ""
)
