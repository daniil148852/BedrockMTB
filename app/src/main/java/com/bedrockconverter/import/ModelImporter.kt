// app/src/main/java/com/bedrockconverter/import/ModelImporter.kt
package com.bedrockconverter.import

import android.content.Context
import android.net.Uri
import com.bedrockconverter.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Main entry point for importing 3D models
 * Delegates to format-specific importers
 */
class ModelImporter(private val context: Context) {

    private val gltfImporter = GltfImporter(context)
    private val objImporter = ObjImporter(context)
    private val fbxImporter = FbxImporter(context)
    private val textureExtractor = TextureExtractor(context)

    /**
     * Import a 3D model from URI
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(uri)
            val format = detectFormat(fileName, uri)

            if (format == null) {
                return@withContext ImportResult.Error(
                    "Unsupported file format. Supported formats: GLB, GLTF, OBJ, FBX"
                )
            }

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("Cannot open file")

            val tempFile = createTempFile(fileName, inputStream)
            inputStream.close()

            val model = when (format) {
                ModelFormat.GLB -> gltfImporter.importGlb(tempFile, fileName)
                ModelFormat.GLTF -> gltfImporter.importGltf(tempFile, fileName)
                ModelFormat.OBJ -> objImporter.import(tempFile, fileName)
                ModelFormat.FBX -> fbxImporter.import(tempFile, fileName)
            }

            // Clean up temp file
            tempFile.delete()

            if (model != null) {
                ImportResult.Success(model.copy(sourceUri = uri))
            } else {
                ImportResult.Error("Failed to parse model file")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult.Error("Import failed: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Import with progress callback
     */
    suspend fun importWithProgress(
        uri: Uri,
        onProgress: (ImportProgress) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            onProgress(ImportProgress(0.1f, "Reading file..."))

            val fileName = getFileName(uri)
            val format = detectFormat(fileName, uri)

            if (format == null) {
                return@withContext ImportResult.Error(
                    "Unsupported file format. Supported formats: GLB, GLTF, OBJ, FBX"
                )
            }

            onProgress(ImportProgress(0.2f, "Detected format: ${format.name}"))

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("Cannot open file")

            val tempFile = createTempFile(fileName, inputStream)
            inputStream.close()

            onProgress(ImportProgress(0.3f, "Parsing geometry..."))

            val model = when (format) {
                ModelFormat.GLB -> {
                    onProgress(ImportProgress(0.4f, "Processing GLB..."))
                    gltfImporter.importGlb(tempFile, fileName)
                }
                ModelFormat.GLTF -> {
                    onProgress(ImportProgress(0.4f, "Processing GLTF..."))
                    gltfImporter.importGltf(tempFile, fileName)
                }
                ModelFormat.OBJ -> {
                    onProgress(ImportProgress(0.4f, "Processing OBJ..."))
                    objImporter.import(tempFile, fileName)
                }
                ModelFormat.FBX -> {
                    onProgress(ImportProgress(0.4f, "Processing FBX..."))
                    fbxImporter.import(tempFile, fileName)
                }
            }

            onProgress(ImportProgress(0.7f, "Extracting textures..."))

            // Extract and process textures if needed
            val modelWithTextures = model?.let { m ->
                if (m.textures.isEmpty() && format == ModelFormat.GLTF) {
                    // Try to extract textures from accompanying files
                    val extractedTextures = textureExtractor.extractFromDirectory(tempFile.parentFile)
                    m.copy(textures = extractedTextures)
                } else {
                    m
                }
            }

            onProgress(ImportProgress(0.9f, "Calculating bounds..."))

            // Clean up temp file
            tempFile.delete()

            onProgress(ImportProgress(1.0f, "Complete!"))

            if (modelWithTextures != null) {
                ImportResult.Success(modelWithTextures.copy(sourceUri = uri))
            } else {
                ImportResult.Error("Failed to parse model file")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult.Error("Import failed: ${e.message ?: "Unknown error"}")
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "model"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: "model"
                }
            }
        }

        return name
    }

    private fun detectFormat(fileName: String, uri: Uri): ModelFormat? {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when (extension) {
            "glb" -> ModelFormat.GLB
            "gltf" -> ModelFormat.GLTF
            "obj" -> ModelFormat.OBJ
            "fbx" -> ModelFormat.FBX
            else -> {
                // Try to detect from MIME type
                val mimeType = context.contentResolver.getType(uri)
                when {
                    mimeType?.contains("gltf") == true -> ModelFormat.GLTF
                    mimeType?.contains("obj") == true -> ModelFormat.OBJ
                    mimeType?.contains("fbx") == true -> ModelFormat.FBX
                    else -> null
                }
            }
        }
    }

    private fun createTempFile(fileName: String, inputStream: InputStream): File {
        val tempDir = File(context.cacheDir, "model_import")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val tempFile = File(tempDir, fileName)
        tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }

        return tempFile
    }

    /**
     * Validate if a model can be imported
     */
    fun canImport(uri: Uri): Boolean {
        val fileName = getFileName(uri)
        return detectFormat(fileName, uri) != null
    }

    /**
     * Get supported formats info
     */
    fun getSupportedFormats(): List<FormatInfo> {
        return listOf(
            FormatInfo(
                extension = "glb",
                name = "GLB (Binary glTF)",
                description = "Recommended format. Contains geometry and textures in single file.",
                isRecommended = true
            ),
            FormatInfo(
                extension = "gltf",
                name = "glTF",
                description = "JSON-based format. May require separate texture files.",
                isRecommended = false
            ),
            FormatInfo(
                extension = "obj",
                name = "Wavefront OBJ",
                description = "Simple format. Textures defined in MTL file.",
                isRecommended = false
            ),
            FormatInfo(
                extension = "fbx",
                name = "Autodesk FBX",
                description = "Complex format with animation support. Basic support only.",
                isRecommended = false
            )
        )
    }
}

enum class ModelFormat {
    GLB,
    GLTF,
    OBJ,
    FBX
}

data class FormatInfo(
    val extension: String,
    val name: String,
    val description: String,
    val isRecommended: Boolean
)

sealed class ImportResult {
    data class Success(val model: Model3D) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

data class ImportProgress(
    val progress: Float,
    val message: String
)