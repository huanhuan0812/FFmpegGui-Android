package com.huanhuan.ffmpeggui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioExtractScreen(
    onBack: () -> Unit,
    navController: NavController,
    viewModel: FFmpegViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedVideoPath by remember { mutableStateOf<String?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    var selectedAudioFormat by remember { mutableStateOf("mp3") }
    val audioFormats = listOf("mp3", "aac", "flac", "wav", "ogg")

    // 添加一个状态来跟踪屏幕是否仍然活动
    var isScreenActive by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            isScreenActive = false
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
                outputFileName = "${name}_audio"
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.processingEvents.collect { event ->
            when (event) {
                is ProcessingEvent.Completed -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    if (event.success && isScreenActive) {
                        // 确保导航在屏幕活跃时执行
                        navController.navigate("result/${Uri.encode(event.outputPath)}") {
                            launchSingleTop = true
                            popUpTo("audio_extract") {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音频提取") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 文件选择
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
                        Text(
                            text = File(selectedVideoPath!!).name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 输出设置
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
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "音频格式",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        audioFormats.forEach { format ->
                            FilterChip(
                                selected = selectedAudioFormat == format,
                                onClick = { selectedAudioFormat = format },
                                label = { Text(format.uppercase()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 处理状态
            if (viewModel.isProcessing) {
                LinearProgressIndicator(
                    progress = viewModel.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在处理: ${(viewModel.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

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
                    val outputDir = File(context.getExternalFilesDir(null), "FFmpegOutput")
                    if (!outputDir.exists()) {
                        outputDir.mkdirs()
                    }

                    val outputFile = File(outputDir, "${outputFileName}_${timestamp}.${selectedAudioFormat}")

                    // 移除回调，因为我们现在通过 processingEvents 处理结果
                    viewModel.extractAudio(
                        inputPath = selectedVideoPath!!,
                        outputPath = outputFile.absolutePath
                    ) { _, _ ->
                        // 空实现，结果通过 processingEvents 处理
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !viewModel.isProcessing
            ) {
                if (viewModel.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("处理中...")
                } else {
                    Icon(Icons.Default.AudioFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始提取音频")
                }
            }
        }
    }
}