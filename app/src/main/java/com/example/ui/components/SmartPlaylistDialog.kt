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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SmartPlaylistEvaluator
import com.example.model.Playlist
import com.example.model.SmartPlaylistRule
import com.example.model.SmartRuleType
import com.example.model.Song
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlaylistDialog(
    allSongs: List<Song>,
    enabledFolders: Set<String>,
    onDismiss: () -> Unit,
    onCreateSmartPlaylist: (name: String, matchAll: Boolean, rules: List<SmartPlaylistRule>, exclusions: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var matchAllRules by remember { mutableStateOf(true) }
    var exclusionKeywords by remember { mutableStateOf("") }

    val rules = remember {
        mutableStateListOf(
            SmartPlaylistRule(type = SmartRuleType.TITLE_CONTAINS, value = "")
        )
    }

    // Live match count evaluation
    val liveMatchingSongs by remember(allSongs, enabledFolders, matchAllRules, rules.toList(), exclusionKeywords) {
        derivedStateOf {
            val candidatePl = Playlist(
                id = 0L,
                name = name,
                isSmart = true,
                matchAllRules = matchAllRules,
                rules = rules.toList(),
                exclusionKeywords = exclusionKeywords
            )
            SmartPlaylistEvaluator.evaluate(candidatePl, allSongs, enabledFolders)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Smart Playlist",
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
                // Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("e.g. Nightcore Mix") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_playlist_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Match Mode Chips
                Text(
                    text = "Matching Mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = matchAllRules,
                        onClick = { matchAllRules = true },
                        label = { Text("Match ALL rules") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("match_mode_all")
                    )

                    FilterChip(
                        selected = !matchAllRules,
                        onClick = { matchAllRules = false },
                        label = { Text("Match ANY rule") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("match_mode_any")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rules Header & List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rules (${rules.size})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    TextButton(
                        onClick = {
                            rules.add(SmartPlaylistRule(SmartRuleType.TITLE_CONTAINS, ""))
                        },
                        modifier = Modifier.testTag("add_rule_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Rule")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                rules.forEachIndexed { index, rule ->
                    RuleRow(
                        rule = rule,
                        canRemove = rules.size > 1,
                        onRuleChange = { updated -> rules[index] = updated },
                        onRemove = { rules.removeAt(index) },
                        index = index
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Exclusion Keywords
                OutlinedTextField(
                    value = exclusionKeywords,
                    onValueChange = { exclusionKeywords = it },
                    label = { Text("Exclusion Keywords (optional)") },
                    placeholder = { Text("e.g. live, acoustic, demo") },
                    supportingText = { Text("Songs matching any exclusion keyword are removed") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_playlist_exclusions_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Live Count Preview Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("live_match_count_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Preview",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = "${liveMatchingSongs.size} songs match",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("live_match_count")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreateSmartPlaylist(
                            name.trim(),
                            matchAllRules,
                            rules.toList(),
                            exclusionKeywords.trim()
                        )
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_smart_playlist")
            ) {
                Text("Create Smart Playlist")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_create_smart_playlist")
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier.testTag("smart_playlist_dialog")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleRow(
    rule: SmartPlaylistRule,
    canRemove: Boolean,
    onRuleChange: (SmartPlaylistRule) -> Unit,
    onRemove: () -> Unit,
    index: Int
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceContainer)
            .padding(10.dp)
            .testTag("rule_row_$index")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = rule.type.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Rule Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(DarkSurfaceContainerHigh)
                ) {
                    SmartRuleType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label, fontSize = 14.sp) },
                            onClick = {
                                expanded = false
                                onRuleChange(rule.copy(type = type))
                            }
                        )
                    }
                }
            }

            if (canRemove) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp).testTag("delete_rule_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove rule",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val isNumeric = rule.type == SmartRuleType.ADDED_WITHIN_DAYS || rule.type == SmartRuleType.MOST_PLAYED
        val placeholderText = when (rule.type) {
            SmartRuleType.ADDED_WITHIN_DAYS -> "Days (e.g. 30)"
            SmartRuleType.MOST_PLAYED -> "Max songs limit (e.g. 25)"
            SmartRuleType.GENRE_CONTAINS -> "Keywords (e.g. electronic, rock)"
            SmartRuleType.FOLDER_CONTAINS -> "Folder name or path"
            SmartRuleType.ARTIST_EQUALS -> "Exact artist name"
            else -> "Keywords (e.g. nightcore, remix)"
        }

        OutlinedTextField(
            value = rule.value,
            onValueChange = { onRuleChange(rule.copy(value = it)) },
            label = {
                Text(
                    when (rule.type) {
                        SmartRuleType.ADDED_WITHIN_DAYS -> "Days count"
                        SmartRuleType.MOST_PLAYED -> "Top songs limit"
                        else -> "Keywords (comma-separated)"
                    }
                )
            },
            placeholder = { Text(placeholderText) },
            singleLine = true,
            keyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rule_value_input_$index")
        )
    }
}
