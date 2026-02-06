// app/src/main/java/com/bedrockconverter/utils/CoordinateConverter.kt
package com.bedrockconverter.utils

import com.bedrockconverter.model.*

/**
 * Converts coordinate systems between different 3D formats and Minecraft Bedrock
 * 
 * Common coordinate systems:
 * - Blender/glTF: Y-up, right-handed
 * - 3ds Max/FBX: Z-up, right-handed
 * - Unity: Y-up, left-handed
 * - Minecraft Bedrock: Y-up, right-handed (but with specific conventions)
 * 
 * Minecraft Bedrock specifics:
 * - Origin at entity's feet
 * - Y is up
 * - Z is forward (facing direction)
 * - X is right
 * - 1 unit = 1 pixel (16 pixels = 1 block)
 */
class CoordinateConverter {

    companion object {
        // Minecraft uses 16 pixels per block
        const val PIXELS_PER_BLOCK = 16f
        
        // Default scale factor for converting units
        const val DEFAULT_SCALE = 1f
    }

    /**
     * Convert a model to Minecraft Bedrock coordinate system
     */
    fun convertToMinecraft(model: Model3D): Model3D {
        val convertedGeometry = convertGeometry(model.geometry)
        val convertedBounds = model.bounds?.let { convertBoundingBox(it) }
        val convertedPivot = convertVector(model.pivot)

        return model.copy(
            geometry = convertedGeometry,
            bounds = convertedBounds,
            pivot = convertedPivot
        )
    }

    /**
     * Convert geometry to Minecraft coordinate system
     */
    private fun convertGeometry(geometry: Geometry): Geometry {
        val convertedMeshes = geometry.meshes.map { mesh ->
            convertMesh(mesh)
        }

        val convertedBones = geometry.bones.map { bone ->
            convertBone(bone)
        }

        return geometry.copy(
            meshes = convertedMeshes,
            bones = convertedBones
        )
    }

    /**
     * Convert a mesh to Minecraft coordinate system
     */
    private fun convertMesh(mesh: Mesh): Mesh {
        // Convert vertices
        val convertedVertices = FloatArray(mesh.vertices.size)
        for (i in mesh.vertices.indices step 3) {
            val converted = convertCoordinates(
                mesh.vertices[i],
                mesh.vertices[i + 1],
                mesh.vertices[i + 2]
            )
            convertedVertices[i] = converted.x
            convertedVertices[i + 1] = converted.y
            convertedVertices[i + 2] = converted.z
        }

        // Convert normals
        val convertedNormals = if (mesh.normals.isNotEmpty()) {
            FloatArray(mesh.normals.size).also { normals ->
                for (i in mesh.normals.indices step 3) {
                    val converted = convertNormal(
                        mesh.normals[i],
                        mesh.normals[i + 1],
                        mesh.normals[i + 2]
                    )
                    normals[i] = converted.x
                    normals[i + 1] = converted.y
                    normals[i + 2] = converted.z
                }
            }
        } else {
            mesh.normals
        }

        // Flip UVs if needed (Minecraft uses top-left origin)
        val convertedUvs = if (mesh.uvs.isNotEmpty()) {
            FloatArray(mesh.uvs.size).also { uvs ->
                for (i in mesh.uvs.indices step 2) {
                    uvs[i] = mesh.uvs[i]
                    uvs[i + 1] = 1f - mesh.uvs[i + 1] // Flip V
                }
            }
        } else {
            mesh.uvs
        }

        // Reverse winding order if coordinate system handedness changed
        val convertedIndices = reverseWindingOrder(mesh.indices)

        return mesh.copy(
            vertices = convertedVertices,
            normals = convertedNormals,
            uvs = convertedUvs,
            indices = convertedIndices
        )
    }

    /**
     * Convert a bone to Minecraft coordinate system
     */
    private fun convertBone(bone: Bone): Bone {
        val convertedPivot = convertVector(bone.pivot)
        val convertedRotation = convertRotation(bone.rotation)
        val convertedCubes = bone.cubes.map { cube ->
            convertCube(cube)
        }

        return bone.copy(
            pivot = convertedPivot,
            rotation = convertedRotation,
            cubes = convertedCubes
        )
    }

