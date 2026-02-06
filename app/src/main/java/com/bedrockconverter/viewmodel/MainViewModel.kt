// app/src/main/java/com/bedrockconverter/viewmodel/MainViewModel.kt
package com.bedrockconverter.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bedrockconverter.export.BedrockExporter
import com.bedrockconverter.import.ImportProgress
import com.bedrockconverter.import.ImportResult
import com.bedrockconverter.import.ModelImporter
import com.bedrockconverter.model.*
import com.bedrockconverter.ui.screens.AppSettings
import com.bedrockconverter.ui.screens.ExportState
import com.bedrockconverter.utils.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main ViewModel for the application
 * Manages app state, model import/export, and navigation
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val modelImporter = ModelImporter(context)
    private val bedrockExporter = BedrockExporter(context)

    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Current model
    private val _currentModel = MutableStateFlow<Model3D?>(null)
    val currentModel: StateFlow<Model3D?> = _currentModel.asStateFlow()

    // Recent models
    private val _recentModels = MutableStateFlow<List<Model3D>>(emptyList())
    val recentModels: StateFlow<List<Model3D>> = _recentModels.asStateFlow()

    // Export settings
    private val _exportSettings = MutableStateFlow(ExportSettings.DEFAULT)
    val exportSettings: StateFlow<ExportSettings> = _exportSettings.asStateFlow()

    // Export state
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    // App settings
    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    // Navigation
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    // Current scale for preview
    private val _currentScale = MutableStateFlow(1f)
    val currentScale: StateFlow<Float> = _currentScale.asStateFlow()

    init {
        loadAppSettings()
        loadRecentModels()
    }

    /**
     * Import a 3D model from URI
     */
    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            modelImporter.importWithProgress(uri) { progress ->
                _uiState.update { 
                    it.copy(
                        importProgress = progress.progress,
                        importMessage = progress.message
                    )
                }
            }.let { result ->
                when (result) {
                    is ImportResult.Success -> {
                        val model = result.model
                        _currentModel.value = model
                        
                        // Update export settings with model name
                        _exportSettings.update { 
                            ExportSettings.fromModelName(model.name).copy(
                                scale = _appSettings.value.defaultScale,
                                namespace = _appSettings.value.defaultNamespace
                            )
                        }
                        
                        // Add to recent models
                        addToRecentModels(model)
                        
                        // Navigate to preview
                        _navigationEvent.value = NavigationEvent.NavigateToPreview
                        
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                importProgress = 0f,
                                importMessage = null
                            )
                        }
                    }
                    is ImportResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = result.message,
                                importProgress = 0f,
                                importMessage = null
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Select a recent model
     */
    fun selectModel(model: Model3D) {
        _currentModel.value = model
        _exportSettings.update { 
            ExportSettings.fromModelName(model.name).copy(
                scale = _appSettings.value.defaultScale,
                namespace = _appSettings.value.defaultNamespace
            )
        }
        _navigationEvent.value = NavigationEvent.NavigateToPreview
    }

    /**
     * Delete a model from recent list
     */
    fun deleteModel(model: Model3D) {
        _recentModels.update { models ->
            models.filter { it.id != model.id }
        }
        saveRecentModels()

        // If current model was deleted, clear it
        if (_currentModel.value?.id == model.id) {
            _currentModel.value = null
        }
    }

    /**
     * Update export settings
     */
    fun updateExportSettings(settings: ExportSettings) {
        _exportSettings.value = settings
        _currentScale.value = settings.scale
    }

    /**
     * Update scale
     */
    fun updateScale(scale: Float) {
        _currentScale.value = scale
        _exportSettings.update { it.copy(scale = scale) }
    }

    /**
     * Export current model to Bedrock format
     */
    fun exportModel() {
        val model = _currentModel.value ?: return
        val settings = _exportSettings.value

        viewModelScope.launch {
            _exportState.value = ExportState.Exporting(0f, "Preparing...")

            val result = bedrockExporter.export(model, settings) { progress ->
                _exportState.value = ExportState.Exporting(
                    progress = progress.progress,
                    currentStep = progress.message
                )
            }

            _exportState.value = if (result.success && result.filePath != null) {
                ExportState.Success(
                    filePath = result.filePath,
                    entityIdentifier = result.entityIdentifier ?: settings.fullIdentifier
                )
            } else {
                ExportState.Error(result.error ?: "Unknown error occurred")
            }
        }
    }

    /**
     * Reset export state
     */
    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    /**
     * Share exported file
     */
    fun shareExportedFile(filePath: String): Intent {
        val file = File(filePath)
        return com.bedrockconverter.export.McaddonPackager(context)
            .createShareIntent(file)
    }

    /**
     * Open exported file with Minecraft
     */
    fun openWithMinecraft(filePath: String): Intent {
        val file = File(filePath)
        return com.bedrockconverter.export.McaddonPackager(context)
            .createOpenWithMinecraftIntent(file)
    }

    /**
     * Update app settings
     */
    fun updateAppSettings(settings: AppSettings) {
        _appSettings.value = settings
        saveAppSettings()
    }

    /**
     * Clear cache
     */
    fun clearCache() {
        viewModelScope.launch {
            val clearedBytes = FileUtils.clearCache(context)
            _uiState.update { 
                it.copy(
                    message = "Cleared ${FileUtils.formatFileSize(clearedBytes)}"
                )
            }
        }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clear message
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Consume navigation event
     */
    fun onNavigationEventConsumed() {
        _navigationEvent.value = null
    }

    /**
     * Navigate to settings
     */
    fun navigateToSettings() {
        _navigationEvent.value = NavigationEvent.NavigateToSettings
    }

    /**
     * Navigate to export
     */
    fun navigateToExport() {
        _navigationEvent.value = NavigationEvent.NavigateToExport
    }

    /**
     * Navigate back
     */
    fun navigateBack() {
        _navigationEvent.value = NavigationEvent.NavigateBack
    }

    /**
     * Navigate to home
     */
    fun navigateToHome() {
        _currentModel.value = null
        _exportState.value = ExportState.Idle
        _navigationEvent.value = NavigationEvent.NavigateToHome
    }

    // Private helper methods

    private fun addToRecentModels(model: Model3D) {
        _recentModels.update { models ->
            val filtered = models.filter { it.id != model.id }
            listOf(model) + filtered.take(9) // Keep max 10 recent models
        }
        saveRecentModels()
    }

    private fun loadRecentModels() {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("recent_models", android.content.Context.MODE_PRIVATE)
                val json = prefs.getString("models", null)
                
                if (json != null) {
                    // In a real implementation, deserialize the models
                    // For now, we just keep an in-memory list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveRecentModels() {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("recent_models", android.content.Context.MODE_PRIVATE)
                // In a real implementation, serialize the models to JSON
                // For now, we just keep an in-memory list
                prefs.edit().apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadAppSettings() {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                
                _appSettings.value = AppSettings(
                    defaultScale = prefs.getFloat("default_scale", 1f),
                    defaultNamespace = prefs.getString("default_namespace", "custom") ?: "custom",
                    autoOptimize = prefs.getBoolean("auto_optimize", true),
                    darkTheme = prefs.getBoolean("dark_theme", true),
                    showAdvancedOptions = prefs.getBoolean("show_advanced", false)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveAppSettings() {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                val settings = _appSettings.value
                
                prefs.edit()
                    .putFloat("default_scale", settings.defaultScale)
                    .putString("default_namespace", settings.defaultNamespace)
                    .putBoolean("auto_optimize", settings.autoOptimize)
                    .putBoolean("dark_theme", settings.darkTheme)
                    .putBoolean("show_advanced", settings.showAdvancedOptions)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Get supported file formats info
     */
    fun getSupportedFormats() = modelImporter.getSupportedFormats()

    /**
     * Check if a URI can be imported
     */
    fun canImport(uri: Uri) = modelImporter.canImport(uri)

    /**
     * Get estimated export size
     */
    fun getEstimatedExportSize(): Long {
        val model = _currentModel.value ?: return 0
        val settings = _exportSettings.value
        return bedrockExporter.estimateExportSize(model, settings)
    }
}

/**
 * UI State for main screen
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val importProgress: Float = 0f,
    val importMessage: String? = null
)

/**
 * Navigation events
 */
sealed class NavigationEvent {
    object NavigateToPreview : NavigationEvent()
    object NavigateToExport : NavigationEvent()
    object NavigateToSettings : NavigationEvent()
    object NavigateToHome : NavigationEvent()
    object NavigateBack : NavigationEvent()
}