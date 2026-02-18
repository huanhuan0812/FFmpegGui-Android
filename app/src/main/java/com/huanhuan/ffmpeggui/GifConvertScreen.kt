package com.huanhuan.ffmpeggui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoToGifScreen(
    onBack: () -> Unit,
    navController: NavController,
    viewModel: FFmpegViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val scrollState = rememberScrollState()
    var isScreenActive by remember { mutableStateOf(true) }

    // 视频文件相关
    var selectedVideoPath by remember { mutableStateOf<String?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    var showAdvancedOptions by remember { mutableStateOf(false) }

    // GIF 参数设置
    var fps by remember { mutableIntStateOf(10) }
    var scale by remember { mutableIntStateOf(480) }
    var quality by remember { mutableIntStateOf(5) } // 1-10
    var loopCount by remember { mutableIntStateOf(0) } // 0 表示无限循环
    var startTime by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var useDither by remember { mutableStateOf(true) }
    var colorsCount by remember { mutableIntStateOf(256) } // 调色板颜色数

    // 预设选项
    val fpsOptions = listOf(5, 10, 15, 20, 25, 30)
    val scaleOptions = listOf(320, 480, 640, 720, 854, 1080)
    val colorsOptions = listOf(64, 128, 256)

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
                    Log.d("Gif",event.message)
                    if (event.success && isScreenActive) {
                        val encodedPath = Uri.encode(event.outputPath)
                        navController.navigate("result/${encodedPath}") {
                            launchSingleTop = true
                            popUpTo("video_to_gif") {
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

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            Toast.makeText(context, "需要存储权限才能选择视频文件", Toast.LENGTH_SHORT).show()
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val path = getPathFromUri(context, it)
            selectedVideoPath = path
            if (outputFileName.isEmpty()) {
                val name = File(path).nameWithoutExtension
                outputFileName = "${name}_gif"
            }
        }
    }

    // 初始化权限检查
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
                title = { Text("视频转GIF") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
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

            // 输出设置卡片
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "GIF输出设置",
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
                            Icon(Icons.Default.Movie, contentDescription = null)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 帧率设置
                    Text(
                        text = "帧率 (FPS): $fps",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = fps.toFloat(),
                        onValueChange = { fps = it.toInt() },
                        valueRange = 1f..30f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        fpsOptions.forEach { option ->
                            FilterChip(
                                selected = fps == option,
                                onClick = { fps = option },
                                label = { Text("$option") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 分辨率设置
                    Text(
                        text = "输出宽度 (像素): $scale",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = scale.toFloat(),
                        onValueChange = { scale = it.toInt() },
                        valueRange = 160f..1920f,
                        steps = 20,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        scaleOptions.forEach { option ->
                            FilterChip(
                                selected = scale == option,
                                onClick = { scale = option },
                                label = { Text("${option}p") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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

                        // 循环设置
                        Text(
                            text = "循环次数",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = loopCount == 0,
                                onClick = { loopCount = 0 },
                                label = { Text("无限循环") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = loopCount == 1,
                                onClick = { loopCount = 1 },
                                label = { Text("播放1次") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = loopCount == 3,
                                onClick = { loopCount = 3 },
                                label = { Text("播放3次") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 质量设置
                        Text(
                            text = "质量 (${quality}/10)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = quality.toFloat(),
                            onValueChange = { quality = it.toInt() },
                            valueRange = 1f..10f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 颜色数量
                        Text(
                            text = "颜色数量",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            colorsOptions.forEach { option ->
                                FilterChip(
                                    selected = colorsCount == option,
                                    onClick = { colorsCount = option },
                                    label = { Text("$option 色") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 时间截取设置
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = startTime,
                                onValueChange = { startTime = it },
                                label = { Text("开始时间 (秒)") },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("0") }
                            )
                            OutlinedTextField(
                                value = duration,
                                onValueChange = { duration = it },
                                label = { Text("持续时间 (秒)") },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("全部") }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 抖动选项
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = useDither,
                                onClick = { useDither = true },
                                label = { Text("启用抖动") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = !useDither,
                                onClick = { useDither = false },
                                label = { Text("禁用抖动") },
                                modifier = Modifier.weight(1f)
                            )
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
                                text = "正在转换GIF...",
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
                    val outputDir = File(context.getExternalFilesDir(null), "FFmpegOutput/GIF")
                    if (!outputDir.exists()) {
                        outputDir.mkdirs()
                    }

                    val outputFile = File(outputDir, "${outputFileName}_${timestamp}.gif")

                    // 构建设置字符串用于显示
                    val settings = buildString {
                        append("帧率: ${fps}fps")
                        append(", 宽度: ${scale}px")
                        append(", 颜色: ${colorsCount}色")
                        if (loopCount == 0) {
                            append(", 循环: 无限")
                        } else {
                            append(", 循环: ${loopCount}次")
                        }
                        if (startTime.isNotBlank()) {
                            append(", 开始: ${startTime}s")
                        }
                        if (duration.isNotBlank()) {
                            append(", 时长: ${duration}s")
                        }
                    }

                    Toast.makeText(context, settings, Toast.LENGTH_SHORT).show()

                    // 调用ViewModel的视频转GIF方法
                    viewModel.convertVideoToGif(
                        inputPath = selectedVideoPath!!,
                        outputPath = outputFile.absolutePath,
                        fps = fps,
                        scale = scale,
                        quality = quality,
                        loopCount = loopCount,
                        colors = colorsCount,
                        startTime = startTime.ifBlank { null },
                        duration = duration.ifBlank { null },
                        useDither = useDither,
                        onComplete = { _, _ -> }
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
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始转换GIF")
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}