// app/src/main/java/com/bedrockconverter/ui/screens/PreviewScreen.kt
package com.bedrockconverter.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bedrockconverter.model.Model3D
import com.bedrockconverter.ui.components.ModelViewer
import com.bedrockconverter.ui.components.ScaleSelector
import com.bedrockconverter.ui.theme.BedrockColors
import com.bedrockconverter.ui.theme.BedrockShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    model: Model3D,
    currentScale: Float,
    onScaleChange: (Float) -> Unit,
    onExportClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rotationX by remember { mutableFloatStateOf(0f) }
    var rotationY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showModelInfo by remember { mutableStateOf(false) }
    var showScaleSelector by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = model.name,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showModelInfo = !showModelInfo }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Model Info"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                )
            )
        },
        bottomBar = {
            PreviewBottomBar(
                currentScale = currentScale,
                onScaleClick = { showScaleSelector = true },
                onExportClick = onExportClick,
                onResetView = {
                    rotationX = 0f
                    rotationY = 0f
                    zoom = 1f
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 3D Model Viewer
            ModelViewerContainer(
                model = model,
                rotationX = rotationX,
                rotationY = rotationY,
                zoom = zoom,
                onTransform = { rx, ry, z ->
                    rotationX += rx
                    rotationY += ry
                    zoom = (zoom * z).coerceIn(0.5f, 3f)
                }
            )

            // Model Info Panel
            AnimatedVisibility(
                visible = showModelInfo,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                ModelInfoPanel(
                    model = model,
                    onDismiss = { showModelInfo = false }
                )
            }

            // View Controls
            ViewControlsOverlay(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(16.dp),
                onZoomIn = { zoom = (zoom * 1.2f).coerceAtMost(3f) },
                onZoomOut = { zoom = (zoom / 1.2f).coerceAtLeast(0.5f) },
                onReset = {
                    rotationX = 0f
                    rotationY = 0f
                    zoom = 1f
                }
            )

            // Touch Hint
            TouchHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }

        // Scale Selector Bottom Sheet
        if (showScaleSelector) {
            ScaleSelectorBottomSheet(
                currentScale = currentScale,
                onScaleSelected = {
                    onScaleChange(it)
                    showScaleSelector = false
                },
                onDismiss = { showScaleSelector = false }
            )
        }
    }
}

@Composable
private fun ModelViewerContainer(
    model: Model3D,
    rotationX: Float,
    rotationY: Float,
    zoom: Float,
    onTransform: (Float, Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BedrockColors.Background)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    onTransform(
                        pan.y * 0.5f,
                        pan.x * 0.5f,
                        gestureZoom
                    )
                }
            }
    ) {
        // Grid floor indicator
        GridFloor()

        // Actual 3D Model Viewer
        ModelViewer(
            model = model,
            rotationX = rotationX,
            rotationY = rotationY,
            zoom = zoom,
            modifier = Modifier.fillMaxSize()
        )

        // Axis indicator
        AxisIndicator(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}

@Composable
private fun GridFloor() {
    // Visual representation of the grid floor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                BedrockColors.Background
            )
    )
}

@Composable
private fun AxisIndicator(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.size(60.dp),
        shape = BedrockShapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // X axis (red)
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .background(Color.Red)
                    .align(Alignment.CenterStart)
            )
            // Y axis (green)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(20.dp)
                    .background(Color.Green)
                    .align(Alignment.TopCenter)
            )
            // Z axis (blue)
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color.Blue)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun ViewControlsOverlay(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FloatingActionButton(
            onClick = onZoomIn,
            modifier = Modifier.size(40.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Zoom In",
                modifier = Modifier.size(20.dp)
            )
        }

        FloatingActionButton(
            onClick = onZoomOut,
            modifier = Modifier.size(40.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Zoom Out",
                modifier = Modifier.size(20.dp)
            )
        }

        FloatingActionButton(
            onClick = onReset,
            modifier = Modifier.size(40.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset View",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TouchHint(
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            shape = BedrockShapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Drag to rotate • Pinch to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ModelInfoPanel(
    model: Model3D,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(250.dp)
            .padding(16.dp),
        shape = BedrockShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model Info",
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoRow(label = "Name", value = model.name)
            InfoRow(label = "Format", value = model.format.uppercase())
            InfoRow(label = "Vertices", value = model.vertexCount.toString())
            InfoRow(label = "Triangles", value = model.triangleCount.toString())
            InfoRow(label = "Textures", value = model.textureCount.toString())

            if (model.bounds != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bounds",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "X: %.2f  Y: %.2f  Z: %.2f".format(
                        model.bounds.width,
                        model.bounds.height,
                        model.bounds.depth
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PreviewBottomBar(
    currentScale: Float,
    onScaleClick: () -> Unit,
    onExportClick: () -> Unit,
    onResetView: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scale Button
            OutlinedButton(
                onClick = onScaleClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scale: ${currentScale}x")
            }

            // Export Button
            Button(
                onClick = onExportClick,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaleSelectorBottomSheet(
    currentScale: Float,
    onScaleSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        ScaleSelector(
            currentScale = currentScale,
            onScaleSelected = onScaleSelected,
            modifier = Modifier.padding(16.dp)
        )
    }
}