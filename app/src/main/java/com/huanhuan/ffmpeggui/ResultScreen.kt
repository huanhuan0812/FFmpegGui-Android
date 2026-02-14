package com.huanhuan.ffmpeggui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

// 权限检查扩展函数
fun Context.hasStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+ 使用分区存储，不需要传统存储权限
        true
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

// 请求权限的返回类型
sealed class ExportResult {
    object Success : ExportResult()
    data class Error(val message: String) : ExportResult()
    object PermissionDenied : ExportResult()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    outputPath: String,
    onBack: () -> Unit,
    onNewTask: () -> Unit
) {
    val context = LocalContext.current
    val outputFile = File(outputPath)
    var showExportDialog by remember { mutableStateOf(false) }
    var isExportedSuccessfully by remember { mutableStateOf(false) } // 标记是否导出成功

    // 导出文件函数
    fun exportFile(destination: String): ExportResult {
        return try {
            val success = when (destination) {
                "下载" -> saveToDownloads(context, outputFile)
                "相册" -> saveToPictures(context, outputFile)
                "音乐" -> saveToMusic(context, outputFile)
                else -> false
            }

            if (success) {
                ExportResult.Success
            } else {
                ExportResult.Error("导出失败")
            }
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "未知错误")
        }
    }

    // 离开界面时检查是否导出成功，成功则删除临时文件
    DisposableEffect(Unit) {
        onDispose {
            if (isExportedSuccessfully && outputFile.exists()) {
                outputFile.delete()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("处理完成") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Text(
                    text = "处理成功！",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "输出文件信息",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        InfoRow("文件名", outputFile.name)
                        InfoRow("路径", outputFile.parentFile?.name ?: "")
                        InfoRow("大小", formatFileSize(outputFile.length()))
                        InfoRow("修改时间", formatDate(outputFile.lastModified()))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 播放按钮
                    OutlinedButton(
                        onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    outputFile
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, getMimeType(outputFile.name))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开文件", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("播放")
                    }

                    // 导出按钮
                    Button(
                        onClick = { showExportDialog = true }
                    ) {
                        Icon(Icons.Default.ImportExport, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导出到")
                    }

                    // 分享按钮
                    Button(
                        onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    outputFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = getMimeType(outputFile.name)
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "分享文件")
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法分享文件", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("分享")
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onNewTask,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新建任务")
                }
            }
        }
    }

    // 导出选项对话框
    if (showExportDialog) {
        ExportDialog(
            file = outputFile,
            onDismiss = { showExportDialog = false },
            onExport = { destination ->
                // 检查权限
                if (!context.hasStoragePermission()) {
                    Toast.makeText(context, "需要存储权限才能导出文件", Toast.LENGTH_SHORT).show()
                    showExportDialog = false
                    return@ExportDialog
                }

                // 执行导出
                val result = exportFile(destination)
                when (result) {
                    is ExportResult.Success -> {
                        isExportedSuccessfully = true
                        Toast.makeText(
                            context,
                            "文件已导出到${destination}目录",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is ExportResult.Error -> {
                        Toast.makeText(
                            context,
                            "导出失败: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    ExportResult.PermissionDenied -> {
                        Toast.makeText(context, "权限被拒绝", Toast.LENGTH_SHORT).show()
                    }
                }
                showExportDialog = false
            }
        )
    }
}

@Composable
fun ExportDialog(
    file: File,
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    // 根据文件类型过滤可用选项
    val allOptions = listOf("下载", "相册", "音乐")
    val availableOptions = remember(file) {
        if (isVideoFile(file.name)) {
            allOptions // 视频文件显示所有选项
        } else {
            allOptions.filter { it != "相册" } // 非视频文件不显示相册选项
        }
    }

    var selectedOption by remember { mutableStateOf(availableOptions.firstOrNull() ?: "下载") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出文件") },
        text = {
            Column {
                Text("请选择导出位置")
                if (!isVideoFile(file.name)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "注：只有视频文件才能导出到相册",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                availableOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onExport(selectedOption)
                },
                enabled = selectedOption.isNotEmpty()
            ) {
                Text("导出")
            }
        }
    )
}

// 保存到下载目录
fun saveToDownloads(context: Context, file: File): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveUsingMediaStore(
            context = context,
            file = file,
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            relativePath = Environment.DIRECTORY_DOWNLOADS
        )
    } else {
        saveToPublicDirectory(
            file = file,
            directoryType = Environment.DIRECTORY_DOWNLOADS
        )
    }
}

