// app/src/main/java/com/bedrockconverter/renderer/Shaders.kt
package com.bedrockconverter.renderer

import android.opengl.GLES30
import android.util.Log

/**
 * GLSL shader management for OpenGL ES 3.0
 */
class Shaders {

    companion object {
        private const val TAG = "Shaders"
    }

    // Model vertex shader
    private val modelVertexShader = """
        #version 300 es
        
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        
        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;
        uniform mat4 uNormalMatrix;
        
        out vec3 vPosition;
        out vec3 vNormal;
        out vec2 vTexCoord;
        
        void main() {
            vec4 worldPosition = uModelMatrix * vec4(aPosition, 1.0);
            vPosition = worldPosition.xyz;
            vNormal = normalize(mat3(uNormalMatrix) * aNormal);
            vTexCoord = aTexCoord;
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    // Model fragment shader with Phong lighting
    private val modelFragmentShader = """
        #version 300 es
        precision mediump float;
        
        in vec3 vPosition;
        in vec3 vNormal;
        in vec2 vTexCoord;
        
        uniform vec3 uLightPosition;
        uniform vec3 uViewPosition;
        uniform vec4 uBaseColor;
        uniform int uHasTexture;
        uniform sampler2D uTexture;
        
        out vec4 fragColor;
        
        void main() {
            // Ambient
            float ambientStrength = 0.3;
            vec3 ambient = ambientStrength * vec3(1.0, 1.0, 1.0);
            
            // Diffuse
            vec3 norm = normalize(vNormal);
            vec3 lightDir = normalize(uLightPosition - vPosition);
            float diff = max(dot(norm, lightDir), 0.0);
            vec3 diffuse = diff * vec3(1.0, 1.0, 1.0);
            
            // Specular
            float specularStrength = 0.5;
            vec3 viewDir = normalize(uViewPosition - vPosition);
            vec3 reflectDir = reflect(-lightDir, norm);
            float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
            vec3 specular = specularStrength * spec * vec3(1.0, 1.0, 1.0);
            
            // Get base color
            vec4 baseColor;
            if (uHasTexture == 1) {
                baseColor = texture(uTexture, vTexCoord) * uBaseColor;
            } else {
                baseColor = uBaseColor;
            }
            
            // Combine lighting
            vec3 result = (ambient + diffuse + specular) * baseColor.rgb;
            
            fragColor = vec4(result, baseColor.a);
        }
    """.trimIndent()

    // Grid vertex shader
    private val gridVertexShader = """
        #version 300 es
        
        layout(location = 0) in vec3 aPosition;
        
        uniform mat4 uMvpMatrix;
        
        void main() {
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    // Grid fragment shader
    private val gridFragmentShader = """
        #version 300 es
        precision mediump float;
        
        uniform vec4 uColor;
        
        out vec4 fragColor;
        
        void main() {
            fragColor = uColor;
        }
    """.trimIndent()

    // Wireframe vertex shader
    private val wireframeVertexShader = """
        #version 300 es
        
        layout(location = 0) in vec3 aPosition;
        
        uniform mat4 uMvpMatrix;
        
        void main() {
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    // Wireframe fragment shader
    private val wireframeFragmentShader = """
        #version 300 es
        precision mediump float;
        
        uniform vec4 uColor;
        
        out vec4 fragColor;
        
        void main() {
            fragColor = uColor;
        }
    """.trimIndent()

    // Skybox vertex shader
    private val skyboxVertexShader = """
        #version 300 es
        
        layout(location = 0) in vec3 aPosition;
        
        uniform mat4 uProjectionMatrix;
        uniform mat4 uViewMatrix;
        
        out vec3 vTexCoord;
        
        void main() {
            vTexCoord = aPosition;
            vec4 pos = uProjectionMatrix * mat4(mat3(uViewMatrix)) * vec4(aPosition, 1.0);
            gl_Position = pos.xyww;
        }
    """.trimIndent()

    // Skybox fragment shader (gradient)
    private val skyboxFragmentShader = """
        #version 300 es
        precision mediump float;
        
        in vec3 vTexCoord;
        
        uniform vec3 uTopColor;
        uniform vec3 uBottomColor;
        
        out vec4 fragColor;
        
        void main() {
            float t = normalize(vTexCoord).y * 0.5 + 0.5;
            vec3 color = mix(uBottomColor, uTopColor, t);
            fragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    /**
     * Create the model shader program
     */
    fun createModelProgram(): Int {
        return createProgram(modelVertexShader, modelFragmentShader)
    }

    /**
     * Create the grid shader program
     */
    fun createGridProgram(): Int {
        return createProgram(gridVertexShader, gridFragmentShader)
    }

    /**
     * Create the wireframe shader program
     */
    fun createWireframeProgram(): Int {
        return createProgram(wireframeVertexShader, wireframeFragmentShader)
    }

    /**
     * Create the skybox shader program
     */
    fun createSkyboxProgram(): Int {
        return createProgram(skyboxVertexShader, skyboxFragmentShader)
    }

    /**
     * Create a shader program from vertex and fragment shader source
     */
    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            Log.e(TAG, "Failed to compile vertex shader")
            return 0
        }

        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES30.glDeleteShader(vertexShader)
            Log.e(TAG, "Failed to compile fragment shader")
            return 0
        }

        val program = GLES30.glCreateProgram()
        if (program == 0) {
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            Log.e(TAG, "Failed to create program")
            return 0
        }

        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)

        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(program)
            Log.e(TAG, "Program link error: $error")
            GLES30.glDeleteProgram(program)
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            return 0
        }

        // Shaders can be deleted after linking
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        return program
    }

    /**
     * Compile a shader from source
     */
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader")
            return 0
        }

        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            val shaderType = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
            Log.e(TAG, "$shaderType shader compile error: $error")
            GLES30.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    /**
     * Check for OpenGL errors
     */
    fun checkGlError(operation: String) {
        var error: Int
        while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "$operation: glError $error")
        }
    }

    /**
     * Create a custom shader program from source strings
     */
    fun createCustomProgram(vertexSource: String, fragmentSource: String): Int {
        return createProgram(vertexSource, fragmentSource)
    }

    /**
     * Get shader info log for debugging
     */
    fun getShaderInfoLog(shader: Int): String {
        return GLES30.glGetShaderInfoLog(shader)
    }

    /**
     * Get program info log for debugging
     */
    fun getProgramInfoLog(program: Int): String {
        return GLES30.glGetProgramInfoLog(program)
    }

    /**
     * Validate a program
     */
    fun validateProgram(program: Int): Boolean {
        GLES30.glValidateProgram(program)

        val validateStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_VALIDATE_STATUS, validateStatus, 0)

        if (validateStatus[0] == 0) {
            Log.e(TAG, "Program validation failed: ${getProgramInfoLog(program)}")
            return false
        }

        return true
    }

    /**
     * Get uniform location with caching
     */
    private val uniformCache = mutableMapOf<Pair<Int, String>, Int>()

    fun getUniformLocation(program: Int, name: String): Int {
        val key = Pair(program, name)
        return uniformCache.getOrPut(key) {
            GLES30.glGetUniformLocation(program, name)
        }
    }

    /**
     * Get attribute location with caching
     */
    private val attributeCache = mutableMapOf<Pair<Int, String>, Int>()

    fun getAttributeLocation(program: Int, name: String): Int {
        val key = Pair(program, name)
        return attributeCache.getOrPut(key) {
            GLES30.glGetAttribLocation(program, name)
        }
    }

    /**
     * Clear caches (call when programs are deleted)
     */
    fun clearCaches() {
        uniformCache.clear()
        attributeCache.clear()
    }
}