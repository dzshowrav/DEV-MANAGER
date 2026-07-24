package com.example.devmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.devmanager.ui.documentviewer.DocumentViewerScreen
import com.example.devmanager.ui.filemanager.FileManagerScreen
import com.example.devmanager.ui.filemanager.FileManagerViewModel
import com.example.devmanager.ui.imageviewer.ImageViewerScreen
import com.example.devmanager.ui.texteditor.TextEditorScreen

object Routes {
    const val FILE_MANAGER = "file_manager"
    const val TEXT_EDITOR = "text_editor/{filePath}"
    const val IMAGE_VIEWER = "image_viewer/{filePath}"
    const val DOCUMENT_VIEWER = "document_viewer/{filePath}"
    const val SETTINGS = "settings"

    fun textEditor(filePath: String) = "text_editor/$filePath"
    fun imageViewer(filePath: String) = "image_viewer/$filePath"
    fun documentViewer(filePath: String) = "document_viewer/$filePath"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: FileManagerViewModel = hiltViewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.FILE_MANAGER
    ) {
        composable(Routes.FILE_MANAGER) {
            FileManagerScreen(
                viewModel = viewModel,
                onOpenTextEditor = { filePath ->
                    navController.navigate(Routes.textEditor(filePath))
                },
                onOpenImageViewer = { filePath ->
                    navController.navigate(Routes.imageViewer(filePath))
                },
                onOpenDocumentViewer = { filePath ->
                    navController.navigate(Routes.documentViewer(filePath))
                }
            )
        }
        composable(Routes.TEXT_EDITOR) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: return@composable
            TextEditorScreen(
                viewModel = viewModel,
                filePath = filePath,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.IMAGE_VIEWER) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: return@composable
            ImageViewerScreen(
                viewModel = viewModel,
                filePath = filePath,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.DOCUMENT_VIEWER) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: return@composable
            DocumentViewerScreen(
                viewModel = viewModel,
                filePath = filePath,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
