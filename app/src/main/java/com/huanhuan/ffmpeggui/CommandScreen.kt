package com.huanhuan.ffmpeggui

// 主题
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.huanhuan.ffmpeggui.ui.theme.DarkColorScheme
import com.huanhuan.ffmpeggui.ui.theme.LightColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FFmpegDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// 命令屏幕 - 带导航和返回功能
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandScreen(
    navController: NavController,
    onBack: () -> Unit,
    viewModel: FFmpegViewModel = viewModel()
) {
    var arguments by remember { mutableStateOf("-c:v libx264 -preset medium -crf 23") }
    var inputFilePath by remember { mutableStateOf("") }
    var inputUri by remember { mutableStateOf<Uri?>(null) }
    var outputFileName by remember { mutableStateOf("output.mp4") }
    val executionResult by viewModel.executionResult.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()
    val context = LocalContext.current

    // 为输入框和结果区域分别创建滚动状态
    val inputScrollState = rememberScrollState()
    val resultScrollState = rememberScrollState()

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            inputUri = it
            // 获取文件路径
            try {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            val fileName = c.getString(nameIndex)
                            // 获取文件路径（对于某些Uri可能无法获取真实路径）
                            val path = it.path ?: ""
                            inputFilePath = path
                            // 自动设置输出文件名
                            val baseName = fileName.substringBeforeLast(".")
                            outputFileName = "${baseName}_output.mp4"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                inputFilePath = it.path ?: ""
            }
        }
    }

    // 当结果更新时自动滚动到底部
    LaunchedEffect(executionResult) {
        if (isExecuting) {
            kotlinx.coroutines.delay(100)
            resultScrollState.animateScrollTo(resultScrollState.maxValue)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FFmpeg 命令执行器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 上半部分：命令输入和结果
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 文件选择和输出文件名行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "选择输入文件",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (inputFilePath.isNotBlank()) {
                                File(inputFilePath).name
                            } else {
                                "选择输入文件"
                            },
                            maxLines = 1
                        )
                    }

                    OutlinedTextField(
                        value = outputFileName,
                        onValueChange = { outputFileName = it },
                        label = { Text("输出文件名") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        )
                    )
                }

                // 参数输入卡片
                ArgumentsInputCard(
                    arguments = arguments,
                    onArgumentsChange = { arguments = it },
                    inputFilePath = inputFilePath,
                    scrollState = inputScrollState
                )

                // 构建完整的命令显示
                if (inputFilePath.isNotBlank() && arguments.isNotBlank()) {
                    val fullCommand = buildFullCommand(arguments, inputFilePath, outputFileName)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "📋 完整命令预览",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "ffmpeg $fullCommand",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }

                ExecutionResultCard(
                    executionResult = executionResult,
                    onClear = {
                        viewModel.clearResult()
                        CoroutineScope(Dispatchers.Main).launch {
                            resultScrollState.scrollTo(0)
                        }
                    },
                    scrollState = resultScrollState
                )
            }

            // 下半部分：执行按钮和取消按钮固定在底部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExecuteButton(
                    onClick = {
                        if (arguments.isNotBlank() && inputFilePath.isNotBlank()) {
                            val fullCommand = buildFullCommand(arguments, inputFilePath, outputFileName)
                            viewModel.executeCommand(fullCommand)
                        }
                    },
                    isExecuting = isExecuting,
                    enabled = arguments.isNotBlank() && inputFilePath.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )

                if (isExecuting) {
                    OutlinedButton(
                        onClick = { viewModel.cancelExecution() },
                        modifier = Modifier.weight(0.5f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

// 构建完整命令
private fun buildFullCommand(arguments: String, inputFilePath: String, outputFileName: String): String {
    // 清理输入文件路径中的引号
    val cleanInputPath = inputFilePath.trim('"')
    val cleanOutputFileName = outputFileName.trim('"')

    // 检查参数中是否已经包含 -i
    return if (arguments.contains("-i")) {
        // 如果参数中已经有 -i，替换或添加输出文件名
        if (arguments.contains("output") || arguments.contains("out")) {
            // 如果已经有输出，替换输出文件名
            arguments.replace(Regex("output\\.[a-zA-Z0-9]+"), cleanOutputFileName)
        } else {
            "$arguments $cleanOutputFileName"
        }
    } else {
        // 如果没有 -i，添加输入文件
        "-i \"$cleanInputPath\" $arguments $cleanOutputFileName"
    }
}

// 参数输入卡片
@Composable
fun ArgumentsInputCard(
    arguments: String,
    onArgumentsChange: (String) -> Unit,
    inputFilePath: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 显示选中的输入文件
            if (inputFilePath.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📁 输入文件",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = File(inputFilePath).name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, true)
                    )
                }
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }

            // 多行参数输入框
            OutlinedTextField(
                value = arguments,
                onValueChange = onArgumentsChange,
                label = { Text("FFmpeg 参数") },
                placeholder = {
                    Text("例如：-c:v libx264 -preset medium -crf 23")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp),
                singleLine = false,
                maxLines = Int.MAX_VALUE,
                minLines = 5,
                isError = arguments.isBlank(),
                supportingText = {
                    when {
                        arguments.isBlank() -> Text("⚠️ 参数不能为空")
                        inputFilePath.isBlank() -> Text("⚠️ 请先选择输入文件")
                        else -> Text("💡 无需输入 -i 和输出文件名")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Unspecified
                ),
                shape = MaterialTheme.shapes.medium
            )

            // 简洁的使用提示
            Text(
                text = "💡 仅输入参数即可，系统自动添加输入/输出路径",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// 执行结果卡片
@Composable
fun ExecutionResultCard(
    executionResult: String,
    onClear: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "执行结果",
                    style = MaterialTheme.typography.titleMedium
                )

                if (executionResult.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清空结果"
                        )
                    }
                }
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            // 可滚动的结果区域
            SelectionContainer {
                Text(
                    text = executionResult.ifEmpty {
                        "暂无执行结果\n\n请选择输入文件并输入参数后执行"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp)
                        .verticalScroll(scrollState),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = when {
                            executionResult.contains("❌") -> MaterialTheme.colorScheme.error
                            executionResult.contains("✅") -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                )
            }
        }
    }
}

// 执行按钮
@Composable
fun ExecuteButton(
    onClick: () -> Unit,
    isExecuting: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isExecuting
    ) {
        if (isExecuting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("执行中...")
        } else {
            Text("执行命令")
        }
    }
}

// 预览
@Preview(showBackground = true, name = "完整屏幕预览", heightDp = 800)
@Composable
fun PreviewCommandScreen() {
    FFmpegDemoTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CommandScreen(
                navController = rememberNavController(),
                onBack = {},
                viewModel = viewModel()
            )
        }
    }
}