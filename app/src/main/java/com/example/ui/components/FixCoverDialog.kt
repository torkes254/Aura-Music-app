package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.Song
import com.example.ui.theme.TextSecondary

@Composable
fun FixCoverDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSearchOnline: (String, String, String?) -> Unit,
    onPickLocalImage: (Uri) -> Unit,
    isSearching: Boolean = false
) {
    var albumName by remember { mutableStateOf(song.album) }
    var artistName by remember { mutableStateOf(song.artist) }
    var trackTitle by remember { mutableStateOf(song.title) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickLocalImage(uri)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Fix Cover Art")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Refine details to search online artwork or choose an image file from your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = trackTitle,
                    onValueChange = { trackTitle = it },
                    label = { Text("Track Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fix_cover_title_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = artistName,
                    onValueChange = { artistName = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fix_cover_artist_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    label = { Text("Album") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fix_cover_album_input")
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_local_cover_button")
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Image from Device")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSearchOnline(albumName, artistName, trackTitle)
                    onDismiss()
                },
                enabled = !isSearching,
                modifier = Modifier.testTag("search_cover_online_button")
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                } else {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Search Online")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_fix_cover_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
