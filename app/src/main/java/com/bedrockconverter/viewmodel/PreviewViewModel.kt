// app/src/main/java/com/bedrockconverter/viewmodel/PreviewViewModel.kt
package com.bedrockconverter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bedrockconverter.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the 3D preview screen
 * Manages camera, rendering settings, and preview state
 */
class PreviewViewModel(application: Application) : AndroidViewModel(application) {

    // Preview state
    private val _previewState = MutableStateFlow(PreviewState())
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    // Camera state
    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    // Render settings
    private val _renderSettings = MutableStateFlow(RenderSettings())
    val renderSettings: StateFlow<RenderSettings> = _renderSettings.asStateFlow()

    // Model info visibility
    private val _showModelInfo = MutableStateFlow(false)
    val showModelInfo: StateFlow<Boolean> = _showModelInfo.asStateFlow()

    // Scale selector visibility
    private val _showScaleSelector = MutableStateFlow(false)
    val showScaleSelector: StateFlow<Boolean> = _showScaleSelector.asStateFlow()

    /**
     * Set the model to preview
     */
    fun setModel(model: Model3D) {
        _previewState.update { it.copy(model = model) }
        
        // Auto-frame the model
        model.bounds?.let { bounds ->
            frameModel(bounds)
        }
    }

    /**
     * Update camera rotation from touch input
     */
    fun updateCameraRotation(deltaX: Float, deltaY: Float) {
        _cameraState.update { state ->
            state.copy(
                rotationX = (state.rotationX + deltaY * state.rotationSensitivity).coerceIn(-89f, 89f),
                rotationY = (state.rotationY + deltaX * state.rotationSensitivity) % 360f
            )
        }
    }

    /**
     * Update camera zoom from pinch gesture
     */
    fun updateCameraZoom(scaleFactor: Float) {
        _cameraState.update { state ->
            state.copy(
                zoom = (state.zoom * scaleFactor).coerceIn(state.minZoom, state.maxZoom)
            )
        }
    }

    /**
     * Set camera zoom directly
     */
    fun setCameraZoom(zoom: Float) {
        _cameraState.update { state ->
            state.copy(zoom = zoom.coerceIn(state.minZoom, state.maxZoom))
        }
    }

    /**
     * Update camera pan from drag gesture
     */
    fun updateCameraPan(deltaX: Float, deltaY: Float) {
        _cameraState.update { state ->
            state.copy(
                panX = state.panX + deltaX * state.panSensitivity,
                panY = state.panY - deltaY * state.panSensitivity
            )
        }
    }

    /**
     * Reset camera to default position
     */
    fun resetCamera() {
        _cameraState.update { 
            CameraState().copy(
                // Keep zoom settings but reset position
                minZoom = it.minZoom,
                maxZoom = it.maxZoom
            )
        }

        // Re-frame the model if available
        _previewState.value.model?.bounds?.let { bounds ->
            frameModel(bounds)
        }
    }

    /**
     * Frame the camera to fit the model
     */
    private fun frameModel(bounds: BoundingBox) {
        val maxDimension = maxOf(bounds.width, bounds.height, bounds.depth)
        val distance = maxDimension * 2f

        _cameraState.update { state ->
            state.copy(
                zoom = 1f,
                rotationX = 20f,
                rotationY = 45f,
                panX = 0f,
                panY = 0f,
                targetX = bounds.center.x,
                targetY = bounds.center.y,
                targetZ = bounds.center.z,
                distance = distance
            )
        }
    }

    /**
     * Toggle wireframe mode
     */
    fun toggleWireframe() {
        _renderSettings.update { it.copy(wireframe = !it.wireframe) }
    }

    /**
     * Toggle texture display
     */
    fun toggleTextures() {
        _renderSettings.update { it.copy(showTextures = !it.showTextures) }
    }

    /**
     * Toggle grid display
     */
    fun toggleGrid() {
        _renderSettings.update { it.copy(showGrid = !it.showGrid) }
    }

    /**
     * Toggle normals display
     */
    fun toggleNormals() {
        _renderSettings.update { it.copy(showNormals = !it.showNormals) }
    }

    /**
     * Toggle bounds display
     */
    fun toggleBounds() {
        _renderSettings.update { it.copy(showBounds = !it.showBounds) }
    }

    /**
     * Set background color
     */
    fun setBackgroundColor(color: Int) {
        _renderSettings.update { it.copy(backgroundColor = color) }
    }

    /**
     * Toggle model info panel
     */
    fun toggleModelInfo() {
        _showModelInfo.update { !it }
    }

    /**
     * Show model info panel
     */
    fun showModelInfo() {
        _showModelInfo.value = true
    }

    /**
     * Hide model info panel
     */
    fun hideModelInfo() {
        _showModelInfo.value = false
    }

    /**
     * Toggle scale selector
     */
    fun toggleScaleSelector() {
        _showScaleSelector.update { !it }
    }

    /**
     * Show scale selector
     */
    fun showScaleSelector() {
        _showScaleSelector.value = true
    }

    /**
     * Hide scale selector
     */
    fun hideScaleSelector() {
        _showScaleSelector.value = false
    }

    /**
     * Set lighting mode
     */
    fun setLightingMode(mode: LightingMode) {
        _renderSettings.update { it.copy(lightingMode = mode) }
    }

    /**
     * Set shading mode
     */
    fun setShadingMode(mode: ShadingMode) {
        _renderSettings.update { it.copy(shadingMode = mode) }
    }

