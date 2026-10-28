package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.backup.BackupMode
import com.example.data.backup.BackupSummary
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ExportBackupDialog(
    mediaSizeBytes: Long,
    includeMedia: Boolean,
    onToggleIncludeMedia: (Boolean) -> Unit,
    onConfirmExport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedMediaSize = if (mediaSizeBytes > 0) {
        val mb = mediaSizeBytes.toDouble() / (1024.0 * 1024.0)
        String.format("%.1f MB", mb)
    } else {
        "< 0.1 MB"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export backup",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Create a single .zip backup file that can be saved anywhere with the file picker (Google Drive, Downloads, Documents) and survives app uninstalls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // What's included summary
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Backup contains:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Playlists & smart playlist rules", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("• Custom playlist & album cover art", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("• Global & per-song background videos", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("• Settings, hidden tabs & music folders", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("• Play counts & listening history", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Note: Music files are not included. Songs are identified by stable keys.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle: Include background videos and images
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include background videos and images",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (includeMedia) "Total size: $formattedMediaSize" else "Total size: < 0.1 MB (metadata only)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (includeMedia) MaterialTheme.colorScheme.primary else TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = includeMedia,
                            onCheckedChange = onToggleIncludeMedia,
                            modifier = Modifier.testTag("toggle_include_media_backup")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmExport,
                modifier = Modifier.testTag("confirm_export_backup_button")
            ) {
                Text("Export .zip")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_export_backup_button")
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier.testTag("export_backup_dialog")
    )
}

@Composable
fun ImportSummaryDialog(
    summary: BackupSummary,
    onChooseMode: (BackupMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Import backup",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${summary.formattedDate} • v${summary.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Backup file verified. Review contents before restoring:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stats breakdown
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryRow(
                            icon = Icons.Default.QueueMusic,
                            label = "Playlists",
                            value = "${summary.playlistCount} playlist(s)"
                        )
                        SummaryRow(
                            icon = Icons.Default.MusicNote,
                            label = "Songs matched",
                            value = "${summary.matchedSongCount} of ${summary.totalSongRefCount} found locally"
                        )
                        SummaryRow(
                            icon = Icons.Default.Album,
                            label = "Custom covers",
                            value = "${summary.pickedCoverCount} cover(s)"
                        )
                        val videoText = when {
                            summary.hasGlobalVideo && summary.songVideoCount > 0 -> "Global video + ${summary.songVideoCount} song video(s)"
                            summary.hasGlobalVideo -> "Global video"
                            summary.songVideoCount > 0 -> "${summary.songVideoCount} song video(s)"
                            else -> "None"
                        }
                        SummaryRow(
                            icon = Icons.Default.VideoLibrary,
                            label = "Background videos",
                            value = videoText
                        )
                        SummaryRow(
                            icon = Icons.Default.Settings,
                            label = "Settings",
                            value = "Tabs, folders, video options"
                        )
                    }
                }

                // If some songs were not found, display dedicated banner
                if (summary.unlinkedSongCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${summary.unlinkedSongCount} songs not found",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Entries for missing songs will be preserved in your playlists and automatically linked when those audio files are copied back to this phone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = DarkSurfaceBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Choose import strategy:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Merge Button
                Button(
                    onClick = { onChooseMode(BackupMode.MERGE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_merge_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Merge with current data", fontWeight = FontWeight.Bold)
                        Text(
                            "Combines playlists, covers, and play counts without erasing existing items",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Replace Button
                OutlinedButton(
                    onClick = { onChooseMode(BackupMode.REPLACE) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_replace_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Replace all current data", fontWeight = FontWeight.Bold)
                        Text(
                            "Completely overwrites current playlists, covers, settings, and videos",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_import_summary_button")
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier.testTag("import_summary_dialog")
    )
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}
