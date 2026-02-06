// app/src/main/java/com/bedrockconverter/export/BedrockExporter.kt
package com.bedrockconverter.export

import android.content.Context
import com.bedrockconverter.model.*
import com.bedrockconverter.utils.CoordinateConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Main exporter that orchestrates the conversion process
 * Converts 3D models to Minecraft Bedrock addon format
 */
class BedrockExporter(private val context: Context) {

    private val geometryGenerator = GeometryGenerator()
    private val entityGenerator = EntityGenerator()
    private val manifestGenerator = ManifestGenerator()
    private val mcaddonPackager = McaddonPackager(context)
    private val coordinateConverter = CoordinateConverter()

    /**
     * Export model to .mcaddon file
     */
    suspend fun export(
        model: Model3D,
        settings: ExportSettings,
        onProgress: (ExportProgress) -> Unit = {}
    ): ExportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // Validate settings
            val validation = settings.validate()
            if (validation is ValidationResult.Invalid) {
                return@withContext ExportResult(
                    success = false,
                    error = validation.errors.joinToString("\n")
                )
            }

            onProgress(ExportProgress(0.05f, ExportStep.PREPARING, "Preparing model..."))

            // Create working directory
            val workDir = createWorkingDirectory(settings.entityName)
            val warnings = mutableListOf<String>()

            onProgress(ExportProgress(0.1f, ExportStep.CONVERTING_GEOMETRY, "Converting coordinate system..."))

            // Convert coordinate system (Y-up to Minecraft's coordinate system)
            val convertedModel = coordinateConverter.convertToMinecraft(model)

            // Apply scale
            val scaledGeometry = convertedModel.geometry.scaled(settings.scale)

            onProgress(ExportProgress(0.2f, ExportStep.CONVERTING_GEOMETRY, "Generating Bedrock geometry..."))

            // Generate Bedrock geometry
            val bedrockGeometry = geometryGenerator.generate(
                geometry = scaledGeometry,
                identifier = settings.geometryIdentifier,
                textureWidth = getTextureWidth(convertedModel),
                textureHeight = getTextureHeight(convertedModel),
                bounds = convertedModel.bounds?.scaled(settings.scale)
            )

            onProgress(ExportProgress(0.4f, ExportStep.PROCESSING_TEXTURES, "Processing textures..."))

            // Process textures
            val processedTextures = processTextures(convertedModel.textures, settings)
            if (processedTextures.isEmpty() && convertedModel.textures.isNotEmpty()) {
                warnings.add("Some textures could not be processed")
            }

            onProgress(ExportProgress(0.5f, ExportStep.GENERATING_ENTITY, "Generating entity files..."))

            // Calculate collision box
            val collisionWidth = settings.collisionWidth
                ?: (convertedModel.bounds?.width?.times(settings.scale) ?: 1f)
            val collisionHeight = settings.collisionHeight
                ?: (convertedModel.bounds?.height?.times(settings.scale) ?: 1f)

            // Generate entity definitions
            val entityData = entityGenerator.generate(
                settings = settings,
                collisionWidth = collisionWidth.coerceIn(0.1f, 10f),
                collisionHeight = collisionHeight.coerceIn(0.1f, 10f)
            )

            onProgress(ExportProgress(0.6f, ExportStep.GENERATING_MANIFEST, "Creating manifest files..."))

            // Generate manifests
            val manifests = manifestGenerator.generate(settings)

            onProgress(ExportProgress(0.7f, ExportStep.PACKAGING, "Building addon structure..."))

            // Build addon structure
            val addon = BedrockAddon(
                name = settings.actualPackName,
                description = settings.actualPackDescription,
                resourcePack = ResourcePack(
                    uuid = manifests.resourcePackUuid,
                    manifest = manifests.resourcePackManifest,
                    entity = entityData.clientEntity,
                    geometry = bedrockGeometry,
                    renderController = entityData.renderController,
                    textures = processedTextures
                ),
                behaviorPack = BehaviorPack(
                    uuid = manifests.behaviorPackUuid,
                    manifest = manifests.behaviorPackManifest,
                    entity = entityData.serverEntity
                )
            )

