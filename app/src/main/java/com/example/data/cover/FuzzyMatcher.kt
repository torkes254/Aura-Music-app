package com.example.data.cover

import kotlin.math.max

object FuzzyMatcher {

    fun similarity(s1: String, s2: String): Double {
        val str1 = s1.trim().lowercase()
        val str2 = s2.trim().lowercase()
        if (str1 == str2) return 1.0
        if (str1.isEmpty() || str2.isEmpty()) return 0.0

        val maxLen = max(str1.length, str2.length)
        val distance = levenshteinDistance(str1, str2)
        return (maxLen - distance).toDouble() / maxLen.toDouble()
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length

        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1) { 0 }

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }

        return cost[lhsLen]
    }

    fun isCloseMatch(
        targetArtist: String,
        targetTitle: String,
        candidateArtist: String,
        candidateTitle: String
    ): Boolean {
        val artistSim = similarity(targetArtist, candidateArtist)
        val titleSim = similarity(targetTitle, candidateTitle)

        // If artist is closely matching and title is very close
        if (artistSim >= 0.75 && titleSim >= 0.6) return true

        // Check if one contains the other
        val tNorm = targetTitle.lowercase().trim()
        val cNorm = candidateTitle.lowercase().trim()
        if (artistSim >= 0.7 && (tNorm.contains(cNorm) || cNorm.contains(tNorm))) return true

        return false
    }
}
