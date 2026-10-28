package com.example.data.local

import com.example.model.Playlist
import com.example.model.SmartPlaylistRule
import com.example.model.SmartRuleType
import com.example.model.Song

data class SuggestedPlaylist(
    val name: String,
    val rule: SmartPlaylistRule,
    val matchCount: Int
)

object SmartPlaylistEvaluator {

    /**
     * Normalizes text by lowercasing and replacing punctuation with spaces.
     */
    fun normalize(text: String?): String {
        if (text == null) return ""
        return text.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]+"), " ").trim()
    }

    private fun parseKeywords(rawKeywords: String): List<String> {
        return rawKeywords.split(",")
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
    }

    private fun containsAnyKeyword(text: String?, rawKeywords: String): Boolean {
        val normText = normalize(text)
        if (normText.isEmpty()) return false
        val keywords = parseKeywords(rawKeywords)
        if (keywords.isEmpty()) return true
        val normNoSpaces = normText.replace(" ", "")
        return keywords.any { kw ->
            val kwNoSpaces = kw.replace(" ", "")
            normText.contains(kw) || (normNoSpaces.isNotEmpty() && kwNoSpaces.isNotEmpty() && normNoSpaces.contains(kwNoSpaces))
        }
    }

    private fun equalsAnyKeyword(text: String?, rawKeywords: String): Boolean {
        val normText = normalize(text)
        val keywords = parseKeywords(rawKeywords)
        if (keywords.isEmpty()) return true
        val normNoSpaces = normText.replace(" ", "")
        return keywords.any { kw ->
            val kwNoSpaces = kw.replace(" ", "")
            normText == kw || (normNoSpaces.isNotEmpty() && kwNoSpaces.isNotEmpty() && normNoSpaces == kwNoSpaces)
        }
    }

    fun matchesRule(song: Song, rule: SmartPlaylistRule): Boolean {
        return when (rule.type) {
            SmartRuleType.TITLE_CONTAINS -> containsAnyKeyword(song.title, rule.value)
            SmartRuleType.ARTIST_CONTAINS -> containsAnyKeyword(song.artist, rule.value)
            SmartRuleType.ARTIST_EQUALS -> equalsAnyKeyword(song.artist, rule.value)
            SmartRuleType.ALBUM_CONTAINS -> containsAnyKeyword(song.album, rule.value)
            SmartRuleType.FOLDER_CONTAINS -> containsAnyKeyword(song.folder, rule.value)
            SmartRuleType.GENRE_CONTAINS -> song.genre != null && containsAnyKeyword(song.genre, rule.value)
            SmartRuleType.ADDED_WITHIN_DAYS -> {
                val days = rule.value.trim().toIntOrNull() ?: 30
                val cutoffSec = (System.currentTimeMillis() / 1000L) - (days * 86400L)
                song.dateAdded >= cutoffSec
            }
            SmartRuleType.MOST_PLAYED -> {
                // Evaluated as part of ranking, or at least 1 play count
                song.playCount > 0
            }
        }
    }

    /**
     * Evaluates a Smart Playlist against the candidate songs, respecting enabled folders
     * and exclusion keywords.
     */
    fun evaluate(
        playlist: Playlist,
        allSongs: List<Song>,
        enabledFolders: Set<String>? = null
    ): List<Song> {
        if (!playlist.isSmart) {
            val songIdSet = playlist.songIds.toSet()
            return allSongs.filter { it.id in songIdSet }
        }

        // Respect enabled music folders setting
        val folderFiltered = if (enabledFolders != null) {
            allSongs.filter { it.folder in enabledFolders }
        } else {
            allSongs
        }

        val exclusionKeywords = parseKeywords(playlist.exclusionKeywords)

        // Filter out songs matching exclusion keywords
        val nonExcluded = folderFiltered.filter { song ->
            if (exclusionKeywords.isEmpty()) true
            else {
                val normTitle = normalize(song.title)
                val normArtist = normalize(song.artist)
                val normAlbum = normalize(song.album)
                val isExcluded = exclusionKeywords.any { kw ->
                    val kwNoSpaces = kw.replace(" ", "")
                    normTitle.contains(kw) || normArtist.contains(kw) || normAlbum.contains(kw) ||
                            (kwNoSpaces.isNotEmpty() && (normTitle.replace(" ", "").contains(kwNoSpaces) ||
                                    normArtist.replace(" ", "").contains(kwNoSpaces) ||
                                    normAlbum.replace(" ", "").contains(kwNoSpaces)))
                }
                !isExcluded
            }
        }

        val rules = playlist.rules.filter { it.value.isNotBlank() || it.type == SmartRuleType.MOST_PLAYED }
        if (rules.isEmpty()) {
            return nonExcluded
        }

        val hasMostPlayedRule = rules.any { it.type == SmartRuleType.MOST_PLAYED }
        val nonMostPlayedRules = rules.filter { it.type != SmartRuleType.MOST_PLAYED }

        var matched = nonExcluded.filter { song ->
            if (nonMostPlayedRules.isEmpty()) true
            else if (playlist.matchAllRules) {
                nonMostPlayedRules.all { rule -> matchesRule(song, rule) }
            } else {
                nonMostPlayedRules.any { rule -> matchesRule(song, rule) }
            }
        }

        if (hasMostPlayedRule) {
            val mostPlayedRule = rules.first { it.type == SmartRuleType.MOST_PLAYED }
            val limit = mostPlayedRule.value.trim().toIntOrNull() ?: 25
            matched = matched.sortedWith(
                compareByDescending<Song> { it.playCount }
                    .thenByDescending { it.dateAdded }
            ).take(limit)
        }

        return matched
    }

    /**
     * Scans titles, artists, and folders for common patterns and returns smart playlist suggestions.
     */
    fun suggestPlaylists(
        allSongs: List<Song>,
        enabledFolders: Set<String>? = null,
        existingPlaylistNames: Set<String> = emptySet()
    ): List<SuggestedPlaylist> {
        val folderFiltered = if (enabledFolders != null) {
            allSongs.filter { it.folder in enabledFolders }
        } else {
            allSongs
        }

        val suggestions = mutableListOf<SuggestedPlaylist>()
        val existingNormalized = existingPlaylistNames.map { normalize(it) }.toSet()

        // 1. Common genre/music tag keywords
        val candidateTags = mapOf(
            "Nightcore" to "nightcore, night core",
            "Remix" to "remix",
            "Acoustic" to "acoustic",
            "Live" to "live",
            "Instrumental" to "instrumental",
            "Lo-Fi" to "lo-fi, lofi, lo fi",
            "Synthwave" to "synthwave, synth wave",
            "Chiptune" to "chiptune, 8-bit, 8bit",
            "Sped Up" to "sped up, speed up",
            "Slowed" to "slowed, slow",
            "Cover" to "cover"
        )

        for ((tag, rawKw) in candidateTags) {
            val matches = folderFiltered.filter {
                containsAnyKeyword(it.title, rawKw) ||
                containsAnyKeyword(it.album, rawKw) ||
                containsAnyKeyword(it.folder, rawKw) ||
                (it.genre != null && containsAnyKeyword(it.genre, rawKw))
            }
            if (matches.size >= 2 && normalize(tag) !in existingNormalized) {
                suggestions.add(
                    SuggestedPlaylist(
                        name = tag,
                        rule = SmartPlaylistRule(SmartRuleType.TITLE_CONTAINS, rawKw),
                        matchCount = matches.size
                    )
                )
            }
        }

        // 2. Artists with 2+ tracks
        val artistGroups = folderFiltered.groupBy { it.artist }
            .filter { it.value.size >= 2 && it.key.isNotBlank() && !it.key.equals("Unknown Artist", ignoreCase = true) }
            .toList()
            .sortedByDescending { it.second.size }
            .take(5)

        for ((artistName, artistSongs) in artistGroups) {
            val plName = "Best of $artistName"
            if (normalize(plName) !in existingNormalized) {
                suggestions.add(
                    SuggestedPlaylist(
                        name = plName,
                        rule = SmartPlaylistRule(SmartRuleType.ARTIST_EQUALS, artistName),
                        matchCount = artistSongs.size
                    )
                )
            }
        }

        // 3. Folders with 2+ tracks
        val folderGroups = folderFiltered.groupBy { it.folder }
            .filter { it.value.size >= 2 }
            .toList()
            .sortedByDescending { it.second.size }
            .take(3)

        for ((folderPath, fSongs) in folderGroups) {
            val cleanName = folderPath.trimEnd('/').substringAfterLast('/')
            if (cleanName.isNotBlank() && cleanName != "Music") {
                val plName = "$cleanName Vibes"
                if (normalize(plName) !in existingNormalized) {
                    suggestions.add(
                        SuggestedPlaylist(
                            name = plName,
                            rule = SmartPlaylistRule(SmartRuleType.FOLDER_CONTAINS, cleanName),
                            matchCount = fSongs.size
                        )
                    )
                }
            }
        }

        // 4. Recently Added
        val thirtyDaysAgoSec = (System.currentTimeMillis() / 1000L) - (30L * 86400L)
        val recentMatches = folderFiltered.filter { it.dateAdded >= thirtyDaysAgoSec }
        if (recentMatches.size >= 3 && normalize("Recently Added") !in existingNormalized) {
            suggestions.add(
                SuggestedPlaylist(
                    name = "Recently Added",
                    rule = SmartPlaylistRule(SmartRuleType.ADDED_WITHIN_DAYS, "30"),
                    matchCount = recentMatches.size
                )
            )
        }

        return suggestions
    }
}
