// app/src/main/java/com/bedrockconverter/renderer/Camera.kt
package com.bedrockconverter.renderer

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * Camera for 3D scene viewing
 * Supports orbit, pan, and zoom controls
 */
class Camera {

    // Camera position
    var positionX = 0f
        private set
    var positionY = 2f
        private set
    var positionZ = 5f
        private set

    // Target (look-at) position
    private var targetX = 0f
    private var targetY = 0f
    private var targetZ = 0f

    // Up vector
    private var upX = 0f
    private var upY = 1f
    private var upZ = 0f

    // Orbit parameters
    private var orbitDistance = 5f
    private var orbitAngleH = 0f  // Horizontal angle (around Y axis)
    private var orbitAngleV = 30f // Vertical angle (pitch)

    // Zoom parameters
    private var zoomLevel = 1f
    private var minZoom = 0.5f
    private var maxZoom = 5f

    // Pan offset
    private var panX = 0f
    private var panY = 0f

    /**
     * Get the view matrix
     */
    fun getViewMatrix(matrix: FloatArray) {
        updatePosition()
        Matrix.setLookAtM(
            matrix, 0,
            positionX, positionY, positionZ,
            targetX + panX, targetY + panY, targetZ,
            upX, upY, upZ
        )
    }

    /**
     * Update camera position based on orbit parameters
     */
    private fun updatePosition() {
        val distance = orbitDistance / zoomLevel

        // Convert angles to radians
        val hAngleRad = Math.toRadians(orbitAngleH.toDouble()).toFloat()
        val vAngleRad = Math.toRadians(orbitAngleV.toDouble()).toFloat()

        // Calculate position on sphere
        positionX = targetX + distance * cos(vAngleRad) * sin(hAngleRad)
        positionY = targetY + distance * sin(vAngleRad)
        positionZ = targetZ + distance * cos(vAngleRad) * cos(hAngleRad)
    }

    /**
     * Set camera position directly
     */
    fun setPosition(x: Float, y: Float, z: Float) {
        positionX = x
        positionY = y
        positionZ = z

        // Calculate orbit distance from position
        val dx = positionX - targetX
        val dy = positionY - targetY
        val dz = positionZ - targetZ
        orbitDistance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Set target (look-at) position
     */
    fun setTarget(x: Float, y: Float, z: Float) {
        targetX = x
        targetY = y
        targetZ = z
    }

    /**
     * Set zoom level
     */
    fun setZoom(zoom: Float) {
        zoomLevel = zoom.coerceIn(minZoom, maxZoom)
    }

    /**
     * Get current zoom level
     */
    fun getZoom(): Float = zoomLevel

    /**
     * Zoom in
     */
    fun zoomIn(factor: Float = 1.2f) {
        zoomLevel = (zoomLevel * factor).coerceIn(minZoom, maxZoom)
    }

    /**
     * Zoom out
     */
    fun zoomOut(factor: Float = 1.2f) {
        zoomLevel = (zoomLevel / factor).coerceIn(minZoom, maxZoom)
    }

    /**
     * Orbit horizontally (rotate around Y axis)
     */
    fun orbitHorizontal(angleDelta: Float) {
        orbitAngleH += angleDelta
        orbitAngleH %= 360f
    }

    /**
     * Orbit vertically (change pitch)
     */
    fun orbitVertical(angleDelta: Float) {
        orbitAngleV += angleDelta
        orbitAngleV = orbitAngleV.coerceIn(-89f, 89f)
    }

    /**
     * Pan camera
     */
    fun pan(deltaX: Float, deltaY: Float) {
        val scale = orbitDistance / zoomLevel * 0.01f
        panX += deltaX * scale
        panY += deltaY * scale
    }

    /**
     * Reset camera to default position
     */
    fun reset() {
        orbitAngleH = 0f
        orbitAngleV = 30f
        zoomLevel = 1f
        panX = 0f
        panY = 0f
    }

    /**
     * Set orbit angles directly
     */
    fun setOrbitAngles(horizontal: Float, vertical: Float) {
        orbitAngleH = horizontal % 360f
        orbitAngleV = vertical.coerceIn(-89f, 89f)
    }

    /**
     * Get orbit angles
     */
    fun getOrbitAngles(): Pair<Float, Float> = Pair(orbitAngleH, orbitAngleV)

    /**
     * Set orbit distance
     */
    fun setOrbitDistance(distance: Float) {
        orbitDistance = distance.coerceAtLeast(1f)
    }

    /**
     * Get camera direction vector
     */
    fun getDirection(): FloatArray {
        val dx = targetX - positionX
        val dy = targetY - positionY
        val dz = targetZ - positionZ
        val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)

        return if (length > 0) {
            floatArrayOf(dx / length, dy / length, dz / length)
        } else {
            floatArrayOf(0f, 0f, -1f)
        }
    }

