// app/src/main/java/com/bedrockconverter/ui/screens/ExportScreen.kt
package com.bedrockconverter.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.bedrockconverter.model.ExportSettings
import com.bedrockconverter.model.Model3D
import com.bedrockconverter.ui.theme.BedrockColors
import com.bedrockconverter.ui.theme.BedrockShapes

sealed class ExportState {
    object Idle : ExportState()
    data class Exporting(val progress: Float, val currentStep: String) : ExportState()
    data class Success(val filePath: String, val entityIdentifier: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    model: Model3D,
    exportSettings: ExportSettings,
    exportState: ExportState,
    onSettingsChange: (ExportSettings) -> Unit,
    onExport: () -> Unit,
    onShare: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export to Bedrock") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Model Summary Card
                ModelSummaryCard(model = model)

                // Export Settings Card
                ExportSettingsCard(
                    settings = exportSettings,
                    onSettingsChange = onSettingsChange,
                    enabled = exportState is ExportState.Idle || exportState is ExportState.Error
                )

                // Export Progress / Result
                when (exportState) {
                    is ExportState.Idle -> {
                        ExportButton(
                            onClick = onExport,
                            enabled = true
                        )
                    }
                    is ExportState.Exporting -> {
                        ExportProgressCard(
                            progress = exportState.progress,
                            currentStep = exportState.currentStep
                        )
                    }
                    is ExportState.Success -> {
                        ExportSuccessCard(
                            filePath = exportState.filePath,
                            entityIdentifier = exportState.entityIdentifier,
                            onShare = { onShare(exportState.filePath) }
                        )
                    }
                    is ExportState.Error -> {
                        ExportErrorCard(
                            message = exportState.message,
                            onRetry = onExport
                        )
                    }
                }

                // Instructions
                ExportInstructionsCard()
            }
        }
    }
}

@Composable
private fun ModelSummaryCard(
    model: Model3D
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BedrockShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(BedrockShapes.small)
                    .background(BedrockColors.Primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = BedrockColors.Primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${model.vertexCount} vertices • ${model.triangleCount} triangles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ExportSettingsCard(
    settings: ExportSettings,
    onSettingsChange: (ExportSettings) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BedrockShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Export Settings",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Entity Name
            OutlinedTextField(
                value = settings.entityName,
                onValueChange = {
                    onSettingsChange(settings.copy(entityName = it.lowercase().replace(" ", "_")))
                },
                label = { Text("Entity Name") },
                placeholder = { Text("my_model") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("Used in: /summon custom:${settings.entityName}")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Namespace
            OutlinedTextField(
                value = settings.namespace,
                onValueChange = {
                    onSettingsChange(settings.copy(namespace = it.lowercase().replace(" ", "_")))
                },
                label = { Text("Namespace") },
                placeholder = { Text("custom") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scale Selection
            Text(
                text = "Model Scale",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.25f, 0.5f, 1f, 2f).forEach { scale ->
                    FilterChip(
                        selected = settings.scale == scale,
                        onClick = {
                            if (enabled) onSettingsChange(settings.copy(scale = scale))
                        },
                        label = { Text("${scale}x") },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom Scale Slider
            Text(
                text = "Custom: ${String.format("%.2f", settings.scale)}x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Slider(
                value = settings.scale,
                onValueChange = { onSettingsChange(settings.copy(scale = it)) },
                valueRange = 0.1f..5f,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Additional Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = settings.generateCollision,
                    onCheckedChange = {
                        if (enabled) onSettingsChange(settings.copy(generateCollision = it))
                    },
                    enabled = enabled
                )
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
                        if (enabled) onSettingsChange(settings.copy(optimizeGeometry = it))
                    },
                    enabled = enabled
                )
                Text(
                    text = "Optimize geometry for mobile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ExportButton(
    onClick: () -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = BedrockShapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = BedrockColors.Primary
        )
    ) {
        Icon(
            imageVector = Icons.Default.FileDownload,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Export as .mcaddon",
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun ExportProgressCard(
    progress: Float,
    currentStep: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BedrockShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                progress = { progress },
                color = BedrockColors.Primary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentStep,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = BedrockColors.Primary
            )
        }
    }
}

@Composable
private fun ExportSuccessCard(
    filePath: String,
    entityIdentifier: String,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BedrockShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = BedrockColors.Success.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BedrockColors.Success.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = BedrockColors.Success,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Export Successful!",
                style = MaterialTheme.typography.titleMedium,
                color = BedrockColors.Success
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Command to spawn
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = BedrockShapes.small,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Spawn Command:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "/summon $entityIdentifier",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BedrockColors.Accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Share Button
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BedrockColors.Primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share .mcaddon")
            }
        }
    }
}

@Composable
private fun ExportErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BedrockShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = BedrockColors.Error.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = BedrockColors.Error,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Export Failed",
                style = MaterialTheme.typography.titleMedium,
                color = BedrockColors.Error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ExportInstructionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BedrockShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = BedrockColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How to use in Minecraft",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            InstructionStep(
                number = 1,
                text = "Open the exported .mcaddon file"
            )
            InstructionStep(
                number = 2,
                text = "Minecraft will automatically import it"
            )
            InstructionStep(
                number = 3,
                text = "Create a new world with the addon enabled"
            )
            InstructionStep(
                number = 4,
                text = "Use the /summon command to spawn your model"
            )
        }
    }
}

@Composable
private fun InstructionStep(
    number: Int,
    text: String
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(BedrockColors.Accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = BedrockColors.Accent
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}