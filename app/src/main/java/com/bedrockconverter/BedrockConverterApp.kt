// app/src/main/java/com/bedrockconverter/BedrockConverterApp.kt
package com.bedrockconverter

import android.app.Application
import android.util.Log

/**
 * Application class for Bedrock Converter
 * Handles app-level initialization
 */
class BedrockConverterApp : Application() {

    companion object {
        private const val TAG = "BedrockConverterApp"

        @Volatile
        private var instance: BedrockConverterApp? = null

        fun getInstance(): BedrockConverterApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "Bedrock Converter initialized")

        // Initialize any required components
        initializeApp()
    }

    private fun initializeApp() {
        // Create cache directories
        createCacheDirectories()

        // Set up uncaught exception handler
        setupExceptionHandler()
    }

    private fun createCacheDirectories() {
        try {
            // Temp directory for imports
            val tempDir = java.io.File(cacheDir, "temp")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }

            // Export directory
            val exportDir = java.io.File(getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            // Model cache directory
            val modelCacheDir = java.io.File(cacheDir, "models")
            if (!modelCacheDir.exists()) {
                modelCacheDir.mkdirs()
            }

            Log.d(TAG, "Cache directories created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create cache directories", e)
        }
    }

    private fun setupExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)

            // Clean up temp files on crash
            try {
                val tempDir = java.io.File(cacheDir, "temp")
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }

            // Call default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Get the temp directory for file operations
     */
    fun getTempDirectory(): java.io.File {
        val tempDir = java.io.File(cacheDir, "temp")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return tempDir
    }

    /**
     * Get the export directory
     */
    fun getExportDirectory(): java.io.File {
        val exportDir = java.io.File(getExternalFilesDir(null), "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }

    /**
     * Get the model cache directory
     */
    fun getModelCacheDirectory(): java.io.File {
        val cacheDir = java.io.File(cacheDir, "models")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    /**
     * Clear all temporary files
     */
    fun clearTempFiles() {
        try {
            getTempDirectory().deleteRecursively()
            getTempDirectory().mkdirs()
            Log.d(TAG, "Temp files cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear temp files", e)
        }
    }

    /**
     * Get total cache size
     */
    fun getCacheSize(): Long {
        var size = 0L

        try {
            size += getDirectorySize(cacheDir)
            externalCacheDir?.let { size += getDirectorySize(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate cache size", e)
        }

        return size
    }

    private fun getDirectorySize(directory: java.io.File): Long {
        var size = 0L

        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                getDirectorySize(file)
            } else {
                file.length()
            }
        }

        return size
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning - clearing temp files")
        clearTempFiles()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "Memory pressure - clearing temp files")
                clearTempFiles()
            }
        }
    }
}