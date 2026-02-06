// app/src/main/java/com/bedrockconverter/export/GeometryGenerator.kt
package com.bedrockconverter.export

import com.bedrockconverter.model.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Generates Minecraft Bedrock geometry from 3D mesh data
 * Converts triangular meshes to cube-based geometry
 */
class GeometryGenerator {

    companion object {
        private const val MIN_CUBE_SIZE = 0.1f
        private const val VOXEL_RESOLUTION = 16 // Voxels per unit
    }

    /**
     * Generate Bedrock geometry from mesh geometry
     */
    fun generate(
        geometry: Geometry,
        identifier: String,
        textureWidth: Int,
        textureHeight: Int,
        bounds: BoundingBox?
    ): BedrockGeometry {
        val mesh = geometry.combinedMesh()
        val actualBounds = bounds ?: geometry.calculateBounds()

        // Convert mesh to cubes
        val cubes = meshToCubes(mesh, actualBounds)

        // Create root bone with all cubes
        val rootBone = BedrockBone(
            name = "root",
            pivot = Vector3(0f, 0f, 0f),
            cubes = cubes
        )

        return BedrockGeometry(
            identifier = identifier,
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            visibleBoundsWidth = actualBounds.width + 1f,
            visibleBoundsHeight = actualBounds.height + 1f,
            visibleBoundsOffset = Vector3(0f, actualBounds.height / 2f, 0f),
            bones = listOf(rootBone)
        )
    }

    /**
     * Convert mesh to Bedrock cubes using voxelization
     */
    private fun meshToCubes(mesh: Mesh, bounds: BoundingBox): List<BedrockCube> {
        if (mesh.vertices.isEmpty()) {
            return listOf(createDefaultCube())
        }

        // Voxelize the mesh
        val voxels = voxelizeMesh(mesh, bounds)

        // Convert voxels to cubes with optimization (merge adjacent voxels)
        val optimizedCubes = optimizeVoxels(voxels, bounds)

        // Convert to Bedrock cubes
        return optimizedCubes.map { voxelCube ->
            BedrockCube(
                origin = voxelCube.origin,
                size = voxelCube.size,
                uv = calculateBoxUV(voxelCube, bounds)
            )
        }
    }

    /**
     * Voxelize a mesh into a 3D grid
     */
    private fun voxelizeMesh(mesh: Mesh, bounds: BoundingBox): Set<VoxelCoord> {
        val voxels = mutableSetOf<VoxelCoord>()
        val voxelSize = 1f / VOXEL_RESOLUTION

        // Process each triangle
        for (i in mesh.indices.indices step 3) {
            if (i + 2 >= mesh.indices.size) break

            val i0 = mesh.indices[i] * 3
            val i1 = mesh.indices[i + 1] * 3
            val i2 = mesh.indices[i + 2] * 3

            if (i0 + 2 >= mesh.vertices.size ||
                i1 + 2 >= mesh.vertices.size ||
                i2 + 2 >= mesh.vertices.size) continue

            val v0 = Vector3(mesh.vertices[i0], mesh.vertices[i0 + 1], mesh.vertices[i0 + 2])
            val v1 = Vector3(mesh.vertices[i1], mesh.vertices[i1 + 1], mesh.vertices[i1 + 2])
            val v2 = Vector3(mesh.vertices[i2], mesh.vertices[i2 + 1], mesh.vertices[i2 + 2])

            // Voxelize triangle
            voxelizeTriangle(v0, v1, v2, bounds, voxelSize, voxels)
        }

        return voxels
    }

