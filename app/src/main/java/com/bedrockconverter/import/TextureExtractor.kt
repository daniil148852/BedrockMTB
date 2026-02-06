// app/src/main/java/com/bedrockconverter/import/TextureExtractor.kt
package com.bedrockconverter.import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.bedrockconverter.model.Texture
import com.bedrockconverter.model.TextureFormat
import com.bedrockconverter.model.TextureType
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Extracts and processes textures from various sources
 */
class TextureExtractor(private val context: Context) {

    companion object {
        private val SUPPORTED_EXTENSIONS = listOf("png", "jpg", "jpeg", "tga", "bmp")
        private const val MAX_TEXTURE_SIZE = 2048
    }

    /**
     * Extract textures from a directory (for GLTF with external textures)
     */
    fun extractFromDirectory(directory: File?): List<Texture> {
        if (directory == null || !directory.isDirectory) {
            return emptyList()
        }

        val textures = mutableListOf<Texture>()

        directory.listFiles()?.forEach { file ->
            if (file.isFile && isImageFile(file.name)) {
                extractTexture(file)?.let { textures.add(it) }
            }
        }

        return textures
    }

    /**
     * Extract a single texture from file
     */
    fun extractTexture(file: File): Texture? {
        return try {
            val data = file.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null

            val format = detectFormat(file.name)
            val type = detectType(file.name)

            // Convert to PNG if needed (for consistent format)
            val (finalData, finalFormat) = if (format != TextureFormat.PNG) {
                convertToPng(bitmap) to TextureFormat.PNG
            } else {
                data to format
            }

            val texture = Texture(
                name = file.nameWithoutExtension,
                width = bitmap.width,
                height = bitmap.height,
                data = finalData,
                format = finalFormat,
                type = type
            )

            bitmap.recycle()
            texture
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extract texture from byte array
     */
    fun extractTexture(data: ByteArray, name: String): Texture? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null

            val texture = Texture(
                name = name,
                width = bitmap.width,
                height = bitmap.height,
                data = data,
                format = TextureFormat.PNG,
                type = TextureType.DIFFUSE
            )

            bitmap.recycle()
            texture
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resize texture to specified maximum size
     */
    fun resizeTexture(texture: Texture, maxSize: Int): Texture {
        if (texture.width <= maxSize && texture.height <= maxSize) {
            return texture
        }

        return try {
            val bitmap = BitmapFactory.decodeByteArray(texture.data, 0, texture.data.size)
                ?: return texture

            val scale = maxSize.toFloat() / maxOf(texture.width, texture.height)
            val newWidth = (texture.width * scale).toInt()
            val newHeight = (texture.height * scale).toInt()

            val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            bitmap.recycle()

            val newData = convertToPng(resized)
            resized.recycle()

            texture.copy(
                width = newWidth,
                height = newHeight,
                data = newData
            )
        } catch (e: Exception) {
            e.printStackTrace()
            texture
        }
    }

    /**
     * Optimize texture for Minecraft (power of 2 dimensions)
     */
    fun optimizeForMinecraft(texture: Texture): Texture {
        // Minecraft prefers power-of-2 textures
        val targetWidth = nearestPowerOfTwo(texture.width)
        val targetHeight = nearestPowerOfTwo(texture.height)

        if (targetWidth == texture.width && targetHeight == texture.height) {
            return texture
        }

        return try {
            val bitmap = BitmapFactory.decodeByteArray(texture.data, 0, texture.data.size)
                ?: return texture

            val resized = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            bitmap.recycle()

            val newData = convertToPng(resized)
            resized.recycle()

            texture.copy(
                width = targetWidth,
                height = targetHeight,
                data = newData
            )
        } catch (e: Exception) {
            e.printStackTrace()
            texture
        }
    }

    /**
     * Create a placeholder texture
     */
    fun createPlaceholderTexture(width: Int = 64, height: Int = 64, name: String = "placeholder"): Texture {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Create checkerboard pattern
        for (y in 0 until height) {
            for (x in 0 until width) {
                val isWhite = ((x / 8) + (y / 8)) % 2 == 0
                val color = if (isWhite) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt()
                bitmap.setPixel(x, y, color)
            }
        }

        val data = convertToPng(bitmap)
        bitmap.recycle()

        return Texture(
            name = name,
            width = width,
            height = height,
            data = data,
            format = TextureFormat.PNG,
            type = TextureType.DIFFUSE
        )
    }

    /**
     * Combine multiple textures into a texture atlas
     */
    fun createTextureAtlas(textures: List<Texture>, atlasSize: Int = 512): TextureAtlasResult? {
        if (textures.isEmpty()) return null

        val atlas = Bitmap.createBitmap(atlasSize, atlasSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(atlas)
        val uvMappings = mutableMapOf<String, UVMapping>()

        // Simple grid packing
        val textureCount = textures.size
        val gridSize = kotlin.math.ceil(kotlin.math.sqrt(textureCount.toDouble())).toInt()
        val cellSize = atlasSize / gridSize

        textures.forEachIndexed { index, texture ->
            val gridX = index % gridSize
            val gridY = index / gridSize

            val x = gridX * cellSize
            val y = gridY * cellSize

            try {
                val bitmap = BitmapFactory.decodeByteArray(texture.data, 0, texture.data.size)
                if (bitmap != null) {
                    val scaled = Bitmap.createScaledBitmap(bitmap, cellSize, cellSize, true)
                    canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)

                    uvMappings[texture.id] = UVMapping(
                        u = x.toFloat() / atlasSize,
                        v = y.toFloat() / atlasSize,
                        width = cellSize.toFloat() / atlasSize,
                        height = cellSize.toFloat() / atlasSize
                    )

                    bitmap.recycle()
                    scaled.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val atlasData = convertToPng(atlas)
        atlas.recycle()

        val atlasTexture = Texture(
            name = "texture_atlas",
            width = atlasSize,
            height = atlasSize,
            data = atlasData,
            format = TextureFormat.PNG,
            type = TextureType.DIFFUSE
        )

        return TextureAtlasResult(
            atlas = atlasTexture,
            uvMappings = uvMappings
        )
    }

    /**
     * Convert bitmap to PNG byte array
     */
    private fun convertToPng(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Check if file is a supported image
     */
    private fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in SUPPORTED_EXTENSIONS
    }

    /**
     * Detect texture format from filename
     */
    private fun detectFormat(fileName: String): TextureFormat {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "png" -> TextureFormat.PNG
            "jpg", "jpeg" -> TextureFormat.JPG
            "tga" -> TextureFormat.TGA
            else -> TextureFormat.PNG
        }
    }

    /**
     * Detect texture type from filename
     */
    private fun detectType(fileName: String): TextureType {
        val name = fileName.lowercase()
        return when {
            name.contains("normal") || name.contains("nrm") || name.contains("_n.") -> TextureType.NORMAL
            name.contains("specular") || name.contains("spec") || name.contains("_s.") -> TextureType.SPECULAR
            name.contains("emissive") || name.contains("emit") || name.contains("_e.") -> TextureType.EMISSIVE
            name.contains("opacity") || name.contains("alpha") || name.contains("_a.") -> TextureType.OPACITY
            else -> TextureType.DIFFUSE
        }
    }

    /**
     * Find nearest power of two
     */
    private fun nearestPowerOfTwo(value: Int): Int {
        var v = value
        v--
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        v++
        return v.coerceIn(1, MAX_TEXTURE_SIZE)
    }

    /**
     * Validate texture for Minecraft compatibility
     */
    fun validateTexture(texture: Texture): TextureValidationResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Check dimensions
        if (texture.width > MAX_TEXTURE_SIZE || texture.height > MAX_TEXTURE_SIZE) {
            warnings.add("Texture exceeds ${MAX_TEXTURE_SIZE}px. Will be resized.")
        }

        // Check power of 2
        if (!isPowerOfTwo(texture.width) || !isPowerOfTwo(texture.height)) {
            warnings.add("Texture dimensions are not power of 2. May cause issues.")
        }

        // Check aspect ratio
        if (texture.width != texture.height) {
            warnings.add("Non-square texture. Some UV mappings may not work correctly.")
        }

        // Check format
        if (texture.format != TextureFormat.PNG) {
            warnings.add("Texture will be converted to PNG format.")
        }

        return TextureValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    private fun isPowerOfTwo(value: Int): Boolean {
        return value > 0 && (value and (value - 1)) == 0
    }
}

/**
 * Result of texture atlas creation
 */
data class TextureAtlasResult(
    val atlas: Texture,
    val uvMappings: Map<String, UVMapping>
)

/**
 * UV mapping for a texture in an atlas
 */
data class UVMapping(
    val u: Float,
    val v: Float,
    val width: Float,
    val height: Float
)

/**
 * Result of texture validation
 */
data class TextureValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)