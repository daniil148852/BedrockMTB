// app/src/main/java/com/bedrockconverter/ui/screens/HomeScreen.kt
package com.bedrockconverter.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bedrockconverter.model.Model3D
import com.bedrockconverter.ui.theme.BedrockColors
import com.bedrockconverter.ui.theme.BedrockShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentModels: List<Model3D>,
    isLoading: Boolean,
    onImportModel: (Uri) -> Unit,
    onModelSelected: (Model3D) -> Unit,
    onDeleteModel: (Model3D) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportModel(it) }
    }

    val supportedMimeTypes = arrayOf(
        "model/gltf-binary",      // .glb
        "model/gltf+json",        // .gltf
        "application/octet-stream", // fallback for .glb, .fbx
        "*/*"                     // Allow all for broader compatibility
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewInAr,
                            contentDescription = null,
                            tint = BedrockColors.Primary
                        )
                        Text("Bedrock Converter")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Import Card
                item {
                    ImportCard(
                        onImportClick = {
                            filePickerLauncher.launch(supportedMimeTypes)
                        },
                        isLoading = isLoading
                    )
                }

                // Supported Formats Info
                item {
                    SupportedFormatsCard()
                }

                // Recent Models Section
                if (recentModels.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent Models",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(
                        items = recentModels,
                        key = { it.id }
                    ) { model ->
                        RecentModelCard(
                            model = model,
                            onClick = { onModelSelected(model) },
                            onDelete = { onDeleteModel(model) }
                        )
                    }
                }

                // Quick Guide
                item {
                    QuickGuideCard()
                }
            }

            // Loading Overlay
            if (isLoading) {
                LoadingOverlay()
            }
        }
    }
}

@Composable
private fun ImportCard(
    onImportClick: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable(enabled = !isLoading) { onImportClick() },
        shape = BedrockShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BedrockColors.Primary.copy(alpha = 0.1f),
                            BedrockColors.Accent.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    color = BedrockColors.Primary.copy(alpha = 0.5f),
                    shape = BedrockShapes.large
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BedrockColors.Primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = BedrockColors.Primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Import 3D Model",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tap to select a file",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SupportedFormatsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BedrockShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Supported Formats",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FormatChip(format = ".GLB", description = "Recommended")
                FormatChip(format = ".GLTF", description = "With textures")
                FormatChip(format = ".OBJ", description = "Simple")
                FormatChip(format = ".FBX", description = "Complex")
            }
        }
    }
}

@Composable
private fun FormatChip(
    format: String,
    description: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = BedrockShapes.small,
            color = BedrockColors.Primary.copy(alpha = 0.2f)
        ) {
            Text(
                text = format,
                style = MaterialTheme.typography.labelLarge,
                color = BedrockColors.Primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun RecentModelCard(
    model: Model3D,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
            // Model Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(BedrockShapes.small)
                    .background(BedrockColors.Secondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = BedrockColors.Secondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Model Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelInfoChip(
                        icon = Icons.Outlined.Category,
                        text = "${model.vertexCount} vertices"
                    )
                    ModelInfoChip(
                        icon = Icons.Outlined.Texture,
                        text = model.format.uppercase()
                    )
                }
            }

            // Delete Button
            IconButton(
                onClick = { showDeleteDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Model") },
            text = { Text("Are you sure you want to delete '${model.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ModelInfoChip(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun QuickGuideCard() {
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Help,
                    contentDescription = null,
                    tint = BedrockColors.Accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quick Guide",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GuideStep(
                number = 1,
                title = "Import Model",
                description = "Select a 3D model file from your device"
            )

            GuideStep(
                number = 2,
                title = "Preview & Adjust",
                description = "Preview your model and adjust scale settings"
            )

            GuideStep(
                number = 3,
                title = "Export",
                description = "Export as .mcaddon file"
            )

            GuideStep(
                number = 4,
                title = "Use in Minecraft",
                description = "Import addon and use /summon command"
            )
        }
    }
}

@Composable
private fun GuideStep(
    number: Int,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(BedrockColors.Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = BedrockShapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = BedrockColors.Primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Importing model...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}