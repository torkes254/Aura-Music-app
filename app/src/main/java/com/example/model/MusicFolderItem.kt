package com.example.model

data class MusicFolderItem(
    val path: String,
    val songCount: Int,
    val isChecked: Boolean,
    val isNew: Boolean = false
) {
    val displayName: String
        get() = path.trimEnd('/')
}
