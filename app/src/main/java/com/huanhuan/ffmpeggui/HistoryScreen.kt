package com.huanhuan.ffmpeggui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onNavigateToResult: (String) -> Unit,
    viewModel: FFmpegViewModel = viewModel()
) {
    val context = LocalContext.current

    // 初始化数据库（如果还未初始化）
    LaunchedEffect(Unit) {
        viewModel.initDatabase(context)
    }

    // 监听处理事件，自动刷新历史列表
    LaunchedEffect(Unit) {
        viewModel.processingEvents.collectLatest { event ->
            // 当有事件发生时，延迟一点刷新历史列表
            delay(500)
            viewModel.loadHistory()
        }
    }

    var showClearAllDialog by remember { mutableStateOf(false) }
    var showClearCompletedDialog by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<ConversionTask?>(null) }
    var showFileNotExistDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 操作按钮行
        if (viewModel.historyTasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 清理已完成文件按钮
                TextButton(
                    onClick = { showClearCompletedDialog = true },
                    modifier = Modifier
                ) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = "清理已完成文件",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清理已完成")
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 清空全部按钮
                TextButton(
                    onClick = { showClearAllDialog = true }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "清空全部",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空全部")
                }
            }
        }

        // 历史列表内容
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (viewModel.historyTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无历史记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.historyTasks, key = { it.id }) { task ->
                        HistoryItem(
                            task = task,
                            onDeleteClick = { taskToDelete = task },
                            onItemClick = {
                                try {
                                    // 使用统一的文件检查工具
                                    val fileExists = fileExistsCompat(context, task.outputPath)
                                    if (fileExists) {
                                        onNavigateToResult(task.outputPath)
                                    } else {
                                        showFileNotExistDialog = true
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    showFileNotExistDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 文件不存在提示对话框
    if (showFileNotExistDialog) {
        AlertDialog(
            onDismissRequest = { showFileNotExistDialog = false },
            title = { Text("文件不存在") },
            text = { Text("该输出文件已被删除或移动，无法查看结果。") },
            confirmButton = {
                TextButton(onClick = { showFileNotExistDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    // 删除单个项目的确认对话框
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("删除历史记录") },
            text = {
                val fileName = try {
                    File(taskToDelete!!.outputPath).name
                } catch ( _ : Exception) {
                    "未知文件"
                }
                Text("确定要删除这条记录及其对应的输出文件吗？\n\n文件：$fileName")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            viewModel.clearHistoryItem(taskToDelete!!)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        taskToDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 清空所有历史的确认对话框
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空所有历史") },
            text = {
                Text("确定要清空所有历史记录并删除对应的输出文件吗？\n\n共有 ${viewModel.historyTasks.size} 条记录")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            viewModel.clearAllHistory()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        showClearAllDialog = false
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 清理已完成文件的确认对话框
    if (showClearCompletedDialog) {
        val completedCount = viewModel.historyTasks.count { it.status == "完成" }
        AlertDialog(
            onDismissRequest = { showClearCompletedDialog = false },
            title = { Text("清理已完成文件") },
            text = {
                Text("确定要删除所有已完成任务的输出文件吗？\n\n共 $completedCount 个文件\n\n（历史记录将保留）")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            viewModel.clearCompletedOutputFiles()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        showClearCompletedDialog = false
                    }
                ) {
                    Text("清理")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCompletedDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun HistoryItem(
    task: ConversionTask,
    onDeleteClick: () -> Unit,
    onItemClick: () -> Unit
) {
    val context = LocalContext.current

    // 使用统一的文件检查工具，并添加异常处理
    val outputFileExists = remember(task.outputPath, context) {
        try {
            fileExistsCompat(context, task.outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 缓存文件名，避免重复创建File对象
    val inputFileName = remember(task.inputPath) {
        try {
            if (task.inputPath.isNotEmpty()) {
                File(task.inputPath).name
            } else {
                "未知文件"
            }
        } catch ( _ : Exception) {
            "未知文件"
        }
    }

    val outputFileName = remember(task.outputPath) {
        try {
            if (task.outputPath.isNotEmpty()) {
                File(task.outputPath).name
            } else {
                "未知文件"
            }
        } catch ( _ : Exception) {
            "未知文件"
        }
    }

    // 根据文件是否存在决定点击行为
    val canClick = outputFileExists

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canClick) {
                if (canClick) {
                    onItemClick()
                }
            }
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
                    text = task.type,
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 状态标签
                    Surface(
                        color = when (task.status) {
                            "完成" -> MaterialTheme.colorScheme.primaryContainer
                            "失败" -> MaterialTheme.colorScheme.errorContainer
                            "已取消" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = task.status,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (task.status) {
                                "完成" -> MaterialTheme.colorScheme.onPrimaryContainer
                                "失败" -> MaterialTheme.colorScheme.onErrorContainer
                                "已取消" -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 删除按钮
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 显示输入文件名
            Text(
                text = "输入: $inputFileName",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            // 显示输出文件名
            Text(
                text = "输出: $outputFileName",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 开始时间
                Text(
                    text = formatTimestamp(task.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                // ========== 修改：耗时显示逻辑 ==========
                val endTimeValue = task.endTime
                if (endTimeValue != null && endTimeValue > 0 && endTimeValue > task.startTime) {
                    val duration = (endTimeValue - task.startTime) / 1000
                    Text(
                        text = "耗时: ${duration}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    // 根据状态显示不同内容
                    when (task.status) {
                        "进行中", "处理中" -> {
                            Text(
                                text = "处理中...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        "完成" -> {
                            // 对于完成但没有耗时的任务（旧数据），显示 "-"
                            Text(
                                text = "耗时: -",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        else -> {
                            Text(
                                text = "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // 进度指示器
                when {
                    task.status == "完成" && outputFileExists -> {
                        LinearProgressIndicator(
                            progress = { 1.0f },
                            modifier = Modifier
                                                        .width(80.dp)
                                                        .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                    }

                    task.status == "完成" && !outputFileExists -> {
                        Text(
                            text = "文件已删除",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    task.status == "进行中" || task.status == "处理中" -> {
                        LinearProgressIndicator(
                            progress = { task.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                                        .width(80.dp)
                                                        .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                    }

                    else -> {
                        Spacer(modifier = Modifier.width(80.dp))
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    return try {
        if (timestamp <= 0) {
            return "未知时间"
        }
        val date = Date(timestamp)
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        format.format(date)
    } catch ( _ : Exception) {
        "未知时间"
    }
}