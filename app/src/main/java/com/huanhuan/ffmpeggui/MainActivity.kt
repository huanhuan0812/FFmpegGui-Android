package com.huanhuan.ffmpeggui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
        topBar = {
            TopAppBar(
                title = { Text("FFmpeg GUI") },
                actions = {
                    // 右上角的关于按钮
                    IconButton(onClick = {
                        navController.navigate("about")
                    }) {
                        Icon(
                            if (currentRoute == "about") Icons.Default.Info else Icons.Outlined.Info,
                            contentDescription = "关于"
                        )
                    }
                },
                navigationIcon = {
                    // 如果不是主界面，显示返回按钮
                    if (currentRoute != "history" &&
                        currentRoute != "video" &&
                        currentRoute != "audio" &&
                        currentRoute != "image") {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        bottomBar = {
            // 底部导航栏 - 四个主分类
            NavigationBar {
                val tabs = listOf(
                    BottomNavItem("历史", "history", Icons.Default.History, Icons.Outlined.History),
                    BottomNavItem("视频", "video", Icons.Default.VideoLibrary, Icons.Outlined.VideoLibrary),
                    BottomNavItem("音频", "audio", Icons.Default.Audiotrack, Icons.Outlined.Audiotrack),
                    BottomNavItem("图像", "image", Icons.Default.Image, Icons.Outlined.Image)
                )

                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route ||
                                (tab.route == "video" && (currentRoute?.startsWith("video/") == true)) ||
                                (tab.route == "audio" && (currentRoute?.startsWith("audio/") == true)) ||
                                (tab.route == "image" && (currentRoute?.startsWith("image/") == true)),
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            BadgedBox(badge = { }) {
                                Icon(
                                    if (currentRoute == tab.route ||
                                        (tab.route == "video" && currentRoute?.startsWith("video/") == true) ||
                                        (tab.route == "audio" && currentRoute?.startsWith("audio/") == true) ||
                                        (tab.route == "image" && currentRoute?.startsWith("image/") == true)
                                    ) tab.selectedIcon else tab.unselectedIcon,
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
                // 历史记录主界面
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

                // 视频主界面 - 包含视频相关的二级功能
                composable("video") {
                    VideoMainScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 音频主界面 - 包含音频相关的二级功能
                composable("audio") {
                    AudioMainScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 图像主界面 - 包含图像相关的二级功能
                composable("image") {
                    ImageMainScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 视频转换界面（二级页面）
                composable("video/convert") {
                    VideoConvertScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }

                composable("video/gifconvert") {
                    VideoToGifScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }

//                composable("video/frameextract") {
//                    VideoFrameExtractorScreen(
//                        navController = navController,
//                        onBack = { navController.popBackStack() },
//                        viewModel = viewModel
//                    )
//                }

                // 音频提取界面（二级页面）
                composable("audio/extract") {
                    AudioExtractScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }

                // 音频转换界面（二级页面）
                composable("audio/convert") {
                    AudioConvertScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }

                // 图像转换界面（二级页面）
                composable("image/convert") {
                    ImageConvertScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }

                // 关于界面
                composable("about") {
                    AboutScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 结果界面
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

// 视频功能数据类
data class VideoFeature(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    var route: String
)

// 视频主界面 - 卡片式二级菜单
@Composable
fun VideoMainScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val features = listOf(
        VideoFeature("convert", "视频转换", Icons.Default.VideoLibrary, "video/convert"),
        VideoFeature("gifconvert", "视频转GIF", Icons.Default.Movie, "video/gifconvert"),
        //VideoFeature("frameextract", "视频帧提取", Icons.Default.PhotoLibrary, "video/frameextract")
        // 可以在这里添加更多视频功能
    )

    FeatureListScreen(
        title = "视频功能",
        features = features,
        navController = navController
    )
}

// 音频功能数据类
data class AudioFeature(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

// 音频主界面 - 卡片式二级菜单
@Composable
fun AudioMainScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val features = listOf(
        AudioFeature("extract", "音频提取", Icons.Default.Audiotrack, "audio/extract"),
        AudioFeature("convert", "音频转换", Icons.Default.MusicNote, "audio/convert")
    )

    FeatureListScreen(
        title = "音频功能",
        features = features,
        navController = navController
    )
}

// 图像功能数据类
data class ImageFeature(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

// 图像主界面 - 卡片式二级菜单
@Composable
fun ImageMainScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val features = listOf(
        ImageFeature("convert", "图像转换", Icons.Default.Image, "image/convert")
        // 可以在这里添加更多图像功能
    )

    FeatureListScreen(
        title = "图像功能",
        features = features,
        navController = navController
    )
}

// 通用的功能列表界面
@Composable
fun FeatureListScreen(
    title: String,
    features: List<Any>, // 使用Any类型接受不同的功能类
    navController: androidx.navigation.NavController
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(features) { feature ->
                when (feature) {
                    is VideoFeature -> FeatureCard(
                        title = feature.title,
                        icon = feature.icon,
                        onClick = { navController.navigate(feature.route) }
                    )
                    is AudioFeature -> FeatureCard(
                        title = feature.title,
                        icon = feature.icon,
                        onClick = { navController.navigate(feature.route) }
                    )
                    is ImageFeature -> FeatureCard(
                        title = feature.title,
                        icon = feature.icon,
                        onClick = { navController.navigate(feature.route) }
                    )
                }
            }
        }
    }
}

// 功能卡片组件
@Composable
fun FeatureCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
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