    /**
     * Convert a cube to Minecraft coordinate system
     */
    private fun convertCube(cube: Cube): Cube {
        val convertedOrigin = convertVector(cube.origin)
        val convertedPivot = convertVector(cube.pivot)
        val convertedRotation = convertRotation(cube.rotation)

        // Size doesn't need axis swapping, just ensure positive values
        val convertedSize = Vector3(
            kotlin.math.abs(cube.size.x),
            kotlin.math.abs(cube.size.y),
            kotlin.math.abs(cube.size.z)
        )

        return cube.copy(
            origin = convertedOrigin,
            size = convertedSize,
            pivot = convertedPivot,
            rotation = convertedRotation
        )
    }

    /**
     * Convert coordinates from common formats to Minecraft
     * Most 3D software uses Y-up, so we mainly handle Z-up cases
     */
    private fun convertCoordinates(x: Float, y: Float, z: Float): Vector3 {
        // Standard Y-up to Minecraft Y-up (no change needed for axis)
        // But we might need to adjust origin or scale
        return Vector3(x, y, z)
    }

    /**
     * Convert from Z-up coordinate system (like 3ds Max, FBX)
     */
    fun convertFromZUp(x: Float, y: Float, z: Float): Vector3 {
        // Z-up to Y-up: swap Y and Z, negate new Z
        return Vector3(x, z, -y)
    }

    /**
     * Convert from left-handed coordinate system (like Unity)
     */
    fun convertFromLeftHanded(x: Float, y: Float, z: Float): Vector3 {
        // Negate Z to convert handedness
        return Vector3(x, y, -z)
    }

    /**
     * Convert a normal vector
     */
    private fun convertNormal(x: Float, y: Float, z: Float): Vector3 {
        return convertCoordinates(x, y, z).normalized()
    }

    /**
     * Convert rotation angles
     */
    private fun convertRotation(rotation: Vector3): Vector3 {
        // Minecraft uses degrees, most software uses degrees too
        // Adjust for coordinate system differences if needed
        return rotation
    }

    /**
     * Convert a Vector3
     */
    private fun convertVector(vector: Vector3): Vector3 {
        return convertCoordinates(vector.x, vector.y, vector.z)
    }

    /**
     * Convert a bounding box
     */
    private fun convertBoundingBox(bounds: BoundingBox): BoundingBox {
        val min = convertVector(bounds.min)
        val max = convertVector(bounds.max)

        // Ensure min is actually minimum after conversion
        return BoundingBox(
            min = Vector3(
                minOf(min.x, max.x),
                minOf(min.y, max.y),
                minOf(min.z, max.z)
            ),
            max = Vector3(
                maxOf(min.x, max.x),
                maxOf(min.y, max.y),
                maxOf(min.z, max.z)
            )
        )
    }

    /**
     * Reverse winding order of triangles
     */
    private fun reverseWindingOrder(indices: IntArray): IntArray {
        val result = IntArray(indices.size)
        for (i in indices.indices step 3) {
            if (i + 2 < indices.size) {
                result[i] = indices[i]
                result[i + 1] = indices[i + 2]
                result[i + 2] = indices[i + 1]
            }
        }
        return result
    }

    /**
     * Convert world units to Minecraft pixels
     */
    fun worldToPixels(value: Float, scale: Float = DEFAULT_SCALE): Float {
        return value * PIXELS_PER_BLOCK * scale
    }

    /**
     * Convert Minecraft pixels to world units
     */
    fun pixelsToWorld(pixels: Float, scale: Float = DEFAULT_SCALE): Float {
        return pixels / (PIXELS_PER_BLOCK * scale)
    }

    /**
     * Convert world units to Minecraft blocks
     */
    fun worldToBlocks(value: Float, scale: Float = DEFAULT_SCALE): Float {
        return value * scale
    }

    /**
     * Center geometry at origin
     */
    fun centerGeometry(geometry: Geometry): Geometry {
        val bounds = geometry.calculateBounds()
        val center = bounds.center

        val centeredMeshes = geometry.meshes.map { mesh ->
            val centeredVertices = FloatArray(mesh.vertices.size)
            for (i in mesh.vertices.indices step 3) {
                centeredVertices[i] = mesh.vertices[i] - center.x
                centeredVertices[i + 1] = mesh.vertices[i + 1] - center.y
                centeredVertices[i + 2] = mesh.vertices[i + 2] - center.z
            }
            mesh.copy(vertices = centeredVertices)
        }

        return geometry.copy(meshes = centeredMeshes)
    }

