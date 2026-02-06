// app/src/main/java/com/bedrockconverter/MainActivity.kt
package com.bedrockconverter

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bedrockconverter.ui.screens.*
import com.bedrockconverter.ui.theme.BedrockConverterTheme
import com.bedrockconverter.viewmodel.MainViewModel
import com.bedrockconverter.viewmodel.NavigationEvent
import com.bedrockconverter.viewmodel.PreviewViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val previewViewModel: PreviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val appSettings by mainViewModel.appSettings.collectAsStateWithLifecycle()

            BedrockConverterTheme(darkTheme = appSettings.darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        mainViewModel = mainViewModel,
                        previewViewModel = previewViewModel,
                        onShareFile = { filePath ->
                            shareFile(filePath)
                        }
                    )
                }
            }
        }

        // Handle intent if app was opened with a file
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                val uri = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
                if (uri != null && uri is android.net.Uri) {
                    if (mainViewModel.canImport(uri)) {
                        mainViewModel.importModel(uri)
                    } else {
                        Toast.makeText(
                            this,
                            "Unsupported file format",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun shareFile(filePath: String) {
        try {
            val shareIntent = mainViewModel.shareExportedFile(filePath)
            startActivity(Intent.createChooser(shareIntent, "Share Addon"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share file", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainNavigation(
    mainViewModel: MainViewModel,
    previewViewModel: PreviewViewModel,
    onShareFile: (String) -> Unit
) {
    val navController = rememberNavController()

    // Observe navigation events
    val navigationEvent by mainViewModel.navigationEvent.collectAsStateWithLifecycle()

    LaunchedEffect(navigationEvent) {
        when (navigationEvent) {
            NavigationEvent.NavigateToPreview -> {
                navController.navigate("preview") {
                    launchSingleTop = true
                }
                mainViewModel.onNavigationEventConsumed()
            }
            NavigationEvent.NavigateToExport -> {
                navController.navigate("export") {
                    launchSingleTop = true
                }
                mainViewModel.onNavigationEventConsumed()
            }
            NavigationEvent.NavigateToSettings -> {
                navController.navigate("settings") {
                    launchSingleTop = true
                }
                mainViewModel.onNavigationEventConsumed()
            }
            NavigationEvent.NavigateToHome -> {
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }
                mainViewModel.onNavigationEventConsumed()
            }
            NavigationEvent.NavigateBack -> {
                navController.popBackStack()
                mainViewModel.onNavigationEventConsumed()
            }
            null -> { /* No event */ }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreenRoute(
                mainViewModel = mainViewModel,
                navController = navController
            )
        }

        composable("preview") {
            PreviewScreenRoute(
                mainViewModel = mainViewModel,
                previewViewModel = previewViewModel,
                navController = navController
            )
        }

        composable("export") {
            ExportScreenRoute(
                mainViewModel = mainViewModel,
                navController = navController,
                onShareFile = onShareFile
            )
        }

        composable("settings") {
            SettingsScreenRoute(
                mainViewModel = mainViewModel,
                navController = navController
            )
        }
    }
}

@Composable
fun HomeScreenRoute(
    mainViewModel: MainViewModel,
    navController: NavHostController
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val recentModels by mainViewModel.recentModels.collectAsStateWithLifecycle()

    HomeScreen(
        recentModels = recentModels,
        isLoading = uiState.isLoading,
        onImportModel = { uri ->
            mainViewModel.importModel(uri)
        },
        onModelSelected = { model ->
            mainViewModel.selectModel(model)
        },
        onDeleteModel = { model ->
            mainViewModel.deleteModel(model)
        },
        onSettingsClick = {
            mainViewModel.navigateToSettings()
        }
    )

    // Show error if present
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error toast or snackbar
            mainViewModel.clearError()
        }
    }
}

@Composable
fun PreviewScreenRoute(
    mainViewModel: MainViewModel,
    previewViewModel: PreviewViewModel,
    navController: NavHostController
) {
    val currentModel by mainViewModel.currentModel.collectAsStateWithLifecycle()
    val currentScale by mainViewModel.currentScale.collectAsStateWithLifecycle()

    currentModel?.let { model ->
        // Set model in preview viewmodel
        LaunchedEffect(model) {
            previewViewModel.setModel(model)
        }

        PreviewScreen(
            model = model,
            currentScale = currentScale,
            onScaleChange = { scale ->
                mainViewModel.updateScale(scale)
            },
            onExportClick = {
                mainViewModel.navigateToExport()
            },
            onBackClick = {
                mainViewModel.navigateToHome()
            }
        )
    } ?: run {
        // No model, navigate back to home
        LaunchedEffect(Unit) {
            navController.navigate("home") {
                popUpTo("home") { inclusive = true }
            }
        }
    }
}

@Composable
fun ExportScreenRoute(
    mainViewModel: MainViewModel,
    navController: NavHostController,
    onShareFile: (String) -> Unit
) {
    val currentModel by mainViewModel.currentModel.collectAsStateWithLifecycle()
    val exportSettings by mainViewModel.exportSettings.collectAsStateWithLifecycle()
    val exportState by mainViewModel.exportState.collectAsStateWithLifecycle()

    currentModel?.let { model ->
        ExportScreen(
            model = model,
            exportSettings = exportSettings,
            exportState = exportState,
            onSettingsChange = { settings ->
                mainViewModel.updateExportSettings(settings)
            },
            onExport = {
                mainViewModel.exportModel()
            },
            onShare = { filePath ->
                onShareFile(filePath)
            },
            onBackClick = {
                mainViewModel.resetExportState()
                navController.popBackStack()
            }
        )
    } ?: run {
        LaunchedEffect(Unit) {
            navController.navigate("home") {
                popUpTo("home") { inclusive = true }
            }
        }
    }
}

@Composable
fun SettingsScreenRoute(
    mainViewModel: MainViewModel,
    navController: NavHostController
) {
    val appSettings by mainViewModel.appSettings.collectAsStateWithLifecycle()

    SettingsScreen(
        settings = appSettings,
        onSettingsChange = { settings ->
            mainViewModel.updateAppSettings(settings)
        },
        onClearCache = {
            mainViewModel.clearCache()
        },
        onBackClick = {
            navController.popBackStack()
        }
    )
}