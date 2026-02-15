package com.huanhuan.ffmpeggui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoConvertScreen(
    onBack: () -> Unit,
    navController: NavController,
    viewModel: FFmpegViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // 添加滚动状态
    val scrollState = rememberScrollState()

    // 添加屏幕活跃状态
    var isScreenActive by remember { mutableStateOf(true) }

    var selectedVideoPath by remember { mutableStateOf<String?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    var selectedOutputFormat by remember { mutableStateOf("mp4") }
    var selectedVideoCodec by remember { mutableStateOf("h264") }
    var selectedAudioCodec by remember { mutableStateOf("aac") }
    var selectedQuality by remember { mutableStateOf("medium") }
    var selectedResolution by remember { mutableStateOf("original") }
    var showAdvancedOptions by remember { mutableStateOf(false) }

    val videoFormats = listOf("mp4", "avi", "mkv", "mov", "3gp", "webm")
    val videoCodecs = listOf("h264", "h265", "mpeg4", "vp9")
    val audioCodecs = listOf("aac", "mp3", "copy")
    val qualities = listOf("low", "medium", "high")
    val resolutions = listOf("original", "1080p", "720p", "480p", "360p")

    // 监听生命周期
    DisposableEffect(lifecycleOwner) {
        onDispose {
            isScreenActive = false
        }
    }

    // 监听处理事件
    LaunchedEffect(viewModel) {
        viewModel.processingEvents.collect { event ->
            when (event) {
                is ProcessingEvent.Completed -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    if (event.success && isScreenActive) {
                        // 对文件路径进行编码，避免特殊字符问题
                        val encodedPath = Uri.encode(event.outputPath)
                        navController.navigate("result/${encodedPath}") {
                            launchSingleTop = true
                            popUpTo("video_convert") {
                                inclusive = false
                            }
                        }
                    }
                }
                ProcessingEvent.Cancelled -> {
                    Toast.makeText(context, "处理已取消", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            Toast.makeText(context, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val path = getPathFromUri(context, it)
            selectedVideoPath = path
            if (outputFileName.isEmpty()) {
                val name = File(path).nameWithoutExtension
                outputFileName = "${name}_converted"
            }
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频格式转换") },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState) // 添加垂直滚动
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                                    Icons.Default.VideoLibrary,
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

            // 输出设置卡片
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "输出设置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = outputFileName,
                        onValueChange = { outputFileName = it },
                        label = { Text("输出文件名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "目标格式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 格式选择 - 使用FilterChip
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        videoFormats.forEach { format ->
                            FilterChip(
                                selected = selectedOutputFormat == format,
                                onClick = {
                                    selectedOutputFormat = format
                                    // 根据格式推荐编码器
                                    when (format) {
                                        "mp4" -> {
                                            selectedVideoCodec = "h264"
                                            selectedAudioCodec = "aac"
                                        }
                                        "avi" -> {
                                            selectedVideoCodec = "mpeg4"
                                            selectedAudioCodec = "mp3"
                                        }
                                        "mkv" -> {
                                            selectedVideoCodec = "h265"
                                            selectedAudioCodec = "aac"
                                        }
                                        "webm" -> {
                                            selectedVideoCodec = "vp9"
                                            selectedAudioCodec = "copy"
                                        }
                                    }
                                },
                                label = { Text(format.uppercase()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 质量预设
                    Text(
                        text = "质量预设",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        qualities.forEach { quality ->
                            FilterChip(
                                selected = selectedQuality == quality,
                                onClick = { selectedQuality = quality },
                                label = {
                                    Text(
                                        when (quality) {
                                            "low" -> "低质量"
                                            "medium" -> "中等"
                                            "high" -> "高质量"
                                            else -> quality
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 高级选项开关
                    TextButton(
                        onClick = { showAdvancedOptions = !showAdvancedOptions },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showAdvancedOptions) "隐藏高级选项" else "显示高级选项")
                    }

                    // 高级选项
                    if (showAdvancedOptions) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = DividerDefaults.Thickness,
                            color = DividerDefaults.color
                        )

                        // 分辨率
                        Text(
                            text = "分辨率",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            resolutions.forEach { resolution ->
                                FilterChip(
                                    selected = selectedResolution == resolution,
                                    onClick = { selectedResolution = resolution },
                                    label = {
                                        Text(
                                            when (resolution) {
                                                "original" -> "原始"
                                                else -> resolution
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 视频编码器
                        Text(
                            text = "视频编码器",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            videoCodecs.forEach { codec ->
                                FilterChip(
                                    selected = selectedVideoCodec == codec,
                                    onClick = { selectedVideoCodec = codec },
                                    label = {
                                        Text(
                                            when (codec) {
                                                "h264" -> "H.264"
                                                "h265" -> "H.265/HEVC"
                                                "mpeg4" -> "MPEG-4"
                                                "vp9" -> "VP9"
                                                else -> codec
                                            }
                                        )
                                    },
                                    enabled = codec != "h265" || selectedOutputFormat != "mp4"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 音频编码器
                        Text(
                            text = "音频编码器",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            audioCodecs.forEach { codec ->
                                FilterChip(
                                    selected = selectedAudioCodec == codec,
                                    onClick = { selectedAudioCodec = codec },
                                    label = {
                                        Text(
                                            when (codec) {
                                                "aac" -> "AAC"
                                                "mp3" -> "MP3"
                                                "copy" -> "复制原音频"
                                                else -> codec
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 处理状态
            if (viewModel.isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "正在转换...",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${(viewModel.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { viewModel.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                        if (viewModel.currentCommand.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = viewModel.currentCommand,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 开始转换按钮
            Button(
                onClick = {
                    if (selectedVideoPath == null) {
                        Toast.makeText(context, "请选择视频文件", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (outputFileName.isBlank()) {
                        Toast.makeText(context, "请输入输出文件名", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val outputDir = File(context.getExternalFilesDir(null), "FFmpegOutput/Video")
                    if (!outputDir.exists()) {
                        outputDir.mkdirs()
                    }

                    val outputFile = File(outputDir, "${outputFileName}_${timestamp}.${selectedOutputFormat}")

                    // 构建设置字符串用于显示
                    val settings = buildString {
                        append("格式: ${selectedOutputFormat.uppercase()}")
                        append(", 编码: ${selectedVideoCodec.uppercase()}")
                        append(", 音频: ${selectedAudioCodec.uppercase()}")
                        append(", 质量: ${selectedQuality}")
                        if (selectedResolution != "original") {
                            append(", 分辨率: $selectedResolution")
                        }
                    }

                    Toast.makeText(context, settings, Toast.LENGTH_SHORT).show()

                    // 移除回调，通过 processingEvents 处理结果
                    viewModel.convertVideo(
                        inputPath = selectedVideoPath!!,
                        outputPath = outputFile.absolutePath,
                        format = selectedOutputFormat,
                        videoCodec = selectedVideoCodec,
                        audioCodec = selectedAudioCodec,
                        quality = selectedQuality,
                        resolution = selectedResolution,
                        onComplete = { _, _ -> } // 空实现
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !viewModel.isProcessing && selectedVideoPath != null
            ) {
                if (viewModel.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("转换中...")
                } else {
                    Icon(Icons.Default.VideoFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始转换")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 取消按钮（处理中时显示）
            if (viewModel.isProcessing) {
                OutlinedButton(
                    onClick = {
                        viewModel.cancelCurrentProcessing()
                        Toast.makeText(context, "正在取消...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("取消")
                }
            }

            // 添加底部间距，确保滚动时最后一个元素不会被底部导航栏遮挡
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}