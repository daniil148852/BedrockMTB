// app/src/main/java/com/bedrockconverter/renderer/ModelRenderer.kt
package com.bedrockconverter.renderer

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.bedrockconverter.model.Model3D
import com.bedrockconverter.model.Mesh
import com.bedrockconverter.model.Texture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 3.0 renderer for 3D model preview
 */
class ModelRenderer(
    private val context: Context,
    private val model: Model3D
) : GLSurfaceView.Renderer {

    private val camera = Camera()
    private val shaders = Shaders()

    // Transformation
    private var rotationX = 0f
    private var rotationY = 0f
    private var zoom = 1f

    // Matrices
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)

    // Shader program
    private var programId = 0
    private var gridProgramId = 0

    // Mesh data
    private val meshBuffers = mutableListOf<MeshBuffers>()

    // Grid data
    private var gridVao = 0
    private var gridVertexCount = 0

    // Texture handles
    private val textureHandles = mutableMapOf<String, Int>()

    // Uniform locations
    private var uMvpMatrixLocation = 0
    private var uModelMatrixLocation = 0
    private var uNormalMatrixLocation = 0
    private var uLightPositionLocation = 0
    private var uViewPositionLocation = 0
    private var uBaseColorLocation = 0
    private var uHasTextureLocation = 0
    private var uTextureLocation = 0

    // Light properties
    private val lightPosition = floatArrayOf(5f, 10f, 5f)

    // Screen dimensions
    private var screenWidth = 0
    private var screenHeight = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set clear color (dark background)
        GLES30.glClearColor(0.1f, 0.1f, 0.12f, 1.0f)

        // Enable depth testing
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        // Enable back-face culling
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        // Enable blending for transparency
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // Compile shaders
        programId = shaders.createModelProgram()
        gridProgramId = shaders.createGridProgram()

        // Get uniform locations
        uMvpMatrixLocation = GLES30.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrixLocation = GLES30.glGetUniformLocation(programId, "uModelMatrix")
        uNormalMatrixLocation = GLES30.glGetUniformLocation(programId, "uNormalMatrix")
        uLightPositionLocation = GLES30.glGetUniformLocation(programId, "uLightPosition")
        uViewPositionLocation = GLES30.glGetUniformLocation(programId, "uViewPosition")
        uBaseColorLocation = GLES30.glGetUniformLocation(programId, "uBaseColor")
        uHasTextureLocation = GLES30.glGetUniformLocation(programId, "uHasTexture")
        uTextureLocation = GLES30.glGetUniformLocation(programId, "uTexture")

        // Initialize mesh buffers
        initializeMeshBuffers()

        // Initialize grid
        initializeGrid()

        // Load textures
        loadTextures()

        // Initialize camera based on model bounds
        initializeCamera()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height

        GLES30.glViewport(0, 0, width, height)

        // Update projection matrix
        val aspectRatio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspectRatio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Update view matrix from camera
        camera.getViewMatrix(viewMatrix)

        // Draw grid first
        drawGrid()

        // Draw model
        drawModel()
    }

    /**
     * Set transformation values from UI
     */
    fun setTransformation(rotX: Float, rotY: Float, zoomLevel: Float) {
        rotationX = rotX
        rotationY = rotY
        zoom = zoomLevel
        camera.setZoom(zoom)
    }

    /**
     * Initialize mesh buffers for all meshes in the model
     */
    private fun initializeMeshBuffers() {
        meshBuffers.clear()

        for (mesh in model.geometry.meshes) {
            val buffers = createMeshBuffers(mesh)
            meshBuffers.add(buffers)
        }
    }

    /**
     * Create OpenGL buffers for a mesh
     */
    private fun createMeshBuffers(mesh: Mesh): MeshBuffers {
        // Generate VAO
        val vaoBuffer = IntBuffer.allocate(1)
        GLES30.glGenVertexArrays(1, vaoBuffer)
        val vao = vaoBuffer[0]
        GLES30.glBindVertexArray(vao)

        // Generate VBOs
        val vboBuffer = IntBuffer.allocate(4)
        GLES30.glGenBuffers(4, vboBuffer)
        val positionVbo = vboBuffer[0]
        val normalVbo = vboBuffer[1]
        val uvVbo = vboBuffer[2]
        val ebo = vboBuffer[3]

        // Position buffer
        val positionBuffer = createFloatBuffer(mesh.vertices)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, positionVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            mesh.vertices.size * 4,
            positionBuffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(0)

        // Normal buffer
        val normals = if (mesh.normals.isNotEmpty()) {
            mesh.normals
        } else {
            // Generate default normals (pointing up)
            FloatArray(mesh.vertices.size) { i ->
                when (i % 3) {
                    1 -> 1f
                    else -> 0f
                }
            }
        }
        val normalBuffer = createFloatBuffer(normals)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, normalVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            normals.size * 4,
            normalBuffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(1)

        // UV buffer
        val uvs = if (mesh.uvs.isNotEmpty()) {
            mesh.uvs
        } else {
            FloatArray(mesh.vertices.size / 3 * 2) { 0f }
        }
        val uvBuffer = createFloatBuffer(uvs)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, uvVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            uvs.size * 4,
            uvBuffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(2)

        // Index buffer
        val indexBuffer = createIntBuffer(mesh.indices)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            mesh.indices.size * 4,
            indexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glBindVertexArray(0)

        return MeshBuffers(
            vao = vao,
            positionVbo = positionVbo,
            normalVbo = normalVbo,
            uvVbo = uvVbo,
            ebo = ebo,
            indexCount = mesh.indices.size,
            materialId = mesh.materialId
        )
    }

    /**
     * Initialize the grid for visual reference
     */
    private fun initializeGrid() {
        val gridVertices = mutableListOf<Float>()
        val gridSize = 10
        val gridStep = 1f

        // Grid lines along X axis
        for (i in -gridSize..gridSize) {
            val z = i * gridStep
            gridVertices.addAll(listOf(-gridSize * gridStep, 0f, z))
            gridVertices.addAll(listOf(gridSize * gridStep, 0f, z))
        }

        // Grid lines along Z axis
        for (i in -gridSize..gridSize) {
            val x = i * gridStep
            gridVertices.addAll(listOf(x, 0f, -gridSize * gridStep))
            gridVertices.addAll(listOf(x, 0f, gridSize * gridStep))
        }

        gridVertexCount = gridVertices.size / 3

        // Create VAO for grid
        val vaoBuffer = IntBuffer.allocate(1)
        GLES30.glGenVertexArrays(1, vaoBuffer)
        gridVao = vaoBuffer[0]
        GLES30.glBindVertexArray(gridVao)

        // Create VBO
        val vboBuffer = IntBuffer.allocate(1)
        GLES30.glGenBuffers(1, vboBuffer)
        val vbo = vboBuffer[0]

        val vertexBuffer = createFloatBuffer(gridVertices.toFloatArray())
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            gridVertices.size * 4,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(0)

        GLES30.glBindVertexArray(0)
    }

    /**
     * Load textures into OpenGL
     */
    private fun loadTextures() {
        for (texture in model.textures) {
            val textureHandle = loadTexture(texture)
            if (textureHandle != 0) {
                textureHandles[texture.id] = textureHandle
            }
        }

        // Create default texture if none loaded
        if (textureHandles.isEmpty()) {
            val defaultHandle = createDefaultTexture()
            textureHandles["default"] = defaultHandle
        }
    }

    /**
     * Load a single texture
     */
    private fun loadTexture(texture: Texture): Int {
        val textureHandle = IntBuffer.allocate(1)
        GLES30.glGenTextures(1, textureHandle)
        val handle = textureHandle[0]

        if (handle == 0) return 0

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, handle)

        // Set texture parameters
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)

        // Decode and upload texture
        try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                texture.data, 0, texture.data.size
            ) ?: return 0

            android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

            bitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return handle
    }

    /**
     * Create a default white texture
     */
    private fun createDefaultTexture(): Int {
        val textureHandle = IntBuffer.allocate(1)
        GLES30.glGenTextures(1, textureHandle)
        val handle = textureHandle[0]

        if (handle == 0) return 0

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, handle)

        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)

        // 2x2 white texture
        val pixels = byteArrayOf(
            -1, -1, -1, -1,  // White
            -1, -1, -1, -1,
            -1, -1, -1, -1,
            -1, -1, -1, -1
        )
        val buffer = ByteBuffer.allocateDirect(pixels.size).apply {
            put(pixels)
            position(0)
        }

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            2, 2, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer
        )

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return handle
    }

    /**
     * Initialize camera based on model bounds
     */
    private fun initializeCamera() {
        val bounds = model.bounds
        if (bounds != null) {
            val maxDimension = maxOf(bounds.width, bounds.height, bounds.depth)
            val distance = maxDimension * 2f + 5f
            camera.setPosition(0f, bounds.center.y + maxDimension * 0.5f, distance)
            camera.setTarget(bounds.center.x, bounds.center.y, bounds.center.z)
        } else {
            camera.setPosition(0f, 2f, 5f)
            camera.setTarget(0f, 0f, 0f)
        }
    }

    /**
     * Draw the grid
     */
    private fun drawGrid() {
        GLES30.glUseProgram(gridProgramId)

        // Calculate MVP matrix for grid
        Matrix.setIdentityM(modelMatrix, 0)

        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // Set uniforms
        val uMvpLocation = GLES30.glGetUniformLocation(gridProgramId, "uMvpMatrix")
        val uColorLocation = GLES30.glGetUniformLocation(gridProgramId, "uColor")

        GLES30.glUniformMatrix4fv(uMvpLocation, 1, false, mvpMatrix, 0)
        GLES30.glUniform4f(uColorLocation, 0.3f, 0.3f, 0.35f, 1f)

        // Draw grid
        GLES30.glBindVertexArray(gridVao)
        GLES30.glLineWidth(1f)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, gridVertexCount)
        GLES30.glBindVertexArray(0)
    }

    /**
     * Draw the model
     */
    private fun drawModel() {
        GLES30.glUseProgram(programId)

        // Calculate model matrix with rotation
        Matrix.setIdentityM(modelMatrix, 0)

        // Center the model
        model.bounds?.let { bounds ->
            Matrix.translateM(modelMatrix, 0, -bounds.center.x, -bounds.center.y, -bounds.center.z)
        }

        // Apply rotations
        val rotationMatrix = FloatArray(16)
        Matrix.setIdentityM(rotationMatrix, 0)
        Matrix.rotateM(rotationMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(rotationMatrix, 0, rotationY, 0f, 1f, 0f)

        val tempModel = FloatArray(16)
        Matrix.multiplyMM(tempModel, 0, rotationMatrix, 0, modelMatrix, 0)
        System.arraycopy(tempModel, 0, modelMatrix, 0, 16)

        // Calculate MVP matrix
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // Calculate normal matrix (inverse transpose of model matrix)
        Matrix.invertM(normalMatrix, 0, modelMatrix, 0)
        transposeMatrix(normalMatrix)

        // Set uniforms
        GLES30.glUniformMatrix4fv(uMvpMatrixLocation, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uModelMatrixLocation, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(uNormalMatrixLocation, 1, false, normalMatrix, 0)
        GLES30.glUniform3fv(uLightPositionLocation, 1, lightPosition, 0)
        GLES30.glUniform3f(uViewPositionLocation, camera.positionX, camera.positionY, camera.positionZ)

        // Draw each mesh
        for ((index, buffers) in meshBuffers.withIndex()) {
            drawMesh(buffers, index)
        }
    }

    /**
     * Draw a single mesh
     */
    private fun drawMesh(buffers: MeshBuffers, index: Int) {
        // Set material color
        val material = model.materials.find { it.id == buffers.materialId }
        val color = material?.diffuseColor ?: com.bedrockconverter.model.Color.WHITE

        GLES30.glUniform4f(uBaseColorLocation, color.r, color.g, color.b, color.a)

        // Bind texture
        val textureId = material?.diffuseTextureId
        val textureHandle = textureHandles[textureId] ?: textureHandles["default"] ?: 0

        if (textureHandle != 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle)
            GLES30.glUniform1i(uTextureLocation, 0)
            GLES30.glUniform1i(uHasTextureLocation, 1)
        } else {
            GLES30.glUniform1i(uHasTextureLocation, 0)
        }

        // Draw mesh
        GLES30.glBindVertexArray(buffers.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, buffers.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    /**
     * Transpose a 4x4 matrix
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
     * Create a FloatBuffer from a float array
     */
    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    /**
     * Create an IntBuffer from an int array
     */
    private fun createIntBuffer(data: IntArray): IntBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        // Delete mesh buffers
        for (buffers in meshBuffers) {
            val vboArray = intArrayOf(buffers.positionVbo, buffers.normalVbo, buffers.uvVbo, buffers.ebo)
            GLES30.glDeleteBuffers(4, vboArray, 0)
            GLES30.glDeleteVertexArrays(1, intArrayOf(buffers.vao), 0)
        }
        meshBuffers.clear()

        // Delete textures
        for (handle in textureHandles.values) {
            GLES30.glDeleteTextures(1, intArrayOf(handle), 0)
        }
        textureHandles.clear()

        // Delete programs
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        if (gridProgramId != 0) {
            GLES30.glDeleteProgram(gridProgramId)
            gridProgramId = 0
        }
    }

    /**
     * Mesh buffer handles
     */
    private data class MeshBuffers(
        val vao: Int,
        val positionVbo: Int,
        val normalVbo: Int,
        val uvVbo: Int,
        val ebo: Int,
        val indexCount: Int,
        val materialId: String?
    )
}