            onProgress(ExportProgress(0.8f, ExportStep.PACKAGING, "Writing files..."))

            // Write all files to working directory
            writeAddonFiles(workDir, addon, bedrockGeometry, entityData, manifests, processedTextures)

            onProgress(ExportProgress(0.9f, ExportStep.PACKAGING, "Creating .mcaddon package..."))

            // Package into .mcaddon
            val mcaddonFile = mcaddonPackager.createPackage(
                workDir = workDir,
                outputName = settings.entityName
            )

            onProgress(ExportProgress(0.95f, ExportStep.FINALIZING, "Cleaning up..."))

            // Clean up working directory
            workDir.deleteRecursively()

            val duration = System.currentTimeMillis() - startTime

            onProgress(ExportProgress(1.0f, ExportStep.COMPLETE, "Export complete!"))

            ExportResult(
                success = true,
                filePath = mcaddonFile.absolutePath,
                entityIdentifier = settings.fullIdentifier,
                warnings = warnings,
                stats = ExportStats(
                    originalVertices = model.vertexCount,
                    exportedCubes = bedrockGeometry.bones.sumOf { it.cubes.size },
                    textureCount = processedTextures.size,
                    totalFileSize = mcaddonFile.length(),
                    exportDurationMs = duration
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult(
                success = false,
                error = "Export failed: ${e.message ?: "Unknown error"}"
            )
        }
    }

    private fun createWorkingDirectory(name: String): File {
        // Use clean string concatenation to avoid issues
        val dirName = "export_" + name + "_" + System.currentTimeMillis()
        val workDir = File(context.cacheDir, dirName)
        workDir.mkdirs()
        return workDir
    }

    private fun getTextureWidth(model: Model3D): Int {
        return model.textures.firstOrNull()?.width ?: 64
    }

    private fun getTextureHeight(model: Model3D): Int {
        return model.textures.firstOrNull()?.height ?: 64
    }

    private fun processTextures(textures: List<Texture>, settings: ExportSettings): List<Texture> {
        if (textures.isEmpty()) {
            // Create default texture
            return listOf(createDefaultTexture())
        }

        return textures.mapNotNull { texture ->
            try {
                var processed = texture

                // Resize if needed
                if (settings.textureSize != TextureSize.ORIGINAL) {
                    processed = resizeTexture(processed, settings.textureSize.maxSize)
                }

                processed
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun createDefaultTexture(): Texture {
        // Create a simple 64x64 white texture
        val size = 64
        val pixels = ByteArray(size * size * 4) { 0xFF.toByte() }

        return Texture(
            name = "default",
            width = size,
            height = size,
            data = createPngFromRgba(pixels, size, size),
            format = TextureFormat.PNG,
            type = TextureType.DIFFUSE
        )
    }

    private fun createPngFromRgba(pixels: ByteArray, width: Int, height: Int): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = (y * width + x) * 4
                val r = pixels[i].toInt() and 0xFF
                val g = pixels[i + 1].toInt() and 0xFF
                val b = pixels[i + 2].toInt() and 0xFF
                val a = pixels[i + 3].toInt() and 0xFF
                bitmap.setPixel(x, y, android.graphics.Color.argb(a, r, g, b))
            }
        }

        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        bitmap.recycle()

        return outputStream.toByteArray()
    }

    private fun resizeTexture(texture: Texture, maxSize: Int): Texture {
        if (texture.width <= maxSize && texture.height <= maxSize) {
            return texture
        }

        val bitmap = android.graphics.BitmapFactory.decodeByteArray(texture.data, 0, texture.data.size)
            ?: return texture

        val scale = maxSize.toFloat() / maxOf(texture.width, texture.height)
        val newWidth = (texture.width * scale).toInt()
        val newHeight = (texture.height * scale).toInt()

        val resized = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        bitmap.recycle()

        val outputStream = java.io.ByteArrayOutputStream()
        resized.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        resized.recycle()

        return texture.copy(
            width = newWidth,
            height = newHeight,
            data = outputStream.toByteArray()
        )
    }