    /**
     * Move geometry so the bottom is at Y=0
     */
    fun groundGeometry(geometry: Geometry): Geometry {
        val bounds = geometry.calculateBounds()
        val groundOffset = bounds.min.y

        val groundedMeshes = geometry.meshes.map { mesh ->
            val groundedVertices = FloatArray(mesh.vertices.size)
            for (i in mesh.vertices.indices step 3) {
                groundedVertices[i] = mesh.vertices[i]
                groundedVertices[i + 1] = mesh.vertices[i + 1] - groundOffset
                groundedVertices[i + 2] = mesh.vertices[i + 2]
            }
            mesh.copy(vertices = groundedVertices)
        }

        return geometry.copy(meshes = groundedMeshes)
    }

    /**
     * Apply transformation matrix to geometry
     */
    fun transformGeometry(geometry: Geometry, matrix: FloatArray): Geometry {
        val transformedMeshes = geometry.meshes.map { mesh ->
            val transformedVertices = FloatArray(mesh.vertices.size)
            val transformedNormals = FloatArray(mesh.normals.size)

            // Transform vertices
            for (i in mesh.vertices.indices step 3) {
                val result = transformPoint(
                    mesh.vertices[i],
                    mesh.vertices[i + 1],
                    mesh.vertices[i + 2],
                    matrix
                )
                transformedVertices[i] = result[0]
                transformedVertices[i + 1] = result[1]
                transformedVertices[i + 2] = result[2]
            }

            // Transform normals (using inverse transpose for correct normal transformation)
            if (mesh.normals.isNotEmpty()) {
                val normalMatrix = invertMatrix(matrix)
                transposeMatrix(normalMatrix)

                for (i in mesh.normals.indices step 3) {
                    val result = transformDirection(
                        mesh.normals[i],
                        mesh.normals[i + 1],
                        mesh.normals[i + 2],
                        normalMatrix
                    )
                    val length = kotlin.math.sqrt(result[0] * result[0] + result[1] * result[1] + result[2] * result[2])
                    if (length > 0) {
                        transformedNormals[i] = result[0] / length
                        transformedNormals[i + 1] = result[1] / length
                        transformedNormals[i + 2] = result[2] / length
                    }
                }
            }

            mesh.copy(
                vertices = transformedVertices,
                normals = transformedNormals
            )
        }

        return geometry.copy(meshes = transformedMeshes)
    }

    /**
     * Transform a point by a 4x4 matrix
     */
    private fun transformPoint(x: Float, y: Float, z: Float, matrix: FloatArray): FloatArray {
        val w = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15]
        return floatArrayOf(
            (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]) / w,
            (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]) / w,
            (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]) / w
        )
    }

    /**
     * Transform a direction by a 4x4 matrix (ignores translation)
     */
    private fun transformDirection(x: Float, y: Float, z: Float, matrix: FloatArray): FloatArray {
        return floatArrayOf(
            matrix[0] * x + matrix[4] * y + matrix[8] * z,
            matrix[1] * x + matrix[5] * y + matrix[9] * z,
            matrix[2] * x + matrix[6] * y + matrix[10] * z
        )
    }

    /**
     * Invert a 4x4 matrix
     */
    private fun invertMatrix(matrix: FloatArray): FloatArray {
        val result = FloatArray(16)
        android.opengl.Matrix.invertM(result, 0, matrix, 0)
        return result
    }

    /**
     * Transpose a 4x4 matrix in place
     */
    private fun transposeMatrix(matrix: FloatArray) {
        for (i in 0 until 4) {
            for (j in i + 1 until 4) {
                val temp = matrix[i * 4 + j]
                matrix[i * 4 + j] = matrix[j * 4 + i]
                matrix[j * 4 + i] = temp
            }
        }
    }

    /**
     * Detect coordinate system from model properties
     */
    fun detectCoordinateSystem(model: Model3D): CoordinateSystem {
        val bounds = model.bounds ?: return CoordinateSystem.Y_UP_RIGHT_HANDED

        // Heuristic: check which axis has the most variation for "up"
        val xRange = bounds.width
        val yRange = bounds.height
        val zRange = bounds.depth

        return when {
            zRange > yRange && zRange > xRange * 0.5f -> CoordinateSystem.Z_UP_RIGHT_HANDED
            else -> CoordinateSystem.Y_UP_RIGHT_HANDED
        }
    }

    enum class CoordinateSystem {
        Y_UP_RIGHT_HANDED,  // glTF, Blender
        Y_UP_LEFT_HANDED,   // Unity
        Z_UP_RIGHT_HANDED,  // 3ds Max, FBX
        Z_UP_LEFT_HANDED    // Some CAD software
    }
}