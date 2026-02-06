// app/src/main/java/com/bedrockconverter/model/Geometry.kt
package com.bedrockconverter.model

import java.util.UUID

/**
 * Complete geometry data for a 3D model
 */
data class Geometry(
    val id: String = UUID.randomUUID().toString(),
    val meshes: List<Mesh> = emptyList(),
    val bones: List<Bone> = emptyList(),
    val rootBoneIndex: Int = -1
) {
    /**
     * Combine all meshes into a single mesh (for simple export)
     */
    fun combinedMesh(): Mesh {
        if (meshes.isEmpty()) {
            return Mesh(name = "combined")
        }
        if (meshes.size == 1) {
            return meshes[0]
        }

        val allVertices = mutableListOf<Float>()
        val allNormals = mutableListOf<Float>()
        val allUvs = mutableListOf<Float>()
        val allIndices = mutableListOf<Int>()

        var indexOffset = 0

        for (mesh in meshes) {
            allVertices.addAll(mesh.vertices.toList())
            allNormals.addAll(mesh.normals.toList())
            allUvs.addAll(mesh.uvs.toList())

            for (index in mesh.indices) {
                allIndices.add(index + indexOffset)
            }

            indexOffset += mesh.vertices.size / 3
        }

        return Mesh(
            name = "combined",
            vertices = allVertices.toFloatArray(),
            normals = allNormals.toFloatArray(),
            uvs = allUvs.toFloatArray(),
            indices = allIndices.toIntArray()
        )
    }

    /**
     * Calculate total bounds for all meshes
     */
    fun calculateBounds(): BoundingBox {
        if (meshes.isEmpty()) {
            return BoundingBox(Vector3.ZERO, Vector3.ZERO)
        }

        val allVertices = meshes.flatMap { it.vertices.toList() }.toFloatArray()
        return BoundingBox.fromVertices(allVertices)
    }

    /**
     * Apply scale to all geometry
     */
    fun scaled(scale: Float): Geometry {
        return copy(
            meshes = meshes.map { it.scaled(scale) }
        )
    }
}

/**
 * Individual mesh within the geometry
 */
