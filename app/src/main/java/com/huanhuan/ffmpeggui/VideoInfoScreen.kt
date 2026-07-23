package com.huanhuan.ffmpeggui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.huanhuan.ffmpeggui.FFmpegViewModel.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VideoInfoScreen(
    onBack: () -> Unit,
    navController: NavController,
    viewModel: FFmpegViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var selectedVideoPath by remember { mutableStateOf<String?>(null) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isScreenActive by remember { mutableStateOf(true) }

    // 监听生命周期
    DisposableEffect(lifecycleOwner) {
        onDispose {
            isScreenActive = false
        }
    }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            Toast.makeText(context, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show()
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val path = getPathFromUri(context, it)
            selectedVideoPath = path
            if (path != null) {
                loadVideoInfo(path, viewModel, { info ->
                    videoInfo = info
                    isLoading = false
                }, { error ->
                    errorMessage = error
                    isLoading = false
                })
            }
        }
    }

    // 请求权限
    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 标题和返回按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "视频信息",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 文件选择卡片
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "选择视频文件",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { filePickerLauncher.launch("video/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择视频")
                    }

                    if (selectedVideoPath != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = File(selectedVideoPath!!).name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 2
                                    )
                                    Text(
                                        text = formatFileSize(File(selectedVideoPath!!).length()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 内容区域
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "正在读取视频信息...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "错误",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = errorMessage ?: "未知错误",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (selectedVideoPath != null) {
                                        isLoading = true
                                        errorMessage = null
                                        loadVideoInfo(selectedVideoPath!!, viewModel, { info ->
                                            videoInfo = info
                                            isLoading = false
                                        }, { error ->
                                            errorMessage = error
                                            isLoading = false
                                        })
                                    }
                                }
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }
                videoInfo != null -> {
                    VideoInfoContent(videoInfo = videoInfo!!)
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "选择视频",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "请选择视频文件查看信息",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// 加载视频信息的辅助函数
private fun loadVideoInfo(
    path: String,
    viewModel: FFmpegViewModel,
    onSuccess: (VideoInfo) -> Unit,
    onError: (String) -> Unit
) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val info = withContext(Dispatchers.IO) {
                viewModel.getVideoInfo(path)
            }
            if (info != null) {
                onSuccess(info)
            } else {
                onError("无法读取视频信息\n请确保文件是有效的视频文件")
            }
        } catch (e: Exception) {
            onError("获取视频信息失败: ${e.message}")
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun VideoInfoContent(videoInfo: VideoInfo) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 文件基本信息卡片
        InfoCard(
            title = "文件信息",
            icon = Icons.Default.Movie,
            items = listOf(
                InfoItem("文件名", videoInfo.fileName),
                InfoItem("文件大小", videoInfo.fileSize),
                InfoItem("格式", videoInfo.formatName.ifEmpty { "未知" }),
                InfoItem("总时长", videoInfo.duration),
                InfoItem("包含音频", if (videoInfo.hasAudio) "是" else "否"),
                InfoItem("总流数", "${videoInfo.streamCount}")
            )
        )

        // 视频参数卡片
        InfoCard(
            title = "视频参数",
            icon = Icons.Default.Videocam,
            items = listOf(
                InfoItem("编码器", videoInfo.videoCodec.uppercase()),
                InfoItem("编码器详情", videoInfo.videoCodecLongName.ifEmpty { "未知" }),
                InfoItem("分辨率", videoInfo.resolution),
                InfoItem("帧率", videoInfo.frameRate),
                InfoItem("视频比特率", videoInfo.bitRate),
                InfoItem("像素格式", videoInfo.pixelFormat ?: "未知"),
                InfoItem("色彩空间", videoInfo.colorSpace ?: "未知"),
                InfoItem("HDR", if (videoInfo.hdr) "✅ 支持" else "否")
            )
        )

        // 音频参数卡片（如果有音频流）
        if (videoInfo.hasAudio) {
            InfoCard(
                title = "音频参数",
                icon = Icons.Default.Audiotrack,
                items = listOf(
                    InfoItem("编码器", videoInfo.audioCodec.uppercase()),
                    InfoItem("编码器详情", videoInfo.audioCodecLongName.ifEmpty { "未知" }),
                    InfoItem("采样率", videoInfo.sampleRate),
                    InfoItem("声道", videoInfo.channels),
                    InfoItem("音频比特率", videoInfo.audioBitRate)
                )
            )
        }

        // 时长信息卡片
        InfoCard(
            title = "时长信息",
            icon = Icons.Default.WatchLater,
            items = listOf(
                InfoItem("总时长", videoInfo.duration),
                InfoItem("总秒数", String.format("%.1f 秒", videoInfo.durationSeconds))
            )
        )

        // 分辨率信息卡片
        InfoCard(
            title = "分辨率信息",
            icon = Icons.Default.AspectRatio,
            items = listOf(
                InfoItem("宽度", "${videoInfo.width} px"),
                InfoItem("高度", "${videoInfo.height} px"),
                InfoItem("总像素数", String.format("%,d", videoInfo.width * videoInfo.height)),
                InfoItem("宽高比", getAspectRatio(videoInfo.width, videoInfo.height))
            )
        )

        // 如果有 HDR 标签，显示特殊标识
        if (videoInfo.hdr) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.HdrOn,
                        contentDescription = "HDR",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🎬 此视频支持 HDR 高动态范围",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 底部留白
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 计算宽高比
 */
private fun getAspectRatio(width: Int, height: Int): String {
    if (width == 0 || height == 0) return "未知"

    val gcd = gcd(width, height)
    val w = width / gcd
    val h = height / gcd

    return when {
        w == 16 && h == 9 -> "16:9 (宽屏)"
        w == 4 && h == 3 -> "4:3 (标屏)"
        w == 21 && h == 9 -> "21:9 (超宽)"
        w == 1 && h == 1 -> "1:1 (方形)"
        else -> "${w}:${h}"
    }
}

/**
 * 计算最大公约数
 */
private fun gcd(a: Int, b: Int): Int {
    var num1 = a
    var num2 = b
    while (num2 != 0) {
        val temp = num2
        num2 = num1 % num2
        num1 = temp
    }
    return num1
}