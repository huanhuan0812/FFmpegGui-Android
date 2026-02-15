package com.huanhuan.ffmpeggui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
fun AudioConvertScreen(
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

    var selectedAudioPath by remember { mutableStateOf<String?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    var selectedOutputFormat by remember { mutableStateOf("mp3") }
    var selectedBitrate by remember { mutableStateOf("192k") }
    var selectedSampleRate by remember { mutableStateOf("44100") }
    var selectedChannels by remember { mutableStateOf("2") }
    var showAdvancedOptions by remember { mutableStateOf(false) }

    val audioFormats = listOf("mp3", "aac", "flac", "wav", "ogg", "m4a")

    // 根据选择的格式动态调整比特率选项
    val bitrates = if (selectedOutputFormat == "ogg") {
        // Opus 在低比特率表现更好，提供更精细的选项
        listOf("64k", "96k", "128k", "160k", "192k")
    } else {
        listOf("128k", "192k", "256k", "320k")
    }

    val sampleRates = listOf("22050", "44100", "48000")
    val channels = listOf("1", "2")

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
                            popUpTo("audio_convert") {
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
            selectedAudioPath = path
            if (outputFileName.isEmpty()) {
                val name = File(path).nameWithoutExtension
                outputFileName = "${name}_converted"
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
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
                title = { Text("音频格式转换") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
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
                        text = "选择音频文件",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { filePickerLauncher.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择音频")
                    }

                    if (selectedAudioPath != null) {
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
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = File(selectedAudioPath!!).name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 2
                                    )
                                    Text(
                                        text = formatFileSize(File(selectedAudioPath!!).length()),
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
                        audioFormats.forEach { format ->
                            FilterChip(
                                selected = selectedOutputFormat == format,
                                onClick = {
                                    selectedOutputFormat = format
                                    // 根据格式推荐默认设置
                                    when (format) {
                                        "mp3" -> {
                                            selectedBitrate = "192k"
                                            selectedSampleRate = "44100"
                                        }
                                        "aac", "m4a" -> {
                                            selectedBitrate = "192k"
                                            selectedSampleRate = "44100"
                                        }
                                        "flac" -> {
                                            selectedBitrate = ""
                                            selectedSampleRate = "48000"
                                        }
                                        "wav" -> {
                                            selectedBitrate = ""
                                            selectedSampleRate = "44100"
                                        }
                                        "ogg" -> {
                                            selectedBitrate = "128k"  // Opus 推荐比特率
                                            selectedSampleRate = "48000"  // Opus 最佳采样率
                                        }
                                    }
                                },
                                label = { Text(format.uppercase()) },
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

                        // 比特率设置（仅对部分格式有效）
                        if (selectedOutputFormat in listOf("mp3", "aac", "m4a", "ogg")) {
                            Text(
                                text = "比特率",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                bitrates.forEach { bitrate ->
                                    FilterChip(
                                        selected = selectedBitrate == bitrate,
                                        onClick = { selectedBitrate = bitrate },
                                        label = { Text(bitrate) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 采样率
                        Text(
                            text = "采样率 (Hz)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sampleRates.forEach { rate ->
                                FilterChip(
                                    selected = selectedSampleRate == rate,
                                    onClick = { selectedSampleRate = rate },
                                    label = { Text("$rate Hz") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 声道数
                        Text(
                            text = "声道",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            channels.forEach { channel ->
                                FilterChip(
                                    selected = selectedChannels == channel,
                                    onClick = { selectedChannels = channel },
                                    label = { Text(if (channel == "1") "单声道" else "立体声") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Opus 特定选项（当选择 OGG 格式时显示）
                        if (selectedOutputFormat == "ogg") {
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Opus 优化选项",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 应用场景选择
                            var selectedApplication by remember { mutableStateOf("audio") }
                            val applications = listOf(
                                "audio" to "音频 (高质量)",
                                "lowdelay" to "低延迟 (实时通信)"
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                applications.forEach { (value, label) ->
                                    FilterChip(
                                        selected = selectedApplication == value,
                                        onClick = { selectedApplication = value },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // 将选择的值传递给 ViewModel（可以在点击转换时处理）
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
                    if (selectedAudioPath == null) {
                        Toast.makeText(context, "请选择音频文件", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (outputFileName.isBlank()) {
                        Toast.makeText(context, "请输入输出文件名", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val outputDir = File(context.getExternalFilesDir(null), "FFmpegOutput/Audio")
                    if (!outputDir.exists()) {
                        outputDir.mkdirs()
                    }

                    val outputFile = File(outputDir, "${outputFileName}_${timestamp}.${selectedOutputFormat}")

                    // 构建设置字符串用于显示
                    val settings = buildString {
                        append("格式: ${selectedOutputFormat.uppercase()}")
                        if (selectedOutputFormat == "ogg") {
                            append(" (Opus编码)")
                        }
                        if (selectedBitrate.isNotBlank() && selectedOutputFormat in listOf("mp3", "aac", "m4a", "ogg")) {
                            append(", 比特率: $selectedBitrate")
                        }
                        append(", 采样率: ${selectedSampleRate}Hz")
                        append(", 声道: ${if (selectedChannels == "1") "单声道" else "立体声"}")
                    }

                    Toast.makeText(context, settings, Toast.LENGTH_SHORT).show()

                    // 移除回调，通过 processingEvents 处理结果
                    viewModel.convertAudio(
                        inputPath = selectedAudioPath!!,
                        outputPath = outputFile.absolutePath,
                        format = selectedOutputFormat,
                        bitrate = selectedBitrate,
                        sampleRate = selectedSampleRate,
                        channels = selectedChannels,
                        onComplete = { _, _ -> } // 空实现
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !viewModel.isProcessing && selectedAudioPath != null
            ) {
                if (viewModel.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("转换中...")
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
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