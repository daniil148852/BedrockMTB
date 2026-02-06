// app/src/main/java/com/bedrockconverter/ui/components/ScaleSelector.kt
package com.bedrockconverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bedrockconverter.ui.theme.BedrockColors
import com.bedrockconverter.ui.theme.BedrockShapes

@Composable
fun ScaleSelector(
    currentScale: Float,
    onScaleSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val presetScales = listOf(0.25f, 0.5f, 1f, 2f, 4f)
    var customScale by remember { mutableFloatStateOf(currentScale) }
    var useCustom by remember { mutableStateOf(!presetScales.contains(currentScale)) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select Model Scale",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Choose how large your model will appear in Minecraft",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Preset Scale Options
        Text(
            text = "Preset Scales",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetScales.forEach { scale ->
                ScaleChip(
                    scale = scale,
                    isSelected = !useCustom && currentScale == scale,
                    onClick = {
                        useCustom = false
                        onScaleSelected(scale)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Scale
        Text(
            text = "Custom Scale",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Slider(
                value = customScale,
                onValueChange = { 
                    customScale = it
                    useCustom = true
                },
                valueRange = 0.1f..5f,
                steps = 48,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = BedrockColors.Primary,
                    activeTrackColor = BedrockColors.Primary
                )
            )

            Text(
                text = String.format("%.2fx", customScale),
                style = MaterialTheme.typography.titleSmall,
                color = if (useCustom) BedrockColors.Primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(60.dp),
                textAlign = TextAlign.End
            )
        }

        // Apply Custom Button
        if (useCustom) {
            Button(
                onClick = { onScaleSelected(customScale) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BedrockColors.Primary
                )
            ) {
                Text("Apply Custom Scale (${String.format("%.2fx", customScale)})")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scale Reference Info
        ScaleReferenceCard()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ScaleChip(
    scale: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        BedrockColors.Primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val borderColor = if (isSelected) {
        BedrockColors.Primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .clip(BedrockShapes.small)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = BedrockShapes.small
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${scale}x",
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScaleReferenceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BedrockShapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Scale Reference",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            ScaleReferenceRow(scale = "0.25x", description = "Small item (1/4 block)")
            ScaleReferenceRow(scale = "0.5x", description = "Half block size")
            ScaleReferenceRow(scale = "1x", description = "Standard (1 block = 1 unit)")
            ScaleReferenceRow(scale = "2x", description = "Large (2 blocks)")
            ScaleReferenceRow(scale = "4x", description = "Very large (4 blocks)")
        }
    }
}

@Composable
private fun ScaleReferenceRow(
    scale: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = scale,
            style = MaterialTheme.typography.bodySmall,
            color = BedrockColors.Primary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}