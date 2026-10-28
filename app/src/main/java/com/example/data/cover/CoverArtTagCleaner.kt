package com.example.data.cover

data class CleanedMetadata(
    val title: String,
    val artist: String
)

object CoverArtTagCleaner {

    private val JUNK_PATTERNS = listOf(
        """(?i)[\(\[\{]\s*(?:official\s*(?:music\s*)?video|official\s*audio|official|lyrics?|lyric\s*video|audio|letra|video\s*clip|clip\s*officiel|hd|hq|4k|remastered?|remaster|live(?:\s*version)?|bonus\s*track)\s*[\)\]\}]""",
        """(?i)[\(\[\{]\s*(?:feat\.?|ft\.?)\s+[^)\]\}]+[\)\]\}]""",
        """(?i)\b(?:feat\.?|ft\.?)\s+.*$""",
        """[\uD83C-\uDBFF\uDC00-\uDFFF]+""" // emojis
    )

    fun clean(rawTitle: String, rawArtist: String): CleanedMetadata {
        var title = rawTitle.trim()
        var artist = rawArtist.trim()

        // Strip leading track numbers like "02. " or "1 - "
        title = title.replace(Regex("""^\d{1,3}[\.\-\s_]+\s*"""), "").trim()

        // If artist is unknown or generic, see if title has "Artist - Title"
        val isArtistUnknown = artist.isEmpty() ||
                artist.equals("Unknown Artist", ignoreCase = true) ||
                artist.equals("<unknown>", ignoreCase = true) ||
                artist.equals("Various Artists", ignoreCase = true)

        if (isArtistUnknown && (title.contains(" - ") || title.contains(" – "))) {
            val delimiter = if (title.contains(" – ")) " – " else " - "
            val parts = title.split(delimiter, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                artist = parts[0].trim()
                title = parts[1].trim()
            }
        }

        for (pattern in JUNK_PATTERNS) {
            title = title.replace(Regex(pattern), "").trim()
        }

        // Clean extra trailing separators or spaces
        title = title.replace(Regex("""[\s\-_]+$"""), "").trim()
        artist = artist.replace(Regex("""[\s\-_]+$"""), "").trim()

        return CleanedMetadata(title = title, artist = artist)
    }
}
