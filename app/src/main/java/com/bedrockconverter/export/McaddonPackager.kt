// app/src/main/java/com/bedrockconverter/export/McaddonPackager.kt
package com.bedrockconverter.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packages resource and behavior packs into .mcaddon format
 * .mcaddon is essentially a ZIP file containing both packs
 */
class McaddonPackager(private val context: Context) {

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val MCADDON_EXTENSION = ".mcaddon"
        private const val MCPACK_EXTENSION = ".mcpack"
    }

    /**
     * Package working directory into .mcaddon file
     */
    fun createPackage(workDir: File, outputName: String): File {
        val outputDir = getOutputDirectory()
        val mcaddonFile = File(outputDir, "${sanitizeFileName(outputName)}$MCADDON_EXTENSION")

        // Delete existing file if present
        if (mcaddonFile.exists()) {
            mcaddonFile.delete()
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(mcaddonFile))).use { zos ->
            // Add resource pack
            val resourcePackDir = File(workDir, "resource_pack")
            if (resourcePackDir.exists()) {
                addDirectoryToZip(zos, resourcePackDir, "resource_pack")
            }

            // Add behavior pack
            val behaviorPackDir = File(workDir, "behavior_pack")
            if (behaviorPackDir.exists()) {
                addDirectoryToZip(zos, behaviorPackDir, "behavior_pack")
            }
        }

        return mcaddonFile
    }

    /**
     * Package a single pack into .mcpack file
     */
    fun packageSinglePack(packDir: File, outputName: String, isResourcePack: Boolean): File {
        val outputDir = getOutputDirectory()
        val suffix = if (isResourcePack) "_rp" else "_bp"
        val mcpackFile = File(outputDir, "${sanitizeFileName(outputName)}$suffix$MCPACK_EXTENSION")

        if (mcpackFile.exists()) {
            mcpackFile.delete()
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(mcpackFile))).use { zos ->
            addDirectoryToZip(zos, packDir, "")
        }

        return mcpackFile
    }

    /**
     * Add a directory recursively to a ZIP file
     */
    private fun addDirectoryToZip(zos: ZipOutputStream, sourceDir: File, basePath: String) {
        val files = sourceDir.listFiles() ?: return

        for (file in files) {
            val entryPath = if (basePath.isEmpty()) {
                file.name
            } else {
                "$basePath/${file.name}"
            }

            if (file.isDirectory) {
                // Add directory entry
                zos.putNextEntry(ZipEntry("$entryPath/"))
                zos.closeEntry()

                // Recursively add contents
                addDirectoryToZip(zos, file, entryPath)
            } else {
                // Add file entry
                zos.putNextEntry(ZipEntry(entryPath))

                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var length: Int
                    while (fis.read(buffer).also { length = it } > 0) {
                        zos.write(buffer, 0, length)
                    }
                }

                zos.closeEntry()
            }
        }
    }

    /**
     * Get the output directory for exported files
     */
    private fun getOutputDirectory(): File {
        val outputDir = File(context.getExternalFilesDir(null), "exports")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return outputDir
    }

    /**
     * Sanitize filename to remove invalid characters
     */
    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(50)
            .ifEmpty { "model" }
    }

    /**
     * Get URI for sharing the mcaddon file
     */
    fun getShareableUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Create share intent for mcaddon file
     */
    fun createShareIntent(file: File): Intent {
        val uri = getShareableUri(file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Minecraft Bedrock Addon")
            putExtra(Intent.EXTRA_TEXT, "Install this addon in Minecraft Bedrock Edition")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Create intent to open mcaddon directly with Minecraft
     */
    fun createOpenWithMinecraftIntent(file: File): Intent {
        val uri = getShareableUri(file)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Get list of exported files
     */
    fun getExportedFiles(): List<ExportedFile> {
        val outputDir = getOutputDirectory()
        val files = outputDir.listFiles { file ->
            file.extension == "mcaddon" || file.extension == "mcpack"
        } ?: return emptyList()

        return files.map { file ->
            ExportedFile(
                name = file.nameWithoutExtension,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                type = if (file.extension == "mcaddon") ExportType.MCADDON else ExportType.MCPACK
            )
        }.sortedByDescending { it.lastModified }
    }

    /**
     * Delete an exported file
     */
    fun deleteExportedFile(path: String): Boolean {
        val file = File(path)
        return if (file.exists() && file.parentFile == getOutputDirectory()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Clear all exported files
     */
    fun clearAllExports(): Int {
        val outputDir = getOutputDirectory()
        val files = outputDir.listFiles() ?: return 0

        var deletedCount = 0
        for (file in files) {
            if (file.delete()) {
                deletedCount++
            }
        }

        return deletedCount
    }

    /**
     * Get total size of exported files
     */
    fun getTotalExportsSize(): Long {
        val outputDir = getOutputDirectory()
        val files = outputDir.listFiles() ?: return 0
        return files.sumOf { it.length() }
    }

    /**
     * Validate mcaddon file structure
     */
    fun validateMcaddon(file: File): ValidationResult {
        if (!file.exists()) {
            return ValidationResult(false, listOf("File does not exist"))
        }

        if (file.extension != "mcaddon") {
            return ValidationResult(false, listOf("Invalid file extension"))
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries().toList()
                val entryNames = entries.map { it.name }

                // Check for resource pack
                val hasResourcePack = entryNames.any { it.startsWith("resource_pack/") }
                val hasBehaviorPack = entryNames.any { it.startsWith("behavior_pack/") }

                if (!hasResourcePack && !hasBehaviorPack) {
                    errors.add("No valid pack found in mcaddon")
                }

                // Check for manifests
                val hasRpManifest = entryNames.any { 
                    it == "resource_pack/manifest.json" || it == "manifest.json" 
                }
                val hasBpManifest = entryNames.any { 
                    it == "behavior_pack/manifest.json" 
                }

                if (hasResourcePack && !hasRpManifest) {
                    warnings.add("Resource pack missing manifest.json")
                }

                if (hasBehaviorPack && !hasBpManifest) {
                    warnings.add("Behavior pack missing manifest.json")
                }
            }
        } catch (e: Exception) {
            errors.add("Invalid ZIP file: ${e.message}")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}

/**
 * Information about an exported file
 */
data class ExportedFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val type: ExportType
) {
    val formattedSize: String
        get() {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "${size / (1024 * 1024)} MB"
            }
        }
}

enum class ExportType {
    MCADDON,
    MCPACK
}

/**
 * Result of validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)