data class Mesh(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val vertices: FloatArray = floatArrayOf(),  // x, y, z per vertex
    val normals: FloatArray = floatArrayOf(),   // nx, ny, nz per vertex
    val uvs: FloatArray = floatArrayOf(),       // u, v per vertex
    val indices: IntArray = intArrayOf(),       // triangle indices
    val materialId: String? = null,
    val boneWeights: FloatArray? = null,        // For skeletal animation
    val boneIndices: IntArray? = null           // For skeletal animation
) {
    val vertexCount: Int get() = vertices.size / 3
    val triangleCount: Int get() = indices.size / 3
    val hasNormals: Boolean get() = normals.isNotEmpty()
    val hasUvs: Boolean get() = uvs.isNotEmpty()
    val hasBoneData: Boolean get() = boneWeights != null && boneIndices != null

    /**
     * Calculate bounding box for this mesh
     */
    fun calculateBounds(): BoundingBox {
        return BoundingBox.fromVertices(vertices)
    }

    /**
     * Generate normals if not present
     */
    fun withGeneratedNormals(): Mesh {
        if (hasNormals) return this

        val generatedNormals = FloatArray(vertices.size)

        for (i in indices.indices step 3) {
            val i0 = indices[i] * 3
            val i1 = indices[i + 1] * 3
            val i2 = indices[i + 2] * 3

            val v0 = Vector3(vertices[i0], vertices[i0 + 1], vertices[i0 + 2])
            val v1 = Vector3(vertices[i1], vertices[i1 + 1], vertices[i1 + 2])
            val v2 = Vector3(vertices[i2], vertices[i2 + 1], vertices[i2 + 2])

            val edge1 = v1 - v0
            val edge2 = v2 - v0

            // Cross product
            val normal = Vector3(
                edge1.y * edge2.z - edge1.z * edge2.y,
                edge1.z * edge2.x - edge1.x * edge2.z,
                edge1.x * edge2.y - edge1.y * edge2.x
            ).normalized()

            // Add to vertex normals (for averaging)
            for (idx in listOf(i0, i1, i2)) {
                generatedNormals[idx] += normal.x
                generatedNormals[idx + 1] += normal.y
                generatedNormals[idx + 2] += normal.z
            }
        }

        // Normalize all normals
        for (i in generatedNormals.indices step 3) {
            val len = kotlin.math.sqrt(
                generatedNormals[i] * generatedNormals[i] +
                        generatedNormals[i + 1] * generatedNormals[i + 1] +
                        generatedNormals[i + 2] * generatedNormals[i + 2]
            )
            if (len > 0) {
                generatedNormals[i] /= len
                generatedNormals[i + 1] /= len
                generatedNormals[i + 2] /= len
            }
        }

        return copy(normals = generatedNormals)
    }

    /**
     * Apply scale transformation
     */
    fun scaled(scale: Float): Mesh {
        val scaledVertices = FloatArray(vertices.size)
        for (i in vertices.indices) {
            scaledVertices[i] = vertices[i] * scale
        }
        return copy(vertices = scaledVertices)
    }

    /**
     * Flip UV V coordinate (some formats use different UV origin)
     */
    fun withFlippedUVs(): Mesh {
        if (!hasUvs) return this

        val flippedUvs = FloatArray(uvs.size)
        for (i in uvs.indices step 2) {
            flippedUvs[i] = uvs[i]         // U stays the same
            flippedUvs[i + 1] = 1f - uvs[i + 1]  // V is flipped
        }
        return copy(uvs = flippedUvs)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Mesh

        if (id != other.id) return false
        if (name != other.name) return false
        if (!vertices.contentEquals(other.vertices)) return false
        if (!normals.contentEquals(other.normals)) return false
        if (!uvs.contentEquals(other.uvs)) return false
        if (!indices.contentEquals(other.indices)) return false
        if (materialId != other.materialId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + vertices.contentHashCode()
        result = 31 * result + normals.contentHashCode()
        result = 31 * result + uvs.contentHashCode()
        result = 31 * result + indices.contentHashCode()
        result = 31 * result + (materialId?.hashCode() ?: 0)
        return result
    }
}

/**
 * Bone for skeletal structure (used in Bedrock entity rigging)
 */
data class Bone(
    val name: String,
    val parentIndex: Int = -1,
    val pivot: Vector3 = Vector3.ZERO,
    val rotation: Vector3 = Vector3.ZERO,
    val bindPose: Transform = Transform.IDENTITY,
    val cubes: List<Cube> = emptyList()
)

/**
 * Cube geometry for Bedrock format
 * Minecraft Bedrock uses cube-based geometry
 */
data class Cube(
    val origin: Vector3,
    val size: Vector3,
    val pivot: Vector3 = Vector3.ZERO,
    val rotation: Vector3 = Vector3.ZERO,
    val uv: CubeUV = CubeUV(),
    val inflate: Float = 0f,
    val mirror: Boolean = false
)

/**
 * UV mapping for a cube (per-face or box UV)
 */
data class CubeUV(
    val north: FaceUV? = null,
    val south: FaceUV? = null,
    val east: FaceUV? = null,
    val west: FaceUV? = null,
    val up: FaceUV? = null,
    val down: FaceUV? = null,
    // Alternative: box UV (single offset)
    val boxUV: Vector2? = null
) {
    val isBoxUV: Boolean get() = boxUV != null
}

/**
 * UV coordinates for a single face
 */
data class FaceUV(
    val uv: Vector2,
    val uvSize: Vector2
)

/**
 * Bedrock-specific geometry format
 */
data class BedrockGeometry(
    val formatVersion: String = "1.12.0",
    val identifier: String,
    val textureWidth: Int = 64,
    val textureHeight: Int = 64,
    val visibleBoundsWidth: Float = 1f,
    val visibleBoundsHeight: Float = 1f,
    val visibleBoundsOffset: Vector3 = Vector3.ZERO,
    val bones: List<BedrockBone> = emptyList()
) {
    fun toJsonMap(): Map<String, Any> {
        return mapOf(
            "format_version" to formatVersion,
            "minecraft:geometry" to listOf(
                mapOf(
                    "description" to mapOf(
                        "identifier" to identifier,
                        "texture_width" to textureWidth,
                        "texture_height" to textureHeight,
                        "visible_bounds_width" to visibleBoundsWidth,
                        "visible_bounds_height" to visibleBoundsHeight,
                        "visible_bounds_offset" to listOf(
                            visibleBoundsOffset.x,
                            visibleBoundsOffset.y,
                            visibleBoundsOffset.z
                        )
                    ),
                    "bones" to bones.map { it.toJsonMap() }
                )
            )
        )
    }
}

/**
 * Bone in Bedrock format
 */
data class BedrockBone(
    val name: String,
    val parent: String? = null,
    val pivot: Vector3 = Vector3.ZERO,
    val rotation: Vector3 = Vector3.ZERO,
    val cubes: List<BedrockCube> = emptyList()
) {
    fun toJsonMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "name" to name,
            "pivot" to listOf(pivot.x, pivot.y, pivot.z)
        )

        parent?.let { map["parent"] = it }

        if (rotation != Vector3.ZERO) {
            map["rotation"] = listOf(rotation.x, rotation.y, rotation.z)
        }

        if (cubes.isNotEmpty()) {
            map["cubes"] = cubes.map { it.toJsonMap() }
        }

        return map
    }
}

/**
 * Cube in Bedrock format
 */
data class BedrockCube(
    val origin: Vector3,
    val size: Vector3,
    val pivot: Vector3? = null,
    val rotation: Vector3? = null,
    val uv: Any, // Can be Vector2 for box UV or Map for per-face UV
    val inflate: Float? = null,
    val mirror: Boolean? = null
) {
    fun toJsonMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "origin" to listOf(origin.x, origin.y, origin.z),
            "size" to listOf(size.x, size.y, size.z),
            "uv" to when (uv) {
                is Vector2 -> listOf(uv.u, uv.v)
                is Map<*, *> -> uv
                else -> listOf(0, 0)
            }
        )

        pivot?.let { map["pivot"] = listOf(it.x, it.y, it.z) }
        rotation?.let { map["rotation"] = listOf(it.x, it.y, it.z) }
        inflate?.let { map["inflate"] = it }
        mirror?.let { map["mirror"] = it }

        return map
    }
}