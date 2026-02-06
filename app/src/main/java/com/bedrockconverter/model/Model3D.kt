// app/src/main/java/com/bedrockconverter/model/Model3D.kt
package com.bedrockconverter.model

import android.net.Uri
import java.util.UUID
import kotlin.math.withSign

/**
 * Represents a 3D model imported into the application
 */
data class Model3D(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val format: String, // glb, gltf, obj, fbx
    val sourceUri: Uri,
    val geometry: Geometry,
    val textures: List<Texture> = emptyList(),
    val materials: List<Material> = emptyList(),
    val bounds: BoundingBox? = null,
    val pivot: Vector3 = Vector3.ZERO,
    val importedAt: Long = System.currentTimeMillis()
) {
    val vertexCount: Int
        get() = geometry.meshes.sumOf { it.vertices.size / 3 }

    val triangleCount: Int
        get() = geometry.meshes.sumOf { it.indices.size / 3 }

    val textureCount: Int
        get() = textures.size

    val hasTextures: Boolean
        get() = textures.isNotEmpty()

    val hasBones: Boolean
        get() = geometry.bones.isNotEmpty()
}

/**
 * 3D Vector representation
 */
data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val ONE = Vector3(1f, 1f, 1f)
        val UP = Vector3(0f, 1f, 0f)
        val FORWARD = Vector3(0f, 0f, 1f)
        val RIGHT = Vector3(1f, 0f, 0f)
    }

    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Vector3(x / scalar, y / scalar, z / scalar)

    fun toFloatArray() = floatArrayOf(x, y, z)

    fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 0) this / len else ZERO
    }
}

/**
 * 2D Vector for UV coordinates
 */
data class Vector2(
    val u: Float,
    val v: Float
) {
    companion object {
        val ZERO = Vector2(0f, 0f)
    }

    fun toFloatArray() = floatArrayOf(u, v)
}

/**
 * Quaternion for rotations
 */
data class Quaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float
) {
    companion object {
        val IDENTITY = Quaternion(0f, 0f, 0f, 1f)

        fun fromEulerAngles(pitch: Float, yaw: Float, roll: Float): Quaternion {
            val cy = kotlin.math.cos(yaw * 0.5f)
            val sy = kotlin.math.sin(yaw * 0.5f)
            val cp = kotlin.math.cos(pitch * 0.5f)
            val sp = kotlin.math.sin(pitch * 0.5f)
            val cr = kotlin.math.cos(roll * 0.5f)
            val sr = kotlin.math.sin(roll * 0.5f)

            return Quaternion(
                x = sr * cp * cy - cr * sp * sy,
                y = cr * sp * cy + sr * cp * sy,
                z = cr * cp * sy - sr * sp * cy,
                w = cr * cp * cy + sr * sp * sy
            )
        }
    }

    fun toEulerAngles(): Vector3 {
        val sinrCosp = 2 * (w * x + y * z)
        val cosrCosp = 1 - 2 * (x * x + y * y)
        val roll = kotlin.math.atan2(sinrCosp, cosrCosp)

        val sinp = 2 * (w * y - z * x)
        val pitch = if (kotlin.math.abs(sinp) >= 1) {
            (Math.PI.toFloat() / 2).withSign(sinp)
        } else {
            kotlin.math.asin(sinp)
        }

        val sinyCosp = 2 * (w * z + x * y)
        val cosyCosp = 1 - 2 * (y * y + z * z)
        val yaw = kotlin.math.atan2(sinyCosp, cosyCosp)

        return Vector3(
            Math.toDegrees(pitch.toDouble()).toFloat(),
            Math.toDegrees(yaw.toDouble()).toFloat(),
            Math.toDegrees(roll.toDouble()).toFloat()
        )
    }

    fun toFloatArray() = floatArrayOf(x, y, z, w)
}

/**
 * Bounding box for collision and sizing
 */
