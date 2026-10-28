package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class PlayCountManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun incrementPlayCount(songId: Long): Int {
        val current = prefs.getInt("play_count_$songId", 0)
        val updated = current + 1
        prefs.edit().putInt("play_count_$songId", updated).apply()
        return updated
    }

    fun getPlayCount(songId: Long): Int {
        return prefs.getInt("play_count_$songId", 0)
    }

    fun getPlayCounts(): Map<Long, Int> {
        val map = mutableMapOf<Long, Int>()
        for ((key, value) in prefs.all) {
            if (key.startsWith("play_count_") && value is Int) {
                val id = key.removePrefix("play_count_").toLongOrNull()
                if (id != null) {
                    map[id] = value
                }
            }
        }
        return map
    }

    fun setPlayCount(songId: Long, count: Int) {
        prefs.edit().putInt("play_count_$songId", count).apply()
    }

    fun updatePlayCountsWithRelinkedSongs(relinkedMap: Map<Long, Long>) {
        if (relinkedMap.isEmpty()) return
        val editor = prefs.edit()
        for ((oldId, newId) in relinkedMap) {
            val count = prefs.getInt("play_count_$oldId", 0)
            if (count > 0) {
                val currentNew = prefs.getInt("play_count_$newId", 0)
                editor.putInt("play_count_$newId", maxOf(count, currentNew))
                editor.remove("play_count_$oldId")
            }
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "aura_music_play_counts"

        @Volatile
        private var instance: PlayCountManager? = null

        fun getInstance(context: Context): PlayCountManager {
            return instance ?: synchronized(this) {
                instance ?: PlayCountManager(context).also { instance = it }
            }
        }
    }
}
