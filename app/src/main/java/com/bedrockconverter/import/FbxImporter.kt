// app/src/main/java/com/bedrockconverter/import/FbxImporter.kt
package com.bedrockconverter.import

import android.content.Context
import com.bedrockconverter.model.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * Importer for Autodesk FBX format files
 * Supports FBX 7.x binary format (basic geometry import)
 * Note: This is a simplified implementation focusing on geometry extraction
 */
class FbxImporter(private val context: Context) {

    companion object {
        private val FBX_HEADER_MAGIC = "Kaydara FBX Binary  ".toByteArray()
        private const val FBX_HEADER_SIZE = 27
    }

    /**
     * Import FBX file
     */
    fun import(file: File, originalName: String): Model3D? {
        return try {
            val fbxData = parseFbxFile(file)
            
            if (fbxData == null) {
                // Try ASCII format
                return importAscii(file, originalName)
            }

            val meshes = buildMeshes(fbxData)
            val geometry = Geometry(meshes = meshes)
            val bounds = geometry.calculateBounds()

            val name = originalName.substringBeforeLast('.').ifEmpty { "model" }

            Model3D(
                name = name,
                format = "fbx",
                sourceUri = android.net.Uri.EMPTY,
                geometry = geometry,
                textures = fbxData.textures,
                materials = fbxData.materials,
                bounds = bounds,
                pivot = bounds.center
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseFbxFile(file: File): FbxData? {
        val raf = RandomAccessFile(file, "r")

        try {
            // Check header
            val headerBytes = ByteArray(FBX_HEADER_SIZE)
            raf.readFully(headerBytes)

            val magicBytes = headerBytes.copyOfRange(0, 20)
            if (!magicBytes.contentEquals(FBX_HEADER_MAGIC)) {
                raf.close()
                return null // Not a binary FBX
            }

            // Get version
            val version = ByteBuffer.wrap(headerBytes, 23, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int

            val is64Bit = version >= 7500

            val vertices = mutableListOf<Float>()
            val normals = mutableListOf<Float>()
            val uvs = mutableListOf<Float>()
            val indices = mutableListOf<Int>()
            val textures = mutableListOf<Texture>()
            val materials = mutableListOf<Material>()

            // Parse nodes
            while (raf.filePointer < raf.length()) {
                val node = readNode(raf, is64Bit) ?: break

                when (node.name) {
                    "Objects" -> {
                        parseObjectsNode(node, vertices, normals, uvs, indices, textures, materials)
                    }
                }
            }

            raf.close()

            return FbxData(
                vertices = vertices.toFloatArray(),
                normals = normals.toFloatArray(),
                uvs = uvs.toFloatArray(),
                indices = indices.toIntArray(),
                textures = textures,
                materials = materials
            )
        } catch (e: Exception) {
            e.printStackTrace()
            raf.close()
            return null
        }
    }

    private fun readNode(raf: RandomAccessFile, is64Bit: Boolean): FbxNode? {
        val startPos = raf.filePointer

        val endOffset: Long
        val numProperties: Long
        val propertyListLen: Long

        if (is64Bit) {
            endOffset = readLongLE(raf)
            numProperties = readLongLE(raf)
            propertyListLen = readLongLE(raf)
        } else {
            endOffset = readIntLE(raf).toLong()
            numProperties = readIntLE(raf).toLong()
            propertyListLen = readIntLE(raf).toLong()
        }

        // Null node (end marker)
        if (endOffset == 0L && numProperties == 0L && propertyListLen == 0L) {
            return null
        }

        val nameLen = raf.read()
        val nameBytes = ByteArray(nameLen)
        raf.readFully(nameBytes)
        val name = String(nameBytes)

        val properties = mutableListOf<Any?>()
        for (i in 0 until numProperties.toInt()) {
            val prop = readProperty(raf)
            properties.add(prop)
        }

        // Read nested nodes
        val nestedNodes = mutableListOf<FbxNode>()
        while (raf.filePointer < endOffset) {
            val nested = readNode(raf, is64Bit)
            if (nested != null) {
                nestedNodes.add(nested)
            } else {
                break
            }
        }

        // Seek to end offset
        raf.seek(endOffset)

        return FbxNode(name, properties, nestedNodes)
    }

    private fun readProperty(raf: RandomAccessFile): Any? {
        val typeCode = raf.read().toChar()

        return when (typeCode) {
            'Y' -> readShortLE(raf)                    // 16-bit signed integer
            'C' -> raf.read() != 0                     // Boolean
            'I' -> readIntLE(raf)                      // 32-bit signed integer
            'F' -> java.lang.Float.intBitsToFloat(readIntLE(raf))  // Float
            'D' -> java.lang.Double.longBitsToDouble(readLongLE(raf)) // Double
            'L' -> readLongLE(raf)                     // 64-bit signed integer
            'f' -> readFloatArray(raf)                 // Float array
            'd' -> readDoubleArray(raf)                // Double array
            'l' -> readLongArray(raf)                  // Long array
            'i' -> readIntArray(raf)                   // Int array
            'b' -> readBoolArray(raf)                  // Bool array
            'S' -> readString(raf)                     // String
            'R' -> readRaw(raf)                        // Raw binary
            else -> {
                // Unknown type, try to skip
                null
            }
        }
    }

    private fun readFloatArray(raf: RandomAccessFile): FloatArray {
        val arrayLength = readIntLE(raf)
        val encoding = readIntLE(raf)
        val compressedLength = readIntLE(raf)

        val data = ByteArray(compressedLength)
        raf.readFully(data)

        val decompressed = if (encoding == 1) {
            decompress(data, arrayLength * 4)
        } else {
            data
        }

        val result = FloatArray(arrayLength)
        val buffer = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until arrayLength) {
            result[i] = buffer.float
        }

        return result
    }

    private fun readDoubleArray(raf: RandomAccessFile): DoubleArray {
        val arrayLength = readIntLE(raf)
        val encoding = readIntLE(raf)
        val compressedLength = readIntLE(raf)

        val data = ByteArray(compressedLength)
        raf.readFully(data)

        val decompressed = if (encoding == 1) {
            decompress(data, arrayLength * 8)
        } else {
            data
        }

        val result = DoubleArray(arrayLength)
        val buffer = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until arrayLength) {
            result[i] = buffer.double
        }

        return result
    }

    private fun readIntArray(raf: RandomAccessFile): IntArray {
        val arrayLength = readIntLE(raf)
        val encoding = readIntLE(raf)
        val compressedLength = readIntLE(raf)

        val data = ByteArray(compressedLength)
        raf.readFully(data)

        val decompressed = if (encoding == 1) {
            decompress(data, arrayLength * 4)
        } else {
            data
        }

        val result = IntArray(arrayLength)
        val buffer = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until arrayLength) {
            result[i] = buffer.int
        }

        return result
    }

    private fun readLongArray(raf: RandomAccessFile): LongArray {
        val arrayLength = readIntLE(raf)
        val encoding = readIntLE(raf)
        val compressedLength = readIntLE(raf)

        val data = ByteArray(compressedLength)
        raf.readFully(data)

        val decompressed = if (encoding == 1) {
            decompress(data, arrayLength * 8)
        } else {
            data
        }

        val result = LongArray(arrayLength)
        val buffer = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until arrayLength) {
            result[i] = buffer.long
        }

        return result
    }

