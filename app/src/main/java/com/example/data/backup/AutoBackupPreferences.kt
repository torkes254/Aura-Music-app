package com.example.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AutoBackupState(
    val isEnabled: Boolean = false,
    val folderUri: String? = null,
    val folderDisplayName: String? = null,
    val lastBackupTimeMs: Long = 0L,
    val includeMedia: Boolean = true
) {
    val formattedLastBackup: String
        get() = if (lastBackupTimeMs > 0L) {
            SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(lastBackupTimeMs))
        } else {
            "Never"
        }
}

class AutoBackupPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<AutoBackupState> = _state.asStateFlow()

    private fun loadState(): AutoBackupState {
        return AutoBackupState(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            folderUri = prefs.getString(KEY_FOLDER_URI, null),
            folderDisplayName = prefs.getString(KEY_FOLDER_NAME, null),
            lastBackupTimeMs = prefs.getLong(KEY_LAST_BACKUP, 0L),
            includeMedia = prefs.getBoolean(KEY_INCLUDE_MEDIA, true)
        )
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(isEnabled = enabled)
    }

    fun setFolder(uri: Uri?, displayName: String?) {
        val uriStr = uri?.toString()
        prefs.edit()
            .putString(KEY_FOLDER_URI, uriStr)
            .putString(KEY_FOLDER_NAME, displayName)
            .apply()
        _state.value = _state.value.copy(
            folderUri = uriStr,
            folderDisplayName = displayName
        )
    }

    fun setLastBackupTime(timeMs: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP, timeMs).apply()
        _state.value = _state.value.copy(lastBackupTimeMs = timeMs)
    }

    fun setIncludeMedia(include: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_MEDIA, include).apply()
        _state.value = _state.value.copy(includeMedia = include)
    }

    companion object {
        private const val PREFS_NAME = "aura_music_auto_backup_prefs"
        private const val KEY_ENABLED = "auto_backup_enabled"
        private const val KEY_FOLDER_URI = "auto_backup_folder_uri"
        private const val KEY_FOLDER_NAME = "auto_backup_folder_name"
        private const val KEY_LAST_BACKUP = "auto_backup_last_time"
        private const val KEY_INCLUDE_MEDIA = "auto_backup_include_media"

        @Volatile
        private var INSTANCE: AutoBackupPreferences? = null

        fun getInstance(context: Context): AutoBackupPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoBackupPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