    /**
     * Voxelize a single triangle
     */
    private fun voxelizeTriangle(
        v0: Vector3,
        v1: Vector3,
        v2: Vector3,
        bounds: BoundingBox,
        voxelSize: Float,
        voxels: MutableSet<VoxelCoord>
    ) {
        // Get triangle bounding box in voxel coordinates
        val minX = floor(minOf(v0.x, v1.x, v2.x) / voxelSize).toInt()
        val maxX = ceil(maxOf(v0.x, v1.x, v2.x) / voxelSize).toInt()
        val minY = floor(minOf(v0.y, v1.y, v2.y) / voxelSize).toInt()
        val maxY = ceil(maxOf(v0.y, v1.y, v2.y) / voxelSize).toInt()
        val minZ = floor(minOf(v0.z, v1.z, v2.z) / voxelSize).toInt()
        val maxZ = ceil(maxOf(v0.z, v1.z, v2.z) / voxelSize).toInt()

        // Check each voxel in the bounding box
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val voxelCenter = Vector3(
                        (x + 0.5f) * voxelSize,
                        (y + 0.5f) * voxelSize,
                        (z + 0.5f) * voxelSize
                    )

                    // Simple check: if voxel center is close to triangle plane
                    if (isVoxelNearTriangle(voxelCenter, v0, v1, v2, voxelSize)) {
                        voxels.add(VoxelCoord(x, y, z))
                    }
                }
            }
        }
    }

    /**
     * Check if a voxel is near a triangle
     */
    private fun isVoxelNearTriangle(
        point: Vector3,
        v0: Vector3,
        v1: Vector3,
        v2: Vector3,
        voxelSize: Float
    ): Boolean {
        // Calculate distance to triangle using barycentric coordinates
        val edge1 = v1 - v0
        val edge2 = v2 - v0
        val h = cross(Vector3(0f, 0f, 1f), edge2)
        val a = dot(edge1, h)

        if (abs(a) < 0.0001f) {
            // Ray parallel to triangle, use distance to edges
            return pointToTriangleDistance(point, v0, v1, v2) < voxelSize * 1.5f
        }

        return pointToTriangleDistance(point, v0, v1, v2) < voxelSize * 1.5f
    }

    private fun pointToTriangleDistance(p: Vector3, v0: Vector3, v1: Vector3, v2: Vector3): Float {
        // Simplified distance calculation
        val center = Vector3(
            (v0.x + v1.x + v2.x) / 3f,
            (v0.y + v1.y + v2.y) / 3f,
            (v0.z + v1.z + v2.z) / 3f
        )
        return (p - center).length()
    }

    private fun cross(a: Vector3, b: Vector3): Vector3 {
        return Vector3(
            a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z,
            a.x * b.y - a.y * b.x
        )
    }

    private fun dot(a: Vector3, b: Vector3): Float {
        return a.x * b.x + a.y * b.y + a.z * b.z
    }

    /**
     * Optimize voxels by merging adjacent ones into larger cubes
     */
    private fun optimizeVoxels(voxels: Set<VoxelCoord>, bounds: BoundingBox): List<VoxelCube> {
        if (voxels.isEmpty()) {
            return listOf(VoxelCube(
                origin = Vector3(-0.5f, 0f, -0.5f),
                size = Vector3(1f, 1f, 1f)
            ))
        }

        val voxelSize = 1f / VOXEL_RESOLUTION
        val cubes = mutableListOf<VoxelCube>()
        val processed = mutableSetOf<VoxelCoord>()

        // Sort voxels for consistent processing
        val sortedVoxels = voxels.sortedWith(compareBy({ it.y }, { it.z }, { it.x }))

        for (voxel in sortedVoxels) {
            if (voxel in processed) continue

            // Try to extend this voxel into a larger cube
            val cube = extendVoxel(voxel, voxels, processed, voxelSize)
            cubes.add(cube)
        }

        return cubes
    }

    /**
     * Extend a voxel into a larger cube by merging neighbors
     */
    private fun extendVoxel(
        start: VoxelCoord,
        voxels: Set<VoxelCoord>,
        processed: MutableSet<VoxelCoord>,
        voxelSize: Float
    ): VoxelCube {
        var maxX = start.x
        var maxY = start.y
        var maxZ = start.z

        // Extend in X direction
        while (VoxelCoord(maxX + 1, start.y, start.z) in voxels &&
            VoxelCoord(maxX + 1, start.y, start.z) !in processed) {
            maxX++
        }

        // Extend in Y direction (check all X positions)
        var canExtendY = true
        while (canExtendY) {
            for (x in start.x..maxX) {
                if (VoxelCoord(x, maxY + 1, start.z) !in voxels ||
                    VoxelCoord(x, maxY + 1, start.z) in processed) {
                    canExtendY = false
                    break
                }
            }
            if (canExtendY) maxY++
        }

        // Extend in Z direction (check all X and Y positions)
        var canExtendZ = true
        while (canExtendZ) {
            for (x in start.x..maxX) {
                for (y in start.y..maxY) {
                    if (VoxelCoord(x, y, maxZ + 1) !in voxels ||
                        VoxelCoord(x, y, maxZ + 1) in processed) {
                        canExtendZ = false
                        break
                    }
                }
                if (!canExtendZ) break
            }
            if (canExtendZ) maxZ++
        }

        // Mark all voxels in this cube as processed
        for (x in start.x..maxX) {
            for (y in start.y..maxY) {
                for (z in start.z..maxZ) {
                    processed.add(VoxelCoord(x, y, z))
                }
            }
        }

        // Convert to world coordinates
        val origin = Vector3(
            start.x * voxelSize,
            start.y * voxelSize,
            start.z * voxelSize
        )

        val size = Vector3(
            (maxX - start.x + 1) * voxelSize,
            (maxY - start.y + 1) * voxelSize,
            (maxZ - start.z + 1) * voxelSize
        )

        return VoxelCube(origin, size)
    }

    /**
     * Calculate box UV mapping for a cube
     */
    private fun calculateBoxUV(cube: VoxelCube, bounds: BoundingBox): Vector2 {
        // Simple UV mapping based on position
        val u = ((cube.origin.x - bounds.min.x) / bounds.width * 64).toInt().coerceIn(0, 63)
        val v = ((cube.origin.y - bounds.min.y) / bounds.height * 64).toInt().coerceIn(0, 63)
        return Vector2(u.toFloat(), v.toFloat())
    }

    /**
     * Create a default cube when mesh is empty
     */
    private fun createDefaultCube(): BedrockCube {
        return BedrockCube(
            origin = Vector3(-8f, 0f, -8f),
            size = Vector3(16f, 16f, 16f),
            uv = Vector2(0f, 0f)
        )
    }

    /**
     * Generate geometry JSON string
     */
    fun generateJson(geometry: BedrockGeometry): String {
        return buildString {
            append("{\n")
            append("  \"format_version\": \"${geometry.formatVersion}\",\n")
            append("  \"minecraft:geometry\": [\n")
            append("    {\n")
            append("      \"description\": {\n")
            append("        \"identifier\": \"${geometry.identifier}\",\n")
            append("        \"texture_width\": ${geometry.textureWidth},\n")
            append("        \"texture_height\": ${geometry.textureHeight},\n")
            append("        \"visible_bounds_width\": ${geometry.visibleBoundsWidth},\n")
            append("        \"visible_bounds_height\": ${geometry.visibleBoundsHeight},\n")
            append("        \"visible_bounds_offset\": [${geometry.visibleBoundsOffset.x}, ${geometry.visibleBoundsOffset.y}, ${geometry.visibleBoundsOffset.z}]\n")
            append("      },\n")
            append("      \"bones\": [\n")

            geometry.bones.forEachIndexed { boneIndex, bone ->
                append("        {\n")
                append("          \"name\": \"${bone.name}\",\n")
                append("          \"pivot\": [${bone.pivot.x}, ${bone.pivot.y}, ${bone.pivot.z}]")

                if (bone.parent != null) {
                    append(",\n          \"parent\": \"${bone.parent}\"")
                }

                if (bone.rotation != Vector3.ZERO) {
                    append(",\n          \"rotation\": [${bone.rotation.x}, ${bone.rotation.y}, ${bone.rotation.z}]")
                }

                if (bone.cubes.isNotEmpty()) {
                    append(",\n          \"cubes\": [\n")

                    bone.cubes.forEachIndexed { cubeIndex, cube ->
                        append("            {\n")
                        append("              \"origin\": [${cube.origin.x}, ${cube.origin.y}, ${cube.origin.z}],\n")
                        append("              \"size\": [${cube.size.x}, ${cube.size.y}, ${cube.size.z}],\n")

                        when (val uv = cube.uv) {
                            is Vector2 -> append("              \"uv\": [${uv.u}, ${uv.v}]")
                            else -> append("              \"uv\": [0, 0]")
                        }

                        cube.pivot?.let {
                            append(",\n              \"pivot\": [${it.x}, ${it.y}, ${it.z}]")
                        }

                        cube.rotation?.let {
                            append(",\n              \"rotation\": [${it.x}, ${it.y}, ${it.z}]")
                        }

                        cube.inflate?.let {
                            append(",\n              \"inflate\": $it")
                        }

                        cube.mirror?.let {
                            append(",\n              \"mirror\": $it")
                        }

                        append("\n            }")
                        if (cubeIndex < bone.cubes.size - 1) append(",")
                        append("\n")
                    }

                    append("          ]")
                }

                append("\n        }")
                if (boneIndex < geometry.bones.size - 1) append(",")
                append("\n")
            }

            append("      ]\n")
            append("    }\n")
            append("  ]\n")
            append("}")
        }
    }

    // Helper data classes
    private data class VoxelCoord(val x: Int, val y: Int, val z: Int)
    private data class VoxelCube(val origin: Vector3, val size: Vector3)
}