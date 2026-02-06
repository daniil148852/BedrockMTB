// app/src/main/java/com/bedrockconverter/ui/components/ModelViewer.kt
package com.bedrockconverter.ui.components

import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bedrockconverter.model.Model3D
import com.bedrockconverter.renderer.ModelRenderer

@Composable
fun ModelViewer(
    model: Model3D,
    rotationX: Float,
    rotationY: Float,
    zoom: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var glSurfaceView by remember { mutableStateOf<GLSurfaceView?>(null) }
    var renderer by remember { mutableStateOf<ModelRenderer?>(null) }

    DisposableEffect(model) {
        onDispose {
            renderer?.cleanup()
        }
    }

    // Update renderer with new transformation values
    LaunchedEffect(rotationX, rotationY, zoom) {
        renderer?.setTransformation(rotationX, rotationY, zoom)
    }

    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                
                val modelRenderer = ModelRenderer(ctx, model)
                setRenderer(modelRenderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                
                renderer = modelRenderer
                glSurfaceView = this
            }
        },
        update = { view ->
            renderer?.setTransformation(rotationX, rotationY, zoom)
        },
        modifier = modifier.fillMaxSize()
    )
}