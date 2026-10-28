package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onSelectAllClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSetVideoClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DarkBackground)
            .padding(horizontal = 8.dp)
            .testTag("selection_top_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onCancelClick,
                modifier = Modifier.testTag("selection_cancel_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.testTag("selection_count_text")
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onSelectAllClick,
                modifier = Modifier.testTag("selection_select_all_button")
            ) {
                Text(
                    text = "Select all",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (onSetVideoClick != null) {
                IconButton(
                    onClick = onSetVideoClick,
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag("selection_set_video_button")
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.VideoLibrary,
                        contentDescription = "Set background video",
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                enabled = selectedCount > 0,
                modifier = Modifier.testTag("selection_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