    private fun writeAddonFiles(
        workDir: File,
        addon: BedrockAddon,
        geometry: BedrockGeometry,
        entityData: EntityGeneratorResult,
        manifests: ManifestGeneratorResult,
        textures: List<Texture>
    ) {
        // Create resource pack structure
        val rpDir = File(workDir, "resource_pack")
        rpDir.mkdirs()

        // RP manifest
        File(rpDir, "manifest.json").writeText(
            manifests.resourcePackManifestJson
        )

        // RP pack_icon (optional, create simple one)
        createPackIcon(rpDir)

        // Entity client definition
        val rpEntityDir = File(rpDir, "entity")
        rpEntityDir.mkdirs()
        File(rpEntityDir, "${entityData.serverEntity.name}.entity.json").writeText(
            entityData.clientEntityJson
        )

        // Geometry
        val modelsDir = File(rpDir, "models/entity")
        modelsDir.mkdirs()
        File(modelsDir, "${entityData.serverEntity.name}.geo.json").writeText(
            entityData.geometryJson
        )

        // Render controller
        val rcDir = File(rpDir, "render_controllers")
        rcDir.mkdirs()
        File(rcDir, "${entityData.serverEntity.name}.render_controllers.json").writeText(
            entityData.renderControllerJson
        )

        // Textures
        val texturesDir = File(rpDir, "textures/entity/${entityData.serverEntity.name}")
        texturesDir.mkdirs()

        textures.forEachIndexed { index, texture ->
            val textureName = if (index == 0) "default.png" else "${texture.name}.png"
            File(texturesDir, textureName).writeBytes(texture.data)
        }

        // If no textures, write default
        if (textures.isEmpty()) {
            val defaultTexture = createDefaultTexture()
            File(texturesDir, "default.png").writeBytes(defaultTexture.data)
        }

        // Create behavior pack structure
        val bpDir = File(workDir, "behavior_pack")
        bpDir.mkdirs()

        // BP manifest
        File(bpDir, "manifest.json").writeText(
            manifests.behaviorPackManifestJson
        )

        // BP pack_icon
        createPackIcon(bpDir)

        // Entity server definition
        val bpEntityDir = File(bpDir, "entities")
        bpEntityDir.mkdirs()
        File(bpEntityDir, "${entityData.serverEntity.name}.json").writeText(
            entityData.serverEntityJson
        )
    }

    private fun createPackIcon(packDir: File) {
        // Create a simple 64x64 pack icon
        val size = 64
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Green background
        canvas.drawColor(android.graphics.Color.parseColor("#4CAF50"))

        // Draw a simple cube shape
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        // Simple 3D cube representation
        val margin = 12f
        val cubeSize = size - margin * 2

        // Front face
        canvas.drawRect(margin, margin + 8, margin + cubeSize * 0.7f, margin + cubeSize, paint)

        // Top face (parallelogram)
        val path = android.graphics.Path()
        path.moveTo(margin, margin + 8)
        path.lineTo(margin + cubeSize * 0.3f, margin)
        path.lineTo(margin + cubeSize, margin)
        path.lineTo(margin + cubeSize * 0.7f, margin + 8)
        path.close()
        canvas.drawPath(path, paint)

        // Right face (parallelogram)
        val path2 = android.graphics.Path()
        path2.moveTo(margin + cubeSize * 0.7f, margin + 8)
        path2.lineTo(margin + cubeSize, margin)
        path2.lineTo(margin + cubeSize, margin + cubeSize - 8)
        path2.lineTo(margin + cubeSize * 0.7f, margin + cubeSize)
        path2.close()
        canvas.drawPath(path2, paint)

        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        bitmap.recycle()

        File(packDir, "pack_icon.png").writeBytes(outputStream.toByteArray())
    }

    /**
     * Get estimated export size
     */
    fun estimateExportSize(model: Model3D, settings: ExportSettings): Long {
        // Rough estimation
        val geometrySize = model.vertexCount * 50L // ~50 bytes per vertex in JSON
        val textureSize = model.textures.sumOf { it.data.size.toLong() }
        val metadataSize = 10_000L // Manifests, entity definitions, etc.

        return geometrySize + textureSize + metadataSize
    }
}