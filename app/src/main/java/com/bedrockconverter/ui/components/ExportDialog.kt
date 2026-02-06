// app/src/main/java/com/bedrockconverter/ui/components/ExportDialog.kt
package com.bedrockconverter.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bedrockconverter.model.ExportSettings
import com.bedrockconverter.ui.theme.BedrockColors
import com.bedrockconverter.ui.theme.BedrockShapes

sealed class ExportDialogState {
    object Hidden : ExportDialogState()
    object Settings : ExportDialogState()
    data class Progress(val progress: Float, val step: String) : ExportDialogState()
    data class Success(val filePath: String, val entityId: String) : ExportDialogState()
    data class Error(val message: String) : ExportDialogState()
}

@Composable
fun ExportDialog(
    state: ExportDialogState,
    settings: ExportSettings,
    onSettingsChange: (ExportSettings) -> Unit,
    onExport: () -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (state == ExportDialogState.Hidden) return

    Dialog(
        onDismissRequest = {
            if (state !is ExportDialogState.Progress) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is ExportDialogState.Progress,
            dismissOnClickOutside = state !is ExportDialogState.Progress
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = BedrockShapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            when (state) {
                is ExportDialogState.Settings -> {
                    ExportSettingsContent(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onExport = onExport,
                        onCancel = onDismiss
                    )
                }
                is ExportDialogState.Progress -> {
                    ExportProgressContent(
                        progress = state.progress,
                        step = state.step
                    )
                }
                is ExportDialogState.Success -> {
                    ExportSuccessContent(
                        filePath = state.filePath,
                        entityId = state.entityId,
                        onShare = { onShare(state.filePath) },
                        onDone = onDismiss
                    )
                }
                is ExportDialogState.Error -> {
                    ExportErrorContent(
                        message = state.message,
                        onRetry = onExport,
                        onCancel = onDismiss
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ExportSettingsContent(
    settings: ExportSettings,
    onSettingsChange: (ExportSettings) -> Unit,
    onExport: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.padding(24.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = null,
                tint = BedrockColors.Primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Export Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Entity Name
        OutlinedTextField(
            value = settings.entityName,
            onValueChange = { 
                onSettingsChange(settings.copy(
                    entityName = it.lowercase()
                        .replace(" ", "_")
                        .replace(Regex("[^a-z0-9_]"), "")
                ))
            },
            label = { Text("Entity Name") },
            placeholder = { Text("my_model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Label,
                    contentDescription = null
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Command: /summon ${settings.namespace}:${settings.entityName}",
            style = MaterialTheme.typography.bodySmall,
            color = BedrockColors.Accent
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Namespace
        OutlinedTextField(
            value = settings.namespace,
            onValueChange = { 
                onSettingsChange(settings.copy(
                    namespace = it.lowercase()
                        .replace(" ", "_")
                        .replace(Regex("[^a-z0-9_]"), "")
                ))
            },
            label = { Text("Namespace") },
            placeholder = { Text("custom") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Scale Display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scale",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${settings.scale}x",
                style = MaterialTheme.typography.titleMedium,
                color = BedrockColors.Primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Options
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = settings.generateCollision,
                onCheckedChange = { 
                    onSettingsChange(settings.copy(generateCollision = it))
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Generate collision box",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = settings.optimizeGeometry,
                onCheckedChange = { 
                    onSettingsChange(settings.copy(optimizeGeometry = it))
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Optimize for mobile",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BedrockColors.Primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export")
            }
        }
    }
}

@Composable
private fun ExportProgressContent(
    progress: Float,
    step: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated Progress Indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(100.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(100.dp),
                color = BedrockColors.Primary,
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Exporting...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = step,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = BedrockColors.Primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun ExportSuccessContent(
    filePath: String,
    entityId: String,
    onShare: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(BedrockColors.Success.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = BedrockColors.Success,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Export Complete!",
            style = MaterialTheme.typography.titleLarge,
            color = BedrockColors.Success
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Spawn Command Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = BedrockShapes.small,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Spawn Command",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = "/summon $entityId",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BedrockColors.Accent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // File Path
        Text(
            text = "Saved to:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = filePath.substringAfterLast("/"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.weight(1f)
            ) {
                Text("Done")
            }

            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BedrockColors.Primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share")
            }
        }
    }
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    // Simple wrapper for selectable text
    Box { content() }
}

@Composable
private fun ExportErrorContent(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Error Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(BedrockColors.Error.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = BedrockColors.Error,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Export Failed",
            style = MaterialTheme.typography.titleLarge,
            color = BedrockColors.Error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BedrockColors.Primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}