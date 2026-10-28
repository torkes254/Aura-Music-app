package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

enum class RecentlyAddedTimeframe(val days: Int, val label: String, val emptyMessage: String) {
    LAST_7_DAYS(7, "7 days", "Nothing added in the last 7 days"),
    LAST_30_DAYS(30, "30 days", "Nothing added in the last 30 days"),
    LAST_90_DAYS(90, "90 days", "Nothing added in the last 90 days"),
    ALL(0, "All", "Nothing added yet")
}

class RecentlyAddedPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "aura_music_recently_added_prefs"
        private const val KEY_TIMEFRAME = "timeframe_days"
    }

    fun getTimeframe(): RecentlyAddedTimeframe {
        val days = prefs.getInt(KEY_TIMEFRAME, RecentlyAddedTimeframe.LAST_30_DAYS.days)
        return RecentlyAddedTimeframe.values().find { it.days == days } ?: RecentlyAddedTimeframe.LAST_30_DAYS
    }

    fun setTimeframe(timeframe: RecentlyAddedTimeframe) {
        prefs.edit().putInt(KEY_TIMEFRAME, timeframe.days).apply()
    }
}