// 保存到视频目录
fun saveToPictures(context: Context, file: File): Boolean {
    // 添加类型检查，确保只有视频文件才能保存到相册
    if (!isVideoFile(file.name)) {
        return false
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveUsingMediaStore(
            context = context,
            file = file,
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            relativePath = Environment.DIRECTORY_PICTURES
        )
    } else {
        saveToPublicDirectory(
            file = file,
            directoryType = Environment.DIRECTORY_PICTURES
        )
    }
}

// 保存到音乐目录
fun saveToMusic(context: Context, file: File): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveUsingMediaStore(
            context = context,
            file = file,
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            relativePath = Environment.DIRECTORY_MUSIC
        )
    } else {
        saveToPublicDirectory(
            file = file,
            directoryType = Environment.DIRECTORY_MUSIC
        )
    }
}

// 使用 MediaStore API 保存（Android 10+）
private fun saveUsingMediaStore(
    context: Context,
    file: File,
    collection: Uri,
    relativePath: String
): Boolean {
    return try {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }

        val uri = context.contentResolver.insert(collection, contentValues)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } ?: false
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}

// 保存到公共目录（Android 9及以下）
private fun saveToPublicDirectory(file: File, directoryType: String): Boolean {
    return try {
        val dir = Environment.getExternalStoragePublicDirectory(directoryType)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val destFile = File(dir, file.name)
        file.copyTo(destFile, overwrite = true)
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@SuppressLint("DefaultLocale")
fun formatFileSize(size: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var fileSize = size.toFloat()
    var unitIndex = 0

    while (fileSize > 1024 && unitIndex < units.size - 1) {
        fileSize /= 1024
        unitIndex++
    }

    return String.format("%.2f %s", fileSize, units[unitIndex])
}

fun formatDate(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return format.format(date)
}

fun getMimeType(fileName: String): String {
    return when {
        fileName.endsWith(".mp4") -> "video/mp4"
        fileName.endsWith(".avi") -> "video/x-msvideo"
        fileName.endsWith(".mkv") -> "video/x-matroska"
        fileName.endsWith(".mov") -> "video/quicktime"
        fileName.endsWith(".wmv") -> "video/x-ms-wmv"
        fileName.endsWith(".flv") -> "video/x-flv"
        fileName.endsWith(".webm") -> "video/webm"
        fileName.endsWith(".mp3") -> "audio/mpeg"
        fileName.endsWith(".aac") -> "audio/aac"
        fileName.endsWith(".flac") -> "audio/flac"
        fileName.endsWith(".wav") -> "audio/wav"
        fileName.endsWith(".ogg") -> "audio/ogg"
        fileName.endsWith(".m4a") -> "audio/mp4"
        else -> "*/*"
    }
}

// 判断是否为视频文件的辅助函数
fun isVideoFile(fileName: String): Boolean {
    return fileName.endsWith(".mp4") ||
            fileName.endsWith(".avi") ||
            fileName.endsWith(".mkv") ||
            fileName.endsWith(".mov") ||
            fileName.endsWith(".wmv") ||
            fileName.endsWith(".flv") ||
            fileName.endsWith(".m4v") ||
            fileName.endsWith(".3gp") ||
            fileName.endsWith(".webm") ||
            fileName.endsWith(".mpg") ||
            fileName.endsWith(".mpeg")
}