    private fun readBoolArray(raf: RandomAccessFile): BooleanArray {
        val arrayLength = readIntLE(raf)
        val encoding = readIntLE(raf)
        val compressedLength = readIntLE(raf)

        val data = ByteArray(compressedLength)
        raf.readFully(data)

        val decompressed = if (encoding == 1) {
            decompress(data, arrayLength)
        } else {
            data
        }

        return BooleanArray(arrayLength) { decompressed[it] != 0.toByte() }
    }

    private fun readString(raf: RandomAccessFile): String {
        val length = readIntLE(raf)
        val bytes = ByteArray(length)
        raf.readFully(bytes)
        return String(bytes)
    }

    private fun readRaw(raf: RandomAccessFile): ByteArray {
        val length = readIntLE(raf)
        val bytes = ByteArray(length)
        raf.readFully(bytes)
        return bytes
    }

    private fun decompress(data: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)

        val result = ByteArray(expectedSize)
        inflater.inflate(result)
        inflater.end()

        return result
    }

    private fun readShortLE(raf: RandomAccessFile): Short {
        val b1 = raf.read()
        val b2 = raf.read()
        return ((b2 shl 8) or b1).toShort()
    }

    private fun readIntLE(raf: RandomAccessFile): Int {
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        val b4 = raf.read()
        return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
    }

    private fun readLongLE(raf: RandomAccessFile): Long {
        val bytes = ByteArray(8)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun parseObjectsNode(
        node: FbxNode,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        uvs: MutableList<Float>,
        indices: MutableList<Int>,
        textures: MutableList<Texture>,
        materials: MutableList<Material>
    ) {
        for (child in node.children) {
            when (child.name) {
                "Geometry" -> {
                    parseGeometryNode(child, vertices, normals, uvs, indices)
                }
                "Material" -> {
                    parseMaterialNode(child, materials)
                }
                "Texture" -> {
                    parseTextureNode(child, textures)
                }
            }
        }
    }

    private fun parseGeometryNode(
        node: FbxNode,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        uvs: MutableList<Float>,
        indices: MutableList<Int>
    ) {
        for (child in node.children) {
            when (child.name) {
                "Vertices" -> {
                    val vertexData = child.properties.firstOrNull()
                    when (vertexData) {
                        is DoubleArray -> {
                            vertices.addAll(vertexData.map { it.toFloat() })
                        }
                        is FloatArray -> {
                            vertices.addAll(vertexData.toList())
                        }
                    }
                }
                "PolygonVertexIndex" -> {
                    val indexData = child.properties.firstOrNull()
                    when (indexData) {
                        is IntArray -> {
                            // FBX uses negative indices to mark polygon end
                            val polygonIndices = mutableListOf<Int>()
                            for (idx in indexData) {
                                if (idx < 0) {
                                    polygonIndices.add(idx.inv()) // Bitwise NOT to get actual index
                                    
                                    // Triangulate polygon
                                    if (polygonIndices.size >= 3) {
                                        for (i in 1 until polygonIndices.size - 1) {
                                            indices.add(polygonIndices[0])
                                            indices.add(polygonIndices[i])
                                            indices.add(polygonIndices[i + 1])
                                        }
                                    }
                                    polygonIndices.clear()
                                } else {
                                    polygonIndices.add(idx)
                                }
                            }
                        }
                    }
                }
                "LayerElementNormal" -> {
                    parseLayerElement(child, normals)
                }
                "LayerElementUV" -> {
                    parseLayerElement(child, uvs)
                }
            }
        }
    }

    private fun parseLayerElement(node: FbxNode, output: MutableList<Float>) {
        for (child in node.children) {
            when (child.name) {
                "Normals", "UV" -> {
                    val data = child.properties.firstOrNull()
                    when (data) {
                        is DoubleArray -> {
                            output.addAll(data.map { it.toFloat() })
                        }
                        is FloatArray -> {
                            output.addAll(data.toList())
                        }
                    }
                }
            }
        }
    }

    private fun parseMaterialNode(node: FbxNode, materials: MutableList<Material>) {
        var name = "material_${materials.size}"
        var diffuseColor = Color.WHITE

        // Get material name from properties
        if (node.properties.size >= 2) {
            val nameProp = node.properties[1]
            if (nameProp is String) {
                name = nameProp.substringBefore('\u0000')
            }
        }

        for (child in node.children) {
            if (child.name == "Properties70") {
                for (prop in child.children) {
                    if (prop.name == "P" && prop.properties.size >= 5) {
                        val propName = prop.properties[0] as? String
                        if (propName == "DiffuseColor") {
                            val r = (prop.properties.getOrNull(4) as? Double)?.toFloat() ?: 1f
                            val g = (prop.properties.getOrNull(5) as? Double)?.toFloat() ?: 1f
                            val b = (prop.properties.getOrNull(6) as? Double)?.toFloat() ?: 1f
                            diffuseColor = Color(r, g, b)
                        }
                    }
                }
            }
        }

        materials.add(
            Material(
                name = name,
                diffuseColor = diffuseColor
            )
        )
    }

    private fun parseTextureNode(node: FbxNode, textures: MutableList<Texture>) {
        // FBX textures are usually external files - we just note their existence
        var name = "texture_${textures.size}"

        if (node.properties.size >= 2) {
            val nameProp = node.properties[1]
            if (nameProp is String) {
                name = nameProp.substringBefore('\u0000')
            }
        }

        // Texture data would need to be loaded from external file
        // For now, we just create a placeholder
    }

    private fun buildMeshes(fbxData: FbxData): List<Mesh> {
        if (fbxData.vertices.isEmpty()) {
            return emptyList()
        }

        // Build normals if not present
        val normals = if (fbxData.normals.isEmpty()) {
            generateNormals(fbxData.vertices, fbxData.indices)
        } else {
            fbxData.normals
        }

        // Generate UVs if not present
        val uvs = if (fbxData.uvs.isEmpty()) {
            FloatArray(fbxData.vertices.size / 3 * 2) { 0f }
        } else {
            fbxData.uvs
        }

        return listOf(
            Mesh(
                name = "fbx_mesh",
                vertices = fbxData.vertices,
                normals = normals,
                uvs = uvs,
                indices = fbxData.indices,
                materialId = fbxData.materials.firstOrNull()?.id
            )
        )
    }

    private fun generateNormals(vertices: FloatArray, indices: IntArray): FloatArray {
        val normals = FloatArray(vertices.size) { 0f }

        for (i in indices.indices step 3) {
            if (i + 2 >= indices.size) break

            val i0 = indices[i] * 3
            val i1 = indices[i + 1] * 3
            val i2 = indices[i + 2] * 3

            if (i0 + 2 >= vertices.size || i1 + 2 >= vertices.size || i2 + 2 >= vertices.size) continue

            val v0 = Vector3(vertices[i0], vertices[i0 + 1], vertices[i0 + 2])
            val v1 = Vector3(vertices[i1], vertices[i1 + 1], vertices[i1 + 2])
            val v2 = Vector3(vertices[i2], vertices[i2 + 1], vertices[i2 + 2])

            val edge1 = v1 - v0
            val edge2 = v2 - v0

            val normal = Vector3(
                edge1.y * edge2.z - edge1.z * edge2.y,
                edge1.z * edge2.x - edge1.x * edge2.z,
                edge1.x * edge2.y - edge1.y * edge2.x
            ).normalized()

            for (idx in listOf(i0, i1, i2)) {
                normals[idx] += normal.x
                normals[idx + 1] += normal.y
                normals[idx + 2] += normal.z
            }
        }

        // Normalize
        for (i in normals.indices step 3) {
            val len = kotlin.math.sqrt(
                normals[i] * normals[i] +
                        normals[i + 1] * normals[i + 1] +
                        normals[i + 2] * normals[i + 2]
            )
            if (len > 0) {
                normals[i] /= len
                normals[i + 1] /= len
                normals[i + 2] /= len
            }
        }

        return normals
    }

    /**
     * Import ASCII FBX format (fallback)
     */
    private fun importAscii(file: File, originalName: String): Model3D? {
        return try {
            val content = file.readText()
            
            if (!content.contains("FBX")) {
                return null
            }

            val vertices = mutableListOf<Float>()
            val indices = mutableListOf<Int>()

            // Simple regex-based parsing for ASCII FBX
            val vertexPattern = Regex("Vertices:\\s*\\*\\d+\\s*\\{[^}]*a:\\s*([\\d.,\\s-]+)")
            val indexPattern = Regex("PolygonVertexIndex:\\s*\\*\\d+\\s*\\{[^}]*a:\\s*([\\d.,\\s-]+)")

            vertexPattern.find(content)?.let { match ->
                val vertexData = match.groupValues[1]
                vertexData.split(",").forEach { v ->
                    v.trim().toFloatOrNull()?.let { vertices.add(it) }
                }
            }

            indexPattern.find(content)?.let { match ->
                val indexData = match.groupValues[1]
                val polygonIndices = mutableListOf<Int>()
                
                indexData.split(",").forEach { i ->
                    val idx = i.trim().toIntOrNull() ?: return@forEach
                    
                    if (idx < 0) {
                        polygonIndices.add(idx.inv())
                        
                        if (polygonIndices.size >= 3) {
                            for (j in 1 until polygonIndices.size - 1) {
                                indices.add(polygonIndices[0])
                                indices.add(polygonIndices[j])
                                indices.add(polygonIndices[j + 1])
                            }
                        }
                        polygonIndices.clear()
                    } else {
                        polygonIndices.add(idx)
                    }
                }
            }

            if (vertices.isEmpty()) {
                return null
            }

            val mesh = Mesh(
                name = "fbx_mesh",
                vertices = vertices.toFloatArray(),
                normals = generateNormals(vertices.toFloatArray(), indices.toIntArray()),
                uvs = FloatArray(vertices.size / 3 * 2) { 0f },
                indices = indices.toIntArray()
            )

            val geometry = Geometry(meshes = listOf(mesh))
            val bounds = geometry.calculateBounds()
            val name = originalName.substringBeforeLast('.').ifEmpty { "model" }

            Model3D(
                name = name,
                format = "fbx",
                sourceUri = android.net.Uri.EMPTY,
                geometry = geometry,
                bounds = bounds,
                pivot = bounds.center
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Helper data classes
    private data class FbxNode(
        val name: String,
        val properties: List<Any?>,
        val children: List<FbxNode>
    )

    private data class FbxData(
        val vertices: FloatArray,
        val normals: FloatArray,
        val uvs: FloatArray,
        val indices: IntArray,
        val textures: List<Texture>,
        val materials: List<Material>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FbxData
            return vertices.contentEquals(other.vertices) &&
                    normals.contentEquals(other.normals) &&
                    uvs.contentEquals(other.uvs) &&
                    indices.contentEquals(other.indices)
        }

        override fun hashCode(): Int {
            var result = vertices.contentHashCode()
            result = 31 * result + normals.contentHashCode()
            result = 31 * result + uvs.contentHashCode()
            result = 31 * result + indices.contentHashCode()
            return result
        }
    }
}