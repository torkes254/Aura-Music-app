package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.model.MusicFolderItem

class FolderPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "aura_music_folder_prefs"
        private const val KEY_INITIALIZED = "folders_initialized"
        private const val KEY_KNOWN_FOLDERS = "known_folders"
        private const val KEY_ENABLED_FOLDERS = "enabled_folders"
        private const val KEY_NEW_FOLDERS = "new_folders"
    }

    @Synchronized
    fun reconcileFolders(
        discoveredFolders: List<String>,
        folderSongCounts: Map<String, Int>
    ): Pair<List<MusicFolderItem>, Set<String>> {
        val isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)
        val knownFolders = prefs.getStringSet(KEY_KNOWN_FOLDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val enabledFolders = prefs.getStringSet(KEY_ENABLED_FOLDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val newFolders = prefs.getStringSet(KEY_NEW_FOLDERS, emptySet())?.toMutableSet() ?: mutableSetOf()

        if (!isInitialized) {
            // First launch: everything is checked, no new badge
            knownFolders.clear()
            knownFolders.addAll(discoveredFolders)
            enabledFolders.clear()
            enabledFolders.addAll(discoveredFolders)
            newFolders.clear()

            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putStringSet(KEY_KNOWN_FOLDERS, knownFolders)
                .putStringSet(KEY_ENABLED_FOLDERS, enabledFolders)
                .putStringSet(KEY_NEW_FOLDERS, newFolders)
                .apply()
        } else {
            // New folders that appear later are included by default but marked with a "New" badge
            val brandNewFolders = discoveredFolders.filter { it !in knownFolders }
            if (brandNewFolders.isNotEmpty()) {
                knownFolders.addAll(brandNewFolders)
                enabledFolders.addAll(brandNewFolders)
                newFolders.addAll(brandNewFolders)

                prefs.edit()
                    .putStringSet(KEY_KNOWN_FOLDERS, knownFolders)
                    .putStringSet(KEY_ENABLED_FOLDERS, enabledFolders)
                    .putStringSet(KEY_NEW_FOLDERS, newFolders)
                    .apply()
            }
        }

        val items = discoveredFolders.sorted().map { path ->
            MusicFolderItem(
                path = path,
                songCount = folderSongCounts[path] ?: 0,
                isChecked = path in enabledFolders,
                isNew = path in newFolders
            )
        }

        return Pair(items, enabledFolders.toSet())
    }

    @Synchronized
    fun updateFolderSelection(
        newEnabledFolders: Set<String>,
        allCurrentFolders: Set<String>
    ) {
        val knownFolders = prefs.getStringSet(KEY_KNOWN_FOLDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        knownFolders.addAll(allCurrentFolders)

        prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putStringSet(KEY_KNOWN_FOLDERS, knownFolders)
            .putStringSet(KEY_ENABLED_FOLDERS, newEnabledFolders)
            .apply()
    }

    @Synchronized
    fun acknowledgeNewFolders() {
        prefs.edit().putStringSet(KEY_NEW_FOLDERS, emptySet()).apply()
    }

    fun isInitialized(): Boolean = prefs.getBoolean(KEY_INITIALIZED, false)

    fun getEnabledFolders(): Set<String> = prefs.getStringSet(KEY_ENABLED_FOLDERS, emptySet())?.toSet() ?: emptySet()

    fun getNewFolders(): Set<String> = prefs.getStringSet(KEY_NEW_FOLDERS, emptySet())?.toSet() ?: emptySet()

    fun getPersistedFolderSets(): Pair<Set<String>, Set<String>> {
        val known = prefs.getStringSet(KEY_KNOWN_FOLDERS, emptySet())?.toSet() ?: emptySet()
        val enabled = prefs.getStringSet(KEY_ENABLED_FOLDERS, emptySet())?.toSet() ?: emptySet()
        return Pair(known, enabled)
    }

    fun applyPersistedFolderSets(knownFolders: Set<String>, enabledFolders: Set<String>) {
        prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putStringSet(KEY_KNOWN_FOLDERS, knownFolders)
            .putStringSet(KEY_ENABLED_FOLDERS, enabledFolders)
            .apply()
    }
}
