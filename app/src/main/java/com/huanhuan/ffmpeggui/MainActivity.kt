package com.huanhuan.ffmpeggui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.huanhuan.ffmpeggui.ui.theme.FFmpegGuiTheme
import java.net.URLDecoder
import java.net.URLEncoder

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
@Composable
fun FFmpegApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current

    // 创建ViewModel实例
    val viewModel: FFmpegViewModel = viewModel()

    // 初始化数据库
    LaunchedEffect(Unit) {
        try {
            Log.d("MainActivity", "开始初始化数据库")
            viewModel.initDatabase(context)
            Log.d("MainActivity", "数据库初始化完成")
        } catch (e: Exception) {
            Log.e("MainActivity", "数据库初始化失败", e)
            e.printStackTrace()
        }
    }

    // 获取当前页面标题和操作按钮
    val screenTitle = when {
        currentRoute == "history" -> "历史记录"
        currentRoute == "video" -> "视频处理"
        currentRoute == "audio" -> "音频处理"
        currentRoute == "image" -> "图像处理"
        currentRoute == "advanced" -> "高级命令"
        currentRoute == "about" -> "关于"
        currentRoute == "tutorial" -> "FFmpeg 命令教程"
        currentRoute == "audio/info" -> "音频信息"
        currentRoute == "video/info" -> "视频信息"
        currentRoute == "audio/tag_edit" -> "标签编辑"
        currentRoute?.startsWith("video/") == true -> {
            when (currentRoute) {
                "video/convert" -> "视频转换"
                "video/gifconvert" -> "视频转GIF"
                "video/extract" -> "音频提取"
                "video/info" -> "视频信息"
                else -> "视频处理"
            }
        }
        currentRoute?.startsWith("audio/") == true -> {
            when (currentRoute) {
                "audio/convert" -> "音频转换"
                "audio/info" -> "音频信息"
                "audio/tag_edit" -> "标签编辑"
                else -> "音频处理"
            }
        }
        currentRoute?.startsWith("image/") == true -> {
            when (currentRoute) {
                "image/convert" -> "图像转换"
                else -> "图像处理"
            }
        }
        currentRoute?.startsWith("result/") == true -> "处理结果"
        else -> "FFmpeg GUI"
    }

    // 判断是否显示返回按钮
    val showBackButton = currentRoute != "history" &&
            currentRoute != "video" &&
            currentRoute != "audio" &&
            currentRoute != "image" &&
            currentRoute != "advanced" &&
            currentRoute != "about" &&
            currentRoute != "tutorial" &&
            currentRoute != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                actions = {
                    if (currentRoute in listOf("history", "video", "audio", "image", "advanced")) {
                        IconButton(onClick = {
                            navController.navigate("about")
                        }) {
                            Icon(
                                if (currentRoute == "about") Icons.Default.Info else Icons.Outlined.Info,
                                contentDescription = "关于"
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = {
                            when {
                                currentRoute == "about" -> navController.navigate("history")
                                currentRoute == "tutorial" -> navController.popBackStack()
                                currentRoute == "audio/info" -> navController.popBackStack()
                                currentRoute == "video/info" -> navController.popBackStack()
                                currentRoute == "audio/tag_edit" -> navController.popBackStack()
                                else -> navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        bottomBar = {
            val shouldShowBottomBar = currentRoute != null &&
                    currentRoute != "tutorial" &&
                    currentRoute != "about" &&
                    currentRoute != "audio/info" &&
                    currentRoute != "video/info" &&
                    currentRoute != "audio/tag_edit" &&
                    !currentRoute.startsWith("result/")

            if (shouldShowBottomBar) {
                NavigationBar {
                    val tabs = listOf(
                        BottomNavItem("历史", "history", Icons.Default.History, Icons.Outlined.History),
                        BottomNavItem("视频", "video", Icons.Default.VideoLibrary, Icons.Outlined.VideoLibrary),
                        BottomNavItem("音频", "audio", Icons.Default.Audiotrack, Icons.Outlined.Audiotrack),
                        BottomNavItem("图像", "image", Icons.Default.Image, Icons.Outlined.Image),
                        BottomNavItem("高级", "advanced", Icons.Default.Terminal, Icons.Outlined.Terminal)
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
                                            (tab.route == "image" && currentRoute?.startsWith("image/") == true) ||
                                            (tab.route == "advanced" && currentRoute?.startsWith("advanced/") == true)
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
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(
                navController = navController,
                startDestination = "history"
            ) {
                // 历史记录主界面
                composable("history") {
                    HistoryScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToResult = { outputPath ->
                            try {
                                val encodedPath = URLEncoder.encode(outputPath, "UTF-8")
                                navController.navigate("result/$encodedPath")
                            } catch (e: Exception) {
                                Log.e("MainActivity", "编码路径失败: ${e.message}")
                                val encodedPath = Uri.encode(outputPath)
                                navController.navigate("result/$encodedPath")
                            }
                        },
                        viewModel = viewModel
                    )
                }

                // 视频主界面
                composable("video") {
                    VideoMainScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 音频主界面
                composable("audio") {
                    AudioMainScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 图像主界面
                composable("image") {
                    ImageMainScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 高级功能主界面
                composable("advanced") {
                    CommandScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }

                // ============================================================
                // 视频功能
                // ============================================================
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

                composable("video/extract") {
                    AudioExtractScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }

                composable("video/info") {
                    VideoInfoScreen(
                        onBack = { navController.popBackStack() },
                        navController = navController,
                        viewModel = viewModel
                    )
                }

                // ============================================================
                // 音频功能
                // ============================================================
                composable("audio/convert") {
                    AudioConvertScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }

                composable("audio/info") {
                    AudioInfoScreen(
                        onBack = { navController.popBackStack() },
                        navController = navController,
                        viewModel = viewModel
                    )
                }

                // ============================================================
                // 新增：音频标签编辑
                // ============================================================
                composable("audio/tag_edit") {
                    AudioTagEditScreen(
                        onBack = { navController.popBackStack() },
                        navController = navController,
                        viewModel = viewModel
                    )
                }

                // ============================================================
                // 图像功能
                // ============================================================
                composable("image/convert") {
                    ImageConvertScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }

                // ============================================================
                // 其他页面
                // ============================================================
                composable("about") {
                    AboutScreen(
                        onBack = { navController.navigate("history") }
                    )
                }

                composable("tutorial") {
                    FFmpegTutorialScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() },
                        onToggleAll = { /* 由 TutorialScreen 内部管理 */ }
                    )
                }

                composable(
                    "result/{outputPath}",
                    arguments = listOf(navArgument("outputPath") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encodedPath = backStackEntry.arguments?.getString("outputPath") ?: ""
                    val outputPath = try {
                        URLDecoder.decode(encodedPath, "UTF-8")
                    } catch ( _ : Exception) {
                        try {
                            Uri.decode(encodedPath)
                        } catch (e2: Exception) {
                            Log.e("MainActivity", "解码路径失败: ${e2.message}")
                            encodedPath
                        }
                    }
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

// ============================================================
// 视频功能
// ============================================================
data class VideoFeature(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    var route: String
)

@Composable
fun VideoMainScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val features = listOf(
        VideoFeature("convert", "视频转换", Icons.Default.VideoLibrary, "video/convert"),
        VideoFeature("gifconvert", "视频转GIF", Icons.Default.Movie, "video/gifconvert"),
        VideoFeature("extract", "音频提取", Icons.Default.Audiotrack, "video/extract"),
        VideoFeature("info", "视频信息", Icons.Default.Info, "video/info")
    )

    FeatureListScreen(
        features = features,
        navController = navController
    )
}

// ============================================================
// 音频功能
// ============================================================
data class AudioFeature(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

@Composable
fun AudioMainScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val features = listOf(
        AudioFeature("convert", "音频转换", Icons.Default.MusicNote, "audio/convert"),
        AudioFeature("info", "音频信息", Icons.Default.Info, "audio/info"),
        AudioFeature("tag_edit", "标签编辑", Icons.Default.Edit, "audio/tag_edit")  // 新增
    )

    FeatureListScreen(
        features = features,
        navController = navController
    )
}

// ============================================================
// 图像功能
// ============================================================
data class ImageFeature(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

@Composable
fun ImageMainScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val features = listOf(
        ImageFeature("convert", "图像转换", Icons.Default.Image, "image/convert")
    )

    FeatureListScreen(
        features = features,
        navController = navController
    )
}

// ============================================================
// 通用功能列表
// ============================================================
@Composable
fun FeatureListScreen(
    features: List<Any>,
    navController: androidx.navigation.NavController
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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

// ============================================================
// 功能卡片
// ============================================================
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

// ============================================================
// 底部导航项
// ============================================================
data class BottomNavItem(
    val title: String,
    val route: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)