package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class TabPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "aura_music_tab_preferences"
        private const val KEY_HIDDEN_TABS = "hidden_tabs"
    }

    fun getHiddenTabs(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_TABS, emptySet())?.toSet() ?: emptySet()
    }

    fun setTabHidden(tabName: String, isHidden: Boolean) {
        val current = getHiddenTabs().toMutableSet()
        if (isHidden) {
            current.add(tabName)
        } else {
            current.remove(tabName)
        }
        prefs.edit().putStringSet(KEY_HIDDEN_TABS, current).apply()
    }
}
