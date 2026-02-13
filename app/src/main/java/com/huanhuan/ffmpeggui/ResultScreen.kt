package com.huanhuan.ffmpeggui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    outputPath: String,
    onBack: () -> Unit,
    onNewTask: () -> Unit
) {
    val context = LocalContext.current
    val outputFile = File(outputPath)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("处理完成") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
        fileName.endsWith(".mp3") -> "audio/mpeg"
        fileName.endsWith(".aac") -> "audio/aac"
        fileName.endsWith(".flac") -> "audio/flac"
        fileName.endsWith(".wav") -> "audio/wav"
        fileName.endsWith(".ogg") -> "audio/ogg"
        else -> "*/*"
    }
}