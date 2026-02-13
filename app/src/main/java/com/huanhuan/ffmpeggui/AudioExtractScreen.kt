package com.huanhuan.ffmpeggui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.io.FileOutputStream
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

    // 保存文件到Download文件夹
    fun saveToDownloads(context: Context, sourceFile: File, fileName: String): Uri? {
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            try {
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                uri
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            // Android 9及以下使用传统方式
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, fileName)
                try {
                    sourceFile.copyTo(destFile, overwrite = true)
                    // 发送广播通知系统文件已创建
                    context.sendBroadcast(
                        Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.fromFile(destFile))
                    )
                    Uri.fromFile(destFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else {
                null
            }
        }) as Uri?
    }

    // 根据文件扩展名获取MIME类型
    fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".").lowercase()) {
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
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
                        onClick = {
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                // Android 9及以下需要检查权限
                                val permission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                                if (permission != PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                                    return@Button
                                }
                            }
                            filePickerLauncher.launch("video/*")
                        },
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

                    // 先保存到应用缓存目录作为临时文件
                    val tempDir = File(context.cacheDir, "temp_audio")
                    if (!tempDir.exists()) {
                        tempDir.mkdirs()
                    }
                    val tempFile = File(tempDir, "${outputFileName}_${timestamp}.${selectedAudioFormat}")

                    viewModel.extractAudio(
                        inputPath = selectedVideoPath!!,
                        outputPath = tempFile.absolutePath
                    ) { success, outputPath ->
                        if (success && isScreenActive) {
                            val sourceFile = File(outputPath)
                            val finalFileName = "${outputFileName}_${timestamp}.${selectedAudioFormat}"

                            // 保存到Download文件夹
                            val downloadUri = saveToDownloads(context, sourceFile, finalFileName)

                            if (downloadUri != null) {
                                Toast.makeText(context, "音频已保存到Download文件夹", Toast.LENGTH_LONG).show()

                                // 删除临时文件
                                try {
                                    sourceFile.delete()
                                    tempDir.delete()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                // 导航到结果页面
                                navController.navigate("result/${Uri.encode(downloadUri.toString())}") {
                                    launchSingleTop = true
                                    popUpTo("audio_extract") {
                                        inclusive = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "保存到Download文件夹失败", Toast.LENGTH_LONG).show()
                            }
                        } else if (!success && isScreenActive) {
                            Toast.makeText(context, "音频提取失败", Toast.LENGTH_LONG).show()
                        }
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