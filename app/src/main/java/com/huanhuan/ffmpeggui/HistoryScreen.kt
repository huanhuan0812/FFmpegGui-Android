package com.huanhuan.ffmpeggui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onNavigateToResult: (String) -> Unit, // 添加导航到结果页面的回调
    viewModel: FFmpegViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        //自动更新历史记录列表
        viewModel.processingEvents.collect { event ->

        }
    }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showClearCompletedDialog by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<ConversionTask?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史记录") },
                actions = {
                    if (viewModel.historyTasks.isNotEmpty()) {
                        // 清空已完成文件按钮
                        IconButton(onClick = { showClearCompletedDialog = true }) {
                            Icon(Icons.Default.CleaningServices, contentDescription = "清理已完成文件")
                        }
                        // 清空所有历史按钮
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "清空全部")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (viewModel.historyTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.historyTasks, key = { it.id }) { task ->
                    HistoryItem(
                        task = task,
                        onDeleteClick = { taskToDelete = task },
                        onItemClick = {
                            // 点击历史项跳转到结果页面
                            if (File(task.outputPath).exists()) {
                                onNavigateToResult(task.outputPath)
                            }
                        }
                    )
                }
            }
        }
    }

    // 删除单个项目的确认对话框
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("删除历史记录") },
            text = {
                Text("确定要删除这条记录及其对应的输出文件吗？\n\n文件：${File(taskToDelete!!.outputPath).name}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistoryItem(taskToDelete!!)
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
                        viewModel.clearAllHistory()
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
                Text("确定要删除所有已完成任务的输出文件吗？\n\n共 ${completedCount} 个文件\n\n（历史记录将保留）")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCompletedOutputFiles()
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() } // 添加点击事件
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

            Text(
                text = "输入: ${File(task.inputPath).name}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )

            Text(
                text = "输出: ${File(task.outputPath).name}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(task.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (task.endTime != null) {
                    val duration = ((task.endTime ?: 0) - (task.startTime ?: 0)) / 1000
                    Text(
                        text = "耗时: ${duration}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (task.status == "完成") {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .width(80.dp)
                            .height(4.dp),
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                } else if (!File(task.outputPath).exists() && task.status == "完成") {
                    // 如果文件不存在但状态是完成，显示提示
                    Text(
                        text = "文件已删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return format.format(date)
}