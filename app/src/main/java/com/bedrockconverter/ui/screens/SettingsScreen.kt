// app/src/main/java/com/bedrockconverter/ui/screens/SettingsScreen.kt
package com.bedrockconverter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.bedrockconverter.ui.theme.BedrockColors
import com.bedrockconverter.ui.theme.BedrockShapes

data class AppSettings(
    val defaultScale: Float = 1f,
    val defaultNamespace: String = "custom",
    val autoOptimize: Boolean = true,
    val darkTheme: Boolean = true,
    val showAdvancedOptions: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClearCache: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var showClearCacheDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Default Export Settings
            SettingsSection(title = "Default Export Settings") {
                // Default Scale
                SettingsDropdown(
                    icon = Icons.Default.AspectRatio,
                    title = "Default Scale",
                    subtitle = "${settings.defaultScale}x",
                    options = listOf("0.25x" to 0.25f, "0.5x" to 0.5f, "1x" to 1f, "2x" to 2f),
                    selectedValue = settings.defaultScale,
                    onValueChange = { onSettingsChange(settings.copy(defaultScale = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Default Namespace
                SettingsTextField(
                    icon = Icons.Default.Label,
                    title = "Default Namespace",
                    value = settings.defaultNamespace,
                    onValueChange = { onSettingsChange(settings.copy(defaultNamespace = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Auto Optimize
                SettingsSwitch(
                    icon = Icons.Default.Speed,
                    title = "Auto Optimize",
                    subtitle = "Automatically optimize models for mobile",
                    checked = settings.autoOptimize,
                    onCheckedChange = { onSettingsChange(settings.copy(autoOptimize = it)) }
                )
            }

            // Appearance
            SettingsSection(title = "Appearance") {
                SettingsSwitch(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Theme",
                    subtitle = "Use dark color scheme",
                    checked = settings.darkTheme,
                    onCheckedChange = { onSettingsChange(settings.copy(darkTheme = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitch(
                    icon = Icons.Default.Code,
                    title = "Advanced Options",
                    subtitle = "Show advanced export options",
                    checked = settings.showAdvancedOptions,
                    onCheckedChange = { onSettingsChange(settings.copy(showAdvancedOptions = it)) }
                )
            }

            // Storage
            SettingsSection(title = "Storage") {
                SettingsClickable(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear Cache",
                    subtitle = "Remove temporary files and cached models",
                    onClick = { showClearCacheDialog = true }
                )
            }

            // About
            SettingsSection(title = "About") {
                SettingsClickable(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = { }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickable(
                    icon = Icons.Default.Description,
                    title = "Minecraft Bedrock Docs",
                    subtitle = "Official addon documentation",
                    onClick = {
                        uriHandler.openUri("https://learn.microsoft.com/en-us/minecraft/creator/")
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickable(
                    icon = Icons.Default.BugReport,
                    title = "Report Issue",
                    subtitle = "Report bugs or suggest features",
                    onClick = {
                        // Open issue tracker
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Clear Cache Dialog
        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = BedrockColors.Warning
                    )
                },
                title = { Text("Clear Cache") },
                text = { Text("This will remove all temporary files and cached models. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearCache()
                            showClearCacheDialog = false
                        }
                    ) {
                        Text("Clear", color = BedrockColors.Error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = BedrockColors.Primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = BedrockShapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(content = content)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsClickable(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdown(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<Pair<String, T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.menuAnchor()
            ) {
                Text(options.find { it.second == selectedValue }?.first ?: "")
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onValueChange(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTextField(
    icon: ImageVector,
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.lowercase().replace(" ", "_")) },
            modifier = Modifier.width(120.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}