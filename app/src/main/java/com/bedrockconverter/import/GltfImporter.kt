// app/src/main/java/com/bedrockconverter/import/GltfImporter.kt
package com.bedrockconverter.import

import android.content.Context
import android.graphics.BitmapFactory
import com.bedrockconverter.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Importer for GLTF and GLB format files
 * Supports GLTF 2.0 specification
 */
class GltfImporter(private val context: Context) {

    companion object {
        private const val GLB_MAGIC = 0x46546C67 // "glTF" in little endian
        private const val GLB_VERSION = 2
        private const val CHUNK_TYPE_JSON = 0x4E4F534A // "JSON"
        private const val CHUNK_TYPE_BIN = 0x004E4942  // "BIN\0"

        // Component types
        private const val COMPONENT_TYPE_BYTE = 5120
        private const val COMPONENT_TYPE_UNSIGNED_BYTE = 5121
        private const val COMPONENT_TYPE_SHORT = 5122
        private const val COMPONENT_TYPE_UNSIGNED_SHORT = 5123
        private const val COMPONENT_TYPE_UNSIGNED_INT = 5125
        private const val COMPONENT_TYPE_FLOAT = 5126
    }

    /**
     * Import GLB (binary GLTF) file
     */
    fun importGlb(file: File, originalName: String): Model3D? {
        return try {
            val raf = RandomAccessFile(file, "r")
            
            // Read GLB header
            val magic = raf.readIntLE()
            if (magic != GLB_MAGIC) {
                raf.close()
                return null
            }

            val version = raf.readIntLE()
            if (version != GLB_VERSION) {
                raf.close()
                return null
            }

            val totalLength = raf.readIntLE()

            // Read JSON chunk
            val jsonChunkLength = raf.readIntLE()
            val jsonChunkType = raf.readIntLE()

            if (jsonChunkType != CHUNK_TYPE_JSON) {
                raf.close()
                return null
            }

            val jsonBytes = ByteArray(jsonChunkLength)
            raf.readFully(jsonBytes)
            val jsonString = String(jsonBytes, Charsets.UTF_8).trimEnd('\u0000')
            val gltfJson = JSONObject(jsonString)

            // Read binary chunk if present
            var binaryData: ByteArray? = null
            if (raf.filePointer < totalLength) {
                val binChunkLength = raf.readIntLE()
                val binChunkType = raf.readIntLE()

                if (binChunkType == CHUNK_TYPE_BIN) {
                    binaryData = ByteArray(binChunkLength)
                    raf.readFully(binaryData)
                }
            }

            raf.close()

            parseGltf(gltfJson, binaryData, file.parentFile, originalName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Import GLTF (JSON) file
     */
    fun importGltf(file: File, originalName: String): Model3D? {
        return try {
            val jsonString = file.readText()
            val gltfJson = JSONObject(jsonString)
            parseGltf(gltfJson, null, file.parentFile, originalName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseGltf(
        json: JSONObject,
        binaryData: ByteArray?,
        baseDir: File?,
        originalName: String
    ): Model3D {
        val buffers = mutableListOf<ByteArray>()
        val bufferViews = mutableListOf<BufferView>()
        val accessors = mutableListOf<Accessor>()
        val meshes = mutableListOf<Mesh>()
        val textures = mutableListOf<Texture>()
        val materials = mutableListOf<Material>()

        // Parse buffers
        if (json.has("buffers")) {
            val buffersArray = json.getJSONArray("buffers")
            for (i in 0 until buffersArray.length()) {
                val bufferObj = buffersArray.getJSONObject(i)
                val buffer = parseBuffer(bufferObj, binaryData, baseDir)
                if (buffer != null) {
                    buffers.add(buffer)
                }
            }
        } else if (binaryData != null) {
            buffers.add(binaryData)
        }

        // Parse buffer views
        if (json.has("bufferViews")) {
            val bufferViewsArray = json.getJSONArray("bufferViews")
            for (i in 0 until bufferViewsArray.length()) {
                val bvObj = bufferViewsArray.getJSONObject(i)
                bufferViews.add(parseBufferView(bvObj))
            }
        }

        // Parse accessors
        if (json.has("accessors")) {
            val accessorsArray = json.getJSONArray("accessors")
            for (i in 0 until accessorsArray.length()) {
                val accObj = accessorsArray.getJSONObject(i)
                accessors.add(parseAccessor(accObj))
            }
        }

        // Parse images and textures
        val images = mutableListOf<ByteArray>()
        if (json.has("images")) {
            val imagesArray = json.getJSONArray("images")
            for (i in 0 until imagesArray.length()) {
                val imgObj = imagesArray.getJSONObject(i)
                val imageData = parseImage(imgObj, buffers, bufferViews, baseDir)
                if (imageData != null) {
                    images.add(imageData)
                }
            }
        }

        if (json.has("textures")) {
            val texturesArray = json.getJSONArray("textures")
            for (i in 0 until texturesArray.length()) {
                val texObj = texturesArray.getJSONObject(i)
                val sourceIndex = texObj.optInt("source", -1)
                if (sourceIndex >= 0 && sourceIndex < images.size) {
                    val imageData = images[sourceIndex]
                    val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                    if (bitmap != null) {
                        textures.add(
                            Texture(
                                name = "texture_$i",
                                width = bitmap.width,
                                height = bitmap.height,
                                data = imageData,
                                format = TextureFormat.PNG
                            )
                        )
                        bitmap.recycle()
                    }
                }
            }
        }

        // Parse materials
        if (json.has("materials")) {
            val materialsArray = json.getJSONArray("materials")
            for (i in 0 until materialsArray.length()) {
                val matObj = materialsArray.getJSONObject(i)
                materials.add(parseMaterial(matObj, i))
            }
        }

        // Parse meshes
        if (json.has("meshes")) {
            val meshesArray = json.getJSONArray("meshes")
            for (i in 0 until meshesArray.length()) {
                val meshObj = meshesArray.getJSONObject(i)
                val parsedMeshes = parseMesh(meshObj, accessors, bufferViews, buffers, i)
                meshes.addAll(parsedMeshes)
            }
        }

        // Calculate bounds
        val geometry = Geometry(meshes = meshes)
        val bounds = geometry.calculateBounds()

        // Get model name
        val name = originalName.substringBeforeLast('.').ifEmpty { "model" }

        return Model3D(
            name = name,
            format = "gltf",
            sourceUri = android.net.Uri.EMPTY,
            geometry = geometry,
            textures = textures,
            materials = materials,
            bounds = bounds,
            pivot = bounds.center
        )
    }

    private fun parseBuffer(
        bufferObj: JSONObject,
        embeddedData: ByteArray?,
        baseDir: File?
    ): ByteArray? {
        val byteLength = bufferObj.getInt("byteLength")

        if (bufferObj.has("uri")) {
            val uri = bufferObj.getString("uri")

            // Check for data URI
            if (uri.startsWith("data:")) {
                val base64Data = uri.substringAfter("base64,")
                return Base64.getDecoder().decode(base64Data)
            }

            // External file
            if (baseDir != null) {
                val externalFile = File(baseDir, uri)
                if (externalFile.exists()) {
                    return externalFile.readBytes()
                }
            }
        }

        // Use embedded binary data
        return embeddedData?.copyOf(byteLength)
    }

    private fun parseBufferView(bvObj: JSONObject): BufferView {
        return BufferView(
            buffer = bvObj.getInt("buffer"),
            byteOffset = bvObj.optInt("byteOffset", 0),
            byteLength = bvObj.getInt("byteLength"),
            byteStride = bvObj.optInt("byteStride", 0),
            target = bvObj.optInt("target", 0)
        )
    }

    private fun parseAccessor(accObj: JSONObject): Accessor {
        return Accessor(
            bufferView = accObj.optInt("bufferView", -1),
            byteOffset = accObj.optInt("byteOffset", 0),
            componentType = accObj.getInt("componentType"),
            count = accObj.getInt("count"),
            type = accObj.getString("type"),
            min = accObj.optJSONArray("min")?.let { parseFloatArray(it) },
            max = accObj.optJSONArray("max")?.let { parseFloatArray(it) }
        )
    }

    private fun parseImage(
        imgObj: JSONObject,
        buffers: List<ByteArray>,
        bufferViews: List<BufferView>,
        baseDir: File?
    ): ByteArray? {
        if (imgObj.has("uri")) {
            val uri = imgObj.getString("uri")

            // Data URI
            if (uri.startsWith("data:")) {
                val base64Data = uri.substringAfter("base64,")
                return Base64.getDecoder().decode(base64Data)
            }

            // External file
            if (baseDir != null) {
                val imageFile = File(baseDir, uri)
                if (imageFile.exists()) {
                    return imageFile.readBytes()
                }
            }
        }

        // Buffer view reference
        if (imgObj.has("bufferView")) {
            val bvIndex = imgObj.getInt("bufferView")
            if (bvIndex < bufferViews.size) {
                val bv = bufferViews[bvIndex]
                if (bv.buffer < buffers.size) {
                    val buffer = buffers[bv.buffer]
                    return buffer.copyOfRange(bv.byteOffset, bv.byteOffset + bv.byteLength)
                }
            }
        }

        return null
    }

    private fun parseMaterial(matObj: JSONObject, index: Int): Material {
        val name = matObj.optString("name", "material_$index")

        var diffuseColor = Color.WHITE
        var diffuseTextureId: String? = null
        var metallic = 0f
        var roughness = 0.5f

        if (matObj.has("pbrMetallicRoughness")) {
            val pbr = matObj.getJSONObject("pbrMetallicRoughness")

            if (pbr.has("baseColorFactor")) {
                val colorArray = pbr.getJSONArray("baseColorFactor")
                diffuseColor = Color(
                    r = colorArray.getDouble(0).toFloat(),
                    g = colorArray.getDouble(1).toFloat(),
                    b = colorArray.getDouble(2).toFloat(),
                    a = if (colorArray.length() > 3) colorArray.getDouble(3).toFloat() else 1f
                )
            }

            if (pbr.has("baseColorTexture")) {
                val texInfo = pbr.getJSONObject("baseColorTexture")
                diffuseTextureId = "texture_${texInfo.getInt("index")}"
            }

            metallic = pbr.optDouble("metallicFactor", 0.0).toFloat()
            roughness = pbr.optDouble("roughnessFactor", 0.5).toFloat()
        }

        return Material(
            name = name,
            diffuseColor = diffuseColor,
            diffuseTextureId = diffuseTextureId,
            metallic = metallic,
            roughness = roughness
        )
    }

    private fun parseMesh(
        meshObj: JSONObject,
        accessors: List<Accessor>,
        bufferViews: List<BufferView>,
        buffers: List<ByteArray>,
        meshIndex: Int
    ): List<Mesh> {
        val meshes = mutableListOf<Mesh>()
        val name = meshObj.optString("name", "mesh_$meshIndex")

        if (meshObj.has("primitives")) {
            val primitivesArray = meshObj.getJSONArray("primitives")

            for (i in 0 until primitivesArray.length()) {
                val primObj = primitivesArray.getJSONObject(i)
                val mesh = parsePrimitive(primObj, accessors, bufferViews, buffers, "${name}_$i")
                if (mesh != null) {
                    meshes.add(mesh)
                }
            }
        }

        return meshes
    }

    private fun parsePrimitive(
        primObj: JSONObject,
        accessors: List<Accessor>,
        bufferViews: List<BufferView>,
        buffers: List<ByteArray>,
        name: String
    ): Mesh? {
        val attributes = primObj.getJSONObject("attributes")

        // Get position data (required)
        val positionAccessorIndex = attributes.optInt("POSITION", -1)
        if (positionAccessorIndex < 0) return null

        val vertices = getAccessorData(positionAccessorIndex, accessors, bufferViews, buffers)
            ?: return null

        // Get normal data (optional)
        val normalAccessorIndex = attributes.optInt("NORMAL", -1)
        val normals = if (normalAccessorIndex >= 0) {
            getAccessorData(normalAccessorIndex, accessors, bufferViews, buffers)
        } else null

        // Get UV data (optional)
        val uvAccessorIndex = attributes.optInt("TEXCOORD_0", -1)
        val uvs = if (uvAccessorIndex >= 0) {
            getAccessorData(uvAccessorIndex, accessors, bufferViews, buffers)
        } else null

        // Get indices (optional)
        val indicesAccessorIndex = primObj.optInt("indices", -1)
        val indices = if (indicesAccessorIndex >= 0) {
            getAccessorIndices(indicesAccessorIndex, accessors, bufferViews, buffers)
        } else {
            // Generate sequential indices
            IntArray(vertices.size / 3) { it }
        }

        // Get material
        val materialIndex = primObj.optInt("material", -1)
        val materialId = if (materialIndex >= 0) "material_$materialIndex" else null

        return Mesh(
            name = name,
            vertices = vertices,
            normals = normals ?: floatArrayOf(),
            uvs = uvs ?: floatArrayOf(),
            indices = indices ?: intArrayOf(),
            materialId = materialId
        )
    }

    private fun getAccessorData(
        accessorIndex: Int,
        accessors: List<Accessor>,
        bufferViews: List<BufferView>,
        buffers: List<ByteArray>
    ): FloatArray? {
        if (accessorIndex >= accessors.size) return null

        val accessor = accessors[accessorIndex]
        if (accessor.bufferView < 0 || accessor.bufferView >= bufferViews.size) return null

        val bufferView = bufferViews[accessor.bufferView]
        if (bufferView.buffer >= buffers.size) return null

        val buffer = buffers[bufferView.buffer]

        val componentCount = when (accessor.type) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4" -> 4
            "MAT2" -> 4
            "MAT3" -> 9
            "MAT4" -> 16
            else -> return null
        }

        val totalComponents = accessor.count * componentCount
        val result = FloatArray(totalComponents)

        val byteBuffer = ByteBuffer.wrap(buffer)
            .order(ByteOrder.LITTLE_ENDIAN)

        val startOffset = bufferView.byteOffset + accessor.byteOffset
        val stride = if (bufferView.byteStride > 0) bufferView.byteStride else componentCount * 4

        for (i in 0 until accessor.count) {
            val elementOffset = startOffset + i * stride

            for (j in 0 until componentCount) {
                val componentOffset = elementOffset + j * 4
                byteBuffer.position(componentOffset)

                result[i * componentCount + j] = when (accessor.componentType) {
                    COMPONENT_TYPE_FLOAT -> byteBuffer.float
                    COMPONENT_TYPE_UNSIGNED_BYTE -> (byteBuffer.get().toInt() and 0xFF) / 255f
                    COMPONENT_TYPE_UNSIGNED_SHORT -> (byteBuffer.short.toInt() and 0xFFFF) / 65535f
                    else -> byteBuffer.float
                }
            }
        }

        return result
    }

    private fun getAccessorIndices(
        accessorIndex: Int,
        accessors: List<Accessor>,
        bufferViews: List<BufferView>,
        buffers: List<ByteArray>
    ): IntArray? {
        if (accessorIndex >= accessors.size) return null

        val accessor = accessors[accessorIndex]
        if (accessor.bufferView < 0 || accessor.bufferView >= bufferViews.size) return null

        val bufferView = bufferViews[accessor.bufferView]
        if (bufferView.buffer >= buffers.size) return null

        val buffer = buffers[bufferView.buffer]
        val result = IntArray(accessor.count)

        val byteBuffer = ByteBuffer.wrap(buffer)
            .order(ByteOrder.LITTLE_ENDIAN)

        val startOffset = bufferView.byteOffset + accessor.byteOffset

        val componentSize = when (accessor.componentType) {
            COMPONENT_TYPE_UNSIGNED_BYTE -> 1
            COMPONENT_TYPE_UNSIGNED_SHORT -> 2
            COMPONENT_TYPE_UNSIGNED_INT -> 4
            else -> 4
        }

        for (i in 0 until accessor.count) {
            val offset = startOffset + i * componentSize
            byteBuffer.position(offset)

            result[i] = when (accessor.componentType) {
                COMPONENT_TYPE_UNSIGNED_BYTE -> byteBuffer.get().toInt() and 0xFF
                COMPONENT_TYPE_UNSIGNED_SHORT -> byteBuffer.short.toInt() and 0xFFFF
                COMPONENT_TYPE_UNSIGNED_INT -> byteBuffer.int
                else -> byteBuffer.int
            }
        }

        return result
    }

    private fun parseFloatArray(jsonArray: JSONArray): FloatArray {
        return FloatArray(jsonArray.length()) { jsonArray.getDouble(it).toFloat() }
    }

    private fun RandomAccessFile.readIntLE(): Int {
        val b1 = read()
        val b2 = read()
        val b3 = read()
        val b4 = read()
        return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
    }

    // Helper data classes
    private data class BufferView(
        val buffer: Int,
        val byteOffset: Int,
        val byteLength: Int,
        val byteStride: Int,
        val target: Int
    )

    private data class Accessor(
        val bufferView: Int,
        val byteOffset: Int,
        val componentType: Int,
        val count: Int,
        val type: String,
        val min: FloatArray?,
        val max: FloatArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Accessor
            return bufferView == other.bufferView &&
                    byteOffset == other.byteOffset &&
                    componentType == other.componentType &&
                    count == other.count &&
                    type == other.type
        }

        override fun hashCode(): Int {
            var result = bufferView
            result = 31 * result + byteOffset
            result = 31 * result + componentType
            result = 31 * result + count
            result = 31 * result + type.hashCode()
            return result
        }
    }
}