    /**
     * Get camera right vector
     */
    fun getRight(): FloatArray {
        val direction = getDirection()

        // Cross product of direction and up
        val rx = direction[1] * upZ - direction[2] * upY
        val ry = direction[2] * upX - direction[0] * upZ
        val rz = direction[0] * upY - direction[1] * upX
        val length = kotlin.math.sqrt(rx * rx + ry * ry + rz * rz)

        return if (length > 0) {
            floatArrayOf(rx / length, ry / length, rz / length)
        } else {
            floatArrayOf(1f, 0f, 0f)
        }
    }

    /**
     * Frame the camera to fit a bounding box
     */
    fun frameBox(
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        fovDegrees: Float = 45f
    ) {
        // Calculate center and size
        targetX = (minX + maxX) / 2f
        targetY = (minY + maxY) / 2f
        targetZ = (minZ + maxZ) / 2f

        val sizeX = maxX - minX
        val sizeY = maxY - minY
        val sizeZ = maxZ - minZ
        val maxSize = maxOf(sizeX, sizeY, sizeZ)

        // Calculate distance to fit object in view
        val fovRad = Math.toRadians(fovDegrees.toDouble() / 2.0)
        orbitDistance = (maxSize / 2f / kotlin.math.tan(fovRad).toFloat()) * 1.5f

        // Reset other parameters
        orbitAngleH = 45f
        orbitAngleV = 30f
        zoomLevel = 1f
        panX = 0f
        panY = 0f
    }

    /**
     * Animate camera to a target position
     */
    fun animateTo(
        targetPosX: Float,
        targetPosY: Float,
        targetPosZ: Float,
        targetLookX: Float,
        targetLookY: Float,
        targetLookZ: Float,
        duration: Float,
        onUpdate: () -> Unit
    ) {
        // This would be implemented with a coroutine or handler for animation
        // For simplicity, just set the values directly
        setTarget(targetLookX, targetLookY, targetLookZ)
        setPosition(targetPosX, targetPosY, targetPosZ)
        onUpdate()
    }

    /**
     * Get view frustum planes for culling
     */
    fun getFrustumPlanes(projectionMatrix: FloatArray): Array<FloatArray> {
        val viewMatrix = FloatArray(16)
        getViewMatrix(viewMatrix)

        val vpMatrix = FloatArray(16)
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // Extract frustum planes from view-projection matrix
        return arrayOf(
            // Left
            floatArrayOf(
                vpMatrix[3] + vpMatrix[0],
                vpMatrix[7] + vpMatrix[4],
                vpMatrix[11] + vpMatrix[8],
                vpMatrix[15] + vpMatrix[12]
            ),
            // Right
            floatArrayOf(
                vpMatrix[3] - vpMatrix[0],
                vpMatrix[7] - vpMatrix[4],
                vpMatrix[11] - vpMatrix[8],
                vpMatrix[15] - vpMatrix[12]
            ),
            // Bottom
            floatArrayOf(
                vpMatrix[3] + vpMatrix[1],
                vpMatrix[7] + vpMatrix[5],
                vpMatrix[11] + vpMatrix[9],
                vpMatrix[15] + vpMatrix[13]
            ),
            // Top
            floatArrayOf(
                vpMatrix[3] - vpMatrix[1],
                vpMatrix[7] - vpMatrix[5],
                vpMatrix[11] - vpMatrix[9],
                vpMatrix[15] - vpMatrix[13]
            ),
            // Near
            floatArrayOf(
                vpMatrix[3] + vpMatrix[2],
                vpMatrix[7] + vpMatrix[6],
                vpMatrix[11] + vpMatrix[10],
                vpMatrix[15] + vpMatrix[14]
            ),
            // Far
            floatArrayOf(
                vpMatrix[3] - vpMatrix[2],
                vpMatrix[7] - vpMatrix[6],
                vpMatrix[11] - vpMatrix[10],
                vpMatrix[15] - vpMatrix[14]
            )
        )
    }
}