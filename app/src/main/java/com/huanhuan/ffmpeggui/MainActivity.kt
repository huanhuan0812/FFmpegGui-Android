package com.huanhuan.ffmpeggui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huanhuan.ffmpeggui.ui.theme.FFmpegGuiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FFmpegGuiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FFmpegApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun FFmpegApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 创建ViewModel实例
    val viewModel: FFmpegViewModel = viewModel()

    Scaffold(
        bottomBar = {
            // 底部导航栏
            NavigationBar {
                val tabs = listOf(
                    BottomNavItem("历史记录", "history", Icons.Default.History, Icons.Outlined.History),
                    BottomNavItem("音频提取", "audio_extract", Icons.Default.Audiotrack, Icons.Outlined.Audiotrack),
                    BottomNavItem("音频转换", "audio_convert", Icons.Default.MusicNote, Icons.Outlined.MusicNote),
                    BottomNavItem("视频转换", "video_convert", Icons.Default.VideoLibrary, Icons.Outlined.VideoLibrary),
                    BottomNavItem("图片转换", "image_convert", Icons.Default.Image, Icons.Outlined.Image),
                    BottomNavItem("关于", "about", Icons.Default.Info, Icons.Outlined.Info)
                )

                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            BadgedBox(badge = { }) {
                                Icon(
                                    if (currentRoute == tab.route) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            }
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(
                navController = navController,
                startDestination = "history"
            ) {
                composable("history") {
                    HistoryScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToResult = { outputPath ->
                            val encodedPath = Uri.encode(outputPath, "/")
                            navController.navigate("result/$encodedPath")
                        },
                        viewModel = viewModel
                    )
                }
                composable("audio_extract") {
                    AudioExtractScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }
                composable("audio_convert") {
                    AudioConvertScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }
                composable("video_convert") {
                    VideoConvertScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }
                composable("image_convert") {
                    ImageConvertScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }
                composable("about") {
                    AboutScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    "result/{outputPath}",
                    arguments = listOf(navArgument("outputPath") { type = NavType.StringType })
                ) { backStackEntry ->
                    val outputPath = backStackEntry.arguments?.getString("outputPath") ?: ""
                    ResultScreen(
                        outputPath = outputPath,
                        onBack = { navController.popBackStack() },
                        onNewTask = { navController.popBackStack("history", false) }
                    )
                }
            }
        }
    }
}

// 底部导航项数据类
data class BottomNavItem(
    val title: String,
    val route: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)