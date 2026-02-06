// app/src/main/java/com/bedrockconverter/import/ObjImporter.kt
package com.bedrockconverter.import

import android.content.Context
import android.graphics.BitmapFactory
import com.bedrockconverter.model.*
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Importer for Wavefront OBJ format files
 */
class ObjImporter(private val context: Context) {

    /**
     * Import OBJ file
     */
    fun import(file: File, originalName: String): Model3D? {
        return try {
            val objData = parseObjFile(file)
            val materials = parseMtlFile(objData.mtlFileName, file.parentFile)
            val textures = loadTextures(materials, file.parentFile)

            val meshes = buildMeshes(objData, materials)
            val geometry = Geometry(meshes = meshes)
            val bounds = geometry.calculateBounds()

            val name = originalName.substringBeforeLast('.').ifEmpty { "model" }

            Model3D(
                name = name,
                format = "obj",
                sourceUri = android.net.Uri.EMPTY,
                geometry = geometry,
                textures = textures,
                materials = materials.values.toList(),
                bounds = bounds,
                pivot = bounds.center
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseObjFile(file: File): ObjData {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val faces = mutableListOf<ObjFace>()
        var currentMaterial: String? = null
        var mtlFileName: String? = null

        BufferedReader(FileReader(file)).use { reader ->
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

                val parts = trimmed.split(Regex("\\s+"))
                if (parts.isEmpty()) return@forEachLine

                when (parts[0]) {
                    "v" -> {
                        // Vertex position
                        if (parts.size >= 4) {
                            vertices.add(parts[1].toFloatOrNull() ?: 0f)
                            vertices.add(parts[2].toFloatOrNull() ?: 0f)
                            vertices.add(parts[3].toFloatOrNull() ?: 0f)
                        }
                    }
                    "vn" -> {
                        // Vertex normal
                        if (parts.size >= 4) {
                            normals.add(parts[1].toFloatOrNull() ?: 0f)
                            normals.add(parts[2].toFloatOrNull() ?: 0f)
                            normals.add(parts[3].toFloatOrNull() ?: 0f)
                        }
                    }
                    "vt" -> {
                        // Texture coordinate
                        if (parts.size >= 3) {
                            uvs.add(parts[1].toFloatOrNull() ?: 0f)
                            uvs.add(parts[2].toFloatOrNull() ?: 0f)
                        }
                    }
                    "f" -> {
                        // Face
                        val faceVertices = mutableListOf<ObjFaceVertex>()
                        for (i in 1 until parts.size) {
                            val vertex = parseFaceVertex(parts[i])
                            if (vertex != null) {
                                faceVertices.add(vertex)
                            }
                        }
                        if (faceVertices.size >= 3) {
                            faces.add(ObjFace(faceVertices, currentMaterial))
                        }
                    }
                    "usemtl" -> {
                        if (parts.size >= 2) {
                            currentMaterial = parts[1]
                        }
                    }
                    "mtllib" -> {
                        if (parts.size >= 2) {
                            mtlFileName = parts[1]
                        }
                    }
                }
            }
        }

        return ObjData(
            vertices = vertices.toFloatArray(),
            normals = normals.toFloatArray(),
            uvs = uvs.toFloatArray(),
            faces = faces,
            mtlFileName = mtlFileName
        )
    }

    private fun parseFaceVertex(str: String): ObjFaceVertex? {
        val parts = str.split("/")
        if (parts.isEmpty()) return null

        val vertexIndex = parts[0].toIntOrNull()?.let { it - 1 } ?: return null
        val uvIndex = parts.getOrNull(1)?.toIntOrNull()?.let { it - 1 }
        val normalIndex = parts.getOrNull(2)?.toIntOrNull()?.let { it - 1 }

        return ObjFaceVertex(vertexIndex, uvIndex, normalIndex)
    }

    private fun parseMtlFile(mtlFileName: String?, baseDir: File?): Map<String, Material> {
        val materials = mutableMapOf<String, Material>()

        if (mtlFileName == null || baseDir == null) return materials

        val mtlFile = File(baseDir, mtlFileName)
        if (!mtlFile.exists()) return materials

        var currentMaterial: MutableMaterial? = null

        try {
            BufferedReader(FileReader(mtlFile)).use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.isEmpty()) return@forEachLine

                    when (parts[0]) {
                        "newmtl" -> {
                            // Save previous material
                            currentMaterial?.let {
                                materials[it.name] = it.toMaterial()
                            }
                            // Start new material
                            if (parts.size >= 2) {
                                currentMaterial = MutableMaterial(parts[1])
                            }
                        }
                        "Kd" -> {
                            // Diffuse color
                            if (parts.size >= 4) {
                                currentMaterial?.diffuseColor = Color(
                                    r = parts[1].toFloatOrNull() ?: 1f,
                                    g = parts[2].toFloatOrNull() ?: 1f,
                                    b = parts[3].toFloatOrNull() ?: 1f
                                )
                            }
                        }
                        "map_Kd" -> {
                            // Diffuse texture
                            if (parts.size >= 2) {
                                currentMaterial?.diffuseTexture = parts.subList(1, parts.size).joinToString(" ")
                            }
                        }
                        "map_Bump", "bump" -> {
                            // Normal map
                            if (parts.size >= 2) {
                                currentMaterial?.normalTexture = parts.subList(1, parts.size).joinToString(" ")
                            }
                        }
                        "d", "Tr" -> {
                            // Opacity
                            if (parts.size >= 2) {
                                val opacity = parts[1].toFloatOrNull() ?: 1f
                                currentMaterial?.opacity = if (parts[0] == "Tr") 1f - opacity else opacity
                            }
                        }
                    }
                }
            }

            // Save last material
            currentMaterial?.let {
                materials[it.name] = it.toMaterial()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return materials
    }

    private fun loadTextures(materials: Map<String, Material>, baseDir: File?): List<Texture> {
        val textures = mutableListOf<Texture>()
        val loadedPaths = mutableSetOf<String>()

        if (baseDir == null) return textures

        for (material in materials.values) {
            // Load diffuse texture
            material.diffuseTextureId?.let { texPath ->
                if (texPath !in loadedPaths) {
                    loadTexture(texPath, baseDir)?.let {
                        textures.add(it)
                        loadedPaths.add(texPath)
                    }
                }
            }
        }

        return textures
    }

    private fun loadTexture(path: String, baseDir: File): Texture? {
        return try {
            val textureFile = File(baseDir, path)
            if (!textureFile.exists()) return null

            val data = textureFile.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null

            val format = when {
                path.lowercase().endsWith(".png") -> TextureFormat.PNG
                path.lowercase().endsWith(".jpg") || path.lowercase().endsWith(".jpeg") -> TextureFormat.JPG
                else -> TextureFormat.PNG
            }

            val texture = Texture(
                name = textureFile.nameWithoutExtension,
                width = bitmap.width,
                height = bitmap.height,
                data = data,
                format = format
            )

            bitmap.recycle()
            texture
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildMeshes(objData: ObjData, materials: Map<String, Material>): List<Mesh> {
        // Group faces by material
        val facesByMaterial = objData.faces.groupBy { it.material ?: "" }

        return facesByMaterial.map { (materialName, faces) ->
            buildMesh(objData, faces, materialName, materials)
        }
    }

    private fun buildMesh(
        objData: ObjData,
        faces: List<ObjFace>,
        materialName: String,
        materials: Map<String, Material>
    ): Mesh {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        val vertexMap = mutableMapOf<String, Int>()
        var currentIndex = 0

        for (face in faces) {
            // Triangulate face (fan triangulation)
            for (i in 1 until face.vertices.size - 1) {
                val triangleVertices = listOf(
                    face.vertices[0],
                    face.vertices[i],
                    face.vertices[i + 1]
                )

                for (faceVertex in triangleVertices) {
                    val key = "${faceVertex.vertexIndex}/${faceVertex.uvIndex}/${faceVertex.normalIndex}"

                    val index = vertexMap.getOrPut(key) {
                        // Add vertex data
                        val vIdx = faceVertex.vertexIndex * 3
                        if (vIdx + 2 < objData.vertices.size) {
                            vertices.add(objData.vertices[vIdx])
                            vertices.add(objData.vertices[vIdx + 1])
                            vertices.add(objData.vertices[vIdx + 2])
                        } else {
                            vertices.addAll(listOf(0f, 0f, 0f))
                        }

                        // Add normal data
                        if (faceVertex.normalIndex != null) {
                            val nIdx = faceVertex.normalIndex * 3
                            if (nIdx + 2 < objData.normals.size) {
                                normals.add(objData.normals[nIdx])
                                normals.add(objData.normals[nIdx + 1])
                                normals.add(objData.normals[nIdx + 2])
                            } else {
                                normals.addAll(listOf(0f, 1f, 0f))
                            }
                        } else {
                            normals.addAll(listOf(0f, 1f, 0f))
                        }

                        // Add UV data
                        if (faceVertex.uvIndex != null) {
                            val tIdx = faceVertex.uvIndex * 2
                            if (tIdx + 1 < objData.uvs.size) {
                                uvs.add(objData.uvs[tIdx])
                                uvs.add(1f - objData.uvs[tIdx + 1]) // Flip V
                            } else {
                                uvs.addAll(listOf(0f, 0f))
                            }
                        } else {
                            uvs.addAll(listOf(0f, 0f))
                        }

                        currentIndex++
                        currentIndex - 1
                    }

                    indices.add(index)
                }
            }
        }

        val material = materials[materialName]

        return Mesh(
            name = materialName.ifEmpty { "default" },
            vertices = vertices.toFloatArray(),
            normals = normals.toFloatArray(),
            uvs = uvs.toFloatArray(),
            indices = indices.toIntArray(),
            materialId = material?.id
        )
    }

    // Helper data classes
    private data class ObjData(
        val vertices: FloatArray,
        val normals: FloatArray,
        val uvs: FloatArray,
        val faces: List<ObjFace>,
        val mtlFileName: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ObjData
            return vertices.contentEquals(other.vertices) &&
                    normals.contentEquals(other.normals) &&
                    uvs.contentEquals(other.uvs) &&
                    faces == other.faces &&
                    mtlFileName == other.mtlFileName
        }

        override fun hashCode(): Int {
            var result = vertices.contentHashCode()
            result = 31 * result + normals.contentHashCode()
            result = 31 * result + uvs.contentHashCode()
            result = 31 * result + faces.hashCode()
            result = 31 * result + (mtlFileName?.hashCode() ?: 0)
            return result
        }
    }

    private data class ObjFace(
        val vertices: List<ObjFaceVertex>,
        val material: String?
    )

    private data class ObjFaceVertex(
        val vertexIndex: Int,
        val uvIndex: Int?,
        val normalIndex: Int?
    )

    private data class MutableMaterial(
        val name: String,
        var diffuseColor: Color = Color.WHITE,
        var diffuseTexture: String? = null,
        var normalTexture: String? = null,
        var opacity: Float = 1f
    ) {
        fun toMaterial(): Material {
            return Material(
                name = name,
                diffuseColor = diffuseColor,
                diffuseTextureId = diffuseTexture,
                normalTextureId = normalTexture,
                opacity = opacity
            )
        }
    }
}