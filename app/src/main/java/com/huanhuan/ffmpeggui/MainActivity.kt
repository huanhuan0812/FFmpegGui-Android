package com.huanhuan.ffmpeggui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 在应用启动时检查更新
        UpdateChecker.checkForUpdates()

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

    // 更新状态
    val updateState by UpdateChecker.updateState.collectAsState()

    // 控制更新对话框的显示
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    // 处理更新状态变化
    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateChecker.UpdateState.Available -> {
                // 延迟一点显示，避免启动时立即弹出影响用户体验
                delay(500)
                updateInfo = (updateState as UpdateChecker.UpdateState.Available).updateInfo
                showUpdateDialog = true
            }
            is UpdateChecker.UpdateState.Error -> {
                errorMessage = (updateState as UpdateChecker.UpdateState.Error).message
                showErrorDialog = true
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FFmpeg GUI")
                        if (updateState is UpdateChecker.UpdateState.Checking) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                },
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
//                    FrameExtractScreen(
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

    // 更新可用对话框
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = {
                showUpdateDialog = false
                UpdateChecker.resetState()
            }
        )
    }

    // 更新错误对话框
    if (showErrorDialog) {
        UpdateErrorDialog(
            message = errorMessage,
            onDismiss = {
                showErrorDialog = false
                UpdateChecker.resetState()
            },
            onRetry = {
                showErrorDialog = false
                UpdateChecker.checkForUpdates()
            }
        )
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