    /**
     * Take screenshot
     */
    fun takeScreenshot(onComplete: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            _previewState.update { it.copy(isCapturingScreenshot = true) }
            
            // In a real implementation, capture from GLSurfaceView
            // For now, just signal completion
            _previewState.update { it.copy(isCapturingScreenshot = false) }
            onComplete(null)
        }
    }

    /**
     * Set preview quality
     */
    fun setPreviewQuality(quality: PreviewQuality) {
        _renderSettings.update { it.copy(quality = quality) }
    }

    /**
     * Get model statistics
     */
    fun getModelStats(): ModelStats? {
        val model = _previewState.value.model ?: return null

        return ModelStats(
            name = model.name,
            format = model.format,
            vertexCount = model.vertexCount,
            triangleCount = model.triangleCount,
            meshCount = model.geometry.meshes.size,
            textureCount = model.textures.size,
            materialCount = model.materials.size,
            boneCount = model.geometry.bones.size,
            hasNormals = model.geometry.meshes.any { it.hasNormals },
            hasUvs = model.geometry.meshes.any { it.hasUvs },
            bounds = model.bounds
        )
    }

    /**
     * Auto-rotate camera
     */
    private var autoRotateEnabled = false

    fun toggleAutoRotate() {
        autoRotateEnabled = !autoRotateEnabled
        _previewState.update { it.copy(isAutoRotating = autoRotateEnabled) }

        if (autoRotateEnabled) {
            startAutoRotate()
        }
    }

    private fun startAutoRotate() {
        viewModelScope.launch {
            while (autoRotateEnabled) {
                _cameraState.update { state ->
                    state.copy(rotationY = (state.rotationY + 0.5f) % 360f)
                }
                kotlinx.coroutines.delay(16) // ~60 FPS
            }
        }
    }

    fun stopAutoRotate() {
        autoRotateEnabled = false
        _previewState.update { it.copy(isAutoRotating = false) }
    }

    /**
     * Set current view preset
     */
    fun setViewPreset(preset: ViewPreset) {
        _cameraState.update { state ->
            when (preset) {
                ViewPreset.FRONT -> state.copy(rotationX = 0f, rotationY = 0f)
                ViewPreset.BACK -> state.copy(rotationX = 0f, rotationY = 180f)
                ViewPreset.LEFT -> state.copy(rotationX = 0f, rotationY = 90f)
                ViewPreset.RIGHT -> state.copy(rotationX = 0f, rotationY = -90f)
                ViewPreset.TOP -> state.copy(rotationX = 89f, rotationY = 0f)
                ViewPreset.BOTTOM -> state.copy(rotationX = -89f, rotationY = 0f)
                ViewPreset.ISOMETRIC -> state.copy(rotationX = 35f, rotationY = 45f)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRotateEnabled = false
    }
}

/**
 * Preview state
 */
data class PreviewState(
    val model: Model3D? = null,
    val isLoading: Boolean = false,
    val isAutoRotating: Boolean = false,
    val isCapturingScreenshot: Boolean = false,
    val error: String? = null
)

/**
 * Camera state
 */
data class CameraState(
    val rotationX: Float = 20f,
    val rotationY: Float = 45f,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val targetX: Float = 0f,
    val targetY: Float = 0f,
    val targetZ: Float = 0f,
    val distance: Float = 5f,
    val minZoom: Float = 0.1f,
    val maxZoom: Float = 10f,
    val rotationSensitivity: Float = 0.5f,
    val panSensitivity: Float = 0.01f
)

/**
 * Render settings
 */
data class RenderSettings(
    val wireframe: Boolean = false,
    val showTextures: Boolean = true,
    val showGrid: Boolean = true,
    val showNormals: Boolean = false,
    val showBounds: Boolean = false,
    val backgroundColor: Int = 0xFF1A1A2E.toInt(),
    val lightingMode: LightingMode = LightingMode.STANDARD,
    val shadingMode: ShadingMode = ShadingMode.SMOOTH,
    val quality: PreviewQuality = PreviewQuality.HIGH
)

/**
 * Lighting modes
 */
enum class LightingMode {
    UNLIT,
    STANDARD,
    STUDIO,
    OUTDOOR
}

/**
 * Shading modes
 */
enum class ShadingMode {
    FLAT,
    SMOOTH,
    MATCAP
}

/**
 * Preview quality levels
 */
enum class PreviewQuality {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * View presets
 */
enum class ViewPreset {
    FRONT,
    BACK,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    ISOMETRIC
}

/**
 * Model statistics
 */
data class ModelStats(
    val name: String,
    val format: String,
    val vertexCount: Int,
    val triangleCount: Int,
    val meshCount: Int,
    val textureCount: Int,
    val materialCount: Int,
    val boneCount: Int,
    val hasNormals: Boolean,
    val hasUvs: Boolean,
    val bounds: BoundingBox?
) {
    val formattedVertexCount: String
        get() = formatNumber(vertexCount)

    val formattedTriangleCount: String
        get() = formatNumber(triangleCount)

    val boundsSize: String
        get() = bounds?.let {
            "%.2f x %.2f x %.2f".format(it.width, it.height, it.depth)
        } ?: "Unknown"

    private fun formatNumber(n: Int): String {
        return when {
            n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
            n >= 1_000 -> "%.1fK".format(n / 1_000f)
            else -> n.toString()
        }
    }
}