data class BoundingBox(
    val min: Vector3,
    val max: Vector3
) {
    val width: Float get() = max.x - min.x
    val height: Float get() = max.y - min.y
    val depth: Float get() = max.z - min.z

    val center: Vector3
        get() = Vector3(
            (min.x + max.x) / 2f,
            (min.y + max.y) / 2f,
            (min.z + max.z) / 2f
        )

    val size: Vector3
        get() = Vector3(width, height, depth)

    companion object {
        fun fromVertices(vertices: FloatArray): BoundingBox {
            if (vertices.isEmpty()) {
                return BoundingBox(Vector3.ZERO, Vector3.ZERO)
            }

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            var maxZ = Float.MIN_VALUE

            for (i in vertices.indices step 3) {
                val x = vertices[i]
                val y = vertices[i + 1]
                val z = vertices[i + 2]

                minX = minOf(minX, x)
                minY = minOf(minY, y)
                minZ = minOf(minZ, z)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
                maxZ = maxOf(maxZ, z)
            }

            return BoundingBox(
                min = Vector3(minX, minY, minZ),
                max = Vector3(maxX, maxY, maxZ)
            )
        }
    }

    fun scaled(scale: Float): BoundingBox {
        return BoundingBox(
            min = min * scale,
            max = max * scale
        )
    }
}

/**
 * Texture data
 */
data class Texture(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val width: Int,
    val height: Int,
    val data: ByteArray, // PNG or JPG data
    val format: TextureFormat = TextureFormat.PNG,
    val type: TextureType = TextureType.DIFFUSE
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Texture

        if (id != other.id) return false
        if (name != other.name) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!data.contentEquals(other.data)) return false
        if (format != other.format) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

enum class TextureFormat {
    PNG,
    JPG,
    TGA
}

enum class TextureType {
    DIFFUSE,
    NORMAL,
    SPECULAR,
    EMISSIVE,
    OPACITY
}

/**
 * Material definition
 */
data class Material(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val diffuseColor: Color = Color.WHITE,
    val diffuseTextureId: String? = null,
    val normalTextureId: String? = null,
    val specularTextureId: String? = null,
    val emissiveTextureId: String? = null,
    val opacity: Float = 1f,
    val metallic: Float = 0f,
    val roughness: Float = 0.5f
)

/**
 * RGBA Color
 */
data class Color(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1f
) {
    companion object {
        val WHITE = Color(1f, 1f, 1f, 1f)
        val BLACK = Color(0f, 0f, 0f, 1f)
        val RED = Color(1f, 0f, 0f, 1f)
        val GREEN = Color(0f, 1f, 0f, 1f)
        val BLUE = Color(0f, 0f, 1f, 1f)
        val TRANSPARENT = Color(0f, 0f, 0f, 0f)
    }

    fun toFloatArray() = floatArrayOf(r, g, b, a)

    fun toIntColor(): Int {
        val red = (r * 255).toInt().coerceIn(0, 255)
        val green = (g * 255).toInt().coerceIn(0, 255)
        val blue = (b * 255).toInt().coerceIn(0, 255)
        val alpha = (a * 255).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}

/**
 * Transform data for bones/nodes
 */
data class Transform(
    val position: Vector3 = Vector3.ZERO,
    val rotation: Quaternion = Quaternion.IDENTITY,
    val scale: Vector3 = Vector3.ONE
) {
    companion object {
        val IDENTITY = Transform()
    }

    fun toMatrix(): FloatArray {
        val matrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(matrix, 0)
        android.opengl.Matrix.translateM(matrix, 0, position.x, position.y, position.z)
        // Apply rotation
        val rotMatrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(rotMatrix, 0)
        // Convert quaternion to rotation matrix
        val euler = rotation.toEulerAngles()
        android.opengl.Matrix.rotateM(rotMatrix, 0, euler.x, 1f, 0f, 0f)
        android.opengl.Matrix.rotateM(rotMatrix, 0, euler.y, 0f, 1f, 0f)
        android.opengl.Matrix.rotateM(rotMatrix, 0, euler.z, 0f, 0f, 1f)
        android.opengl.Matrix.multiplyMM(matrix, 0, matrix, 0, rotMatrix, 0)
        android.opengl.Matrix.scaleM(matrix, 0, scale.x, scale.y, scale.z)
        return matrix
    }
}