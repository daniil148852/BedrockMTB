// app/src/main/java/com/bedrockconverter/model/ExportSettings.kt
package com.bedrockconverter.model

/**
 * Settings for exporting a model to Bedrock format
 */
data class ExportSettings(
    val entityName: String = "custom_model",
    val namespace: String = "custom",
    val scale: Float = 1f,
    val generateCollision: Boolean = true,
    val optimizeGeometry: Boolean = true,
    val textureSize: TextureSize = TextureSize.ORIGINAL,
    val geometryFormat: GeometryFormat = GeometryFormat.CUBES,
    val includeSpawnEgg: Boolean = true,
    val spawnEggBaseColor: String = "#4CAF50",
    val spawnEggOverlayColor: String = "#388E3C",
    val enablePhysics: Boolean = true,
    val enableGravity: Boolean = true,
    val collisionWidth: Float? = null,  // null = auto calculate
    val collisionHeight: Float? = null, // null = auto calculate
    val packName: String? = null,       // null = use entity name
    val packDescription: String? = null // null = auto generate
) {
    val fullIdentifier: String
        get() = "$namespace:$entityName"

    val geometryIdentifier: String
        get() = "geometry.$namespace.$entityName"

    val renderControllerIdentifier: String
        get() = "controller.render.$namespace.$entityName"

    val actualPackName: String
        get() = packName ?: "${entityName.replaceFirstChar { it.uppercase() }} Addon"

    val actualPackDescription: String
        get() = packDescription ?: "Custom entity addon for $entityName"

    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()

        if (entityName.isBlank()) {
            errors.add("Entity name cannot be empty")
        }

        if (!entityName.matches(Regex("^[a-z][a-z0-9_]*$"))) {
            errors.add("Entity name must start with a letter and contain only lowercase letters, numbers, and underscores")
        }

        if (namespace.isBlank()) {
            errors.add("Namespace cannot be empty")
        }

        if (!namespace.matches(Regex("^[a-z][a-z0-9_]*$"))) {
            errors.add("Namespace must start with a letter and contain only lowercase letters, numbers, and underscores")
        }

        if (scale <= 0) {
            errors.add("Scale must be greater than 0")
        }

        if (scale > 10) {
            errors.add("Scale cannot exceed 10")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    companion object {
        val DEFAULT = ExportSettings()

        fun fromModelName(modelName: String): ExportSettings {
            val sanitized = modelName
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .replace(Regex("^_+|_+$"), "")
                .take(32)
                .ifEmpty { "custom_model" }

            return ExportSettings(entityName = sanitized)
        }
    }
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()

    val isValid: Boolean get() = this is Valid
}

/**
 * Texture size options for optimization
 */
enum class TextureSize(val maxSize: Int, val displayName: String) {
    ORIGINAL(0, "Original"),
    SIZE_32(32, "32x32"),
    SIZE_64(64, "64x64"),
    SIZE_128(128, "128x128"),
    SIZE_256(256, "256x256"),
    SIZE_512(512, "512x512")
}

/**
 * Geometry format options
 */
enum class GeometryFormat(val displayName: String, val description: String) {
    CUBES("Cubes", "Convert to Minecraft-style cubes (recommended)"),
    MESH("Mesh", "Keep original mesh data (may not render correctly)"),
    VOXEL("Voxel", "Convert to voxel representation")
}

/**
 * Export result data
 */
data class ExportResult(
    val success: Boolean,
    val filePath: String? = null,
    val entityIdentifier: String? = null,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
    val stats: ExportStats? = null
)

/**
 * Statistics about the export
 */
data class ExportStats(
    val originalVertices: Int,
    val exportedCubes: Int,
    val textureCount: Int,
    val totalFileSize: Long,
    val exportDurationMs: Long
)

/**
 * Progress callback for export operation
 */
data class ExportProgress(
    val progress: Float, // 0.0 to 1.0
    val step: ExportStep,
    val message: String
)

enum class ExportStep(val displayName: String) {
    PREPARING("Preparing model..."),
    CONVERTING_GEOMETRY("Converting geometry..."),
    PROCESSING_TEXTURES("Processing textures..."),
    GENERATING_ENTITY("Generating entity files..."),
    GENERATING_MANIFEST("Creating manifest..."),
    PACKAGING("Packaging addon..."),
    FINALIZING("Finalizing..."),
    COMPLETE("Complete!")
}