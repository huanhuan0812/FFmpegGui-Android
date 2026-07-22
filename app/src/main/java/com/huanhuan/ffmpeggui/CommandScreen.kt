package com.huanhuan.ffmpeggui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "CommandScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandScreen(
    navController: NavController,
    onBack: () -> Unit,
    viewModel: FFmpegViewModel = viewModel()
) {
    //  移除预填内容，改为空字符串
    var arguments by remember { mutableStateOf("") }
    var inputFilePath by remember { mutableStateOf<String?>(null) }
    var inputFileName by remember { mutableStateOf("") }
    var outputFileName by remember { mutableStateOf("") } //  移除默认值
    val executionResult by viewModel.executionResult.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()
    val context = LocalContext.current

    val resultScrollState = rememberScrollState()

    //  防止重复跳转
    var hasNavigated by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            Toast.makeText(context, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show()
        }
    }

    // 文件选择器 - 复制到应用私有目录
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        try {
            Log.d(TAG, "选择文件回调: uri=$uri")

            if (uri == null) {
                Log.d(TAG, "用户取消了文件选择")
                return@rememberLauncherForActivityResult
            }

            //  重置跳转状态
            hasNavigated = false

            // 复制到应用私有目录（使用 FileUtils）
            val filePath = getPathFromUri(context, uri)
            Log.d(TAG, "获取到的文件路径: $filePath")

            if (filePath == null) {
                Toast.makeText(context, "无法复制文件到应用目录", Toast.LENGTH_SHORT).show()
                inputFileName = ""
                inputFilePath = null
                return@rememberLauncherForActivityResult
            }

            val file = File(filePath)
            Log.d(TAG, "文件是否存在: ${file.exists()}")
            Log.d(TAG, "文件大小: ${file.length()} bytes")
            Log.d(TAG, "文件可读: ${file.canRead()}")

            if (!file.exists()) {
                Toast.makeText(context, "文件不存在，请重新选择", Toast.LENGTH_SHORT).show()
                inputFileName = ""
                inputFilePath = null
                return@rememberLauncherForActivityResult
            }

            if (file.length() == 0L) {
                Toast.makeText(context, "文件为空，请重新选择", Toast.LENGTH_SHORT).show()
                inputFileName = ""
                inputFilePath = null
                return@rememberLauncherForActivityResult
            }

            // 关键：设置文件权限
            file.setReadable(true, false)
            file.setWritable(true, false)

            inputFilePath = filePath
            inputFileName = file.name

            //  根据输入文件名生成默认输出文件名（但用户可以修改）
            val baseName = file.name.substringBeforeLast(".")
            val extension = file.name.substringAfterLast(".", "")
            outputFileName = if (extension.isNotEmpty()) {
                "${baseName}_output.$extension"
            } else {
                "${baseName}_output"
            }

            Toast.makeText(
                context,
                "已选择: ${file.name} (${file.length() / 1024} KB)",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e(TAG, "文件选择处理失败", e)
            Toast.makeText(context, "选择文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            inputFileName = ""
            inputFilePath = null
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        try {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                //  Android 13+ 使用更通用的权限
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                // 对于其他文件类型，Android 13+ 仍需 MANAGE_EXTERNAL_STORAGE 或使用 SAF
                // 但 GetContent() 实际上不需要 READ_MEDIA_* 权限，因为它是通过 SAF 选择的
            } else {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            //  实际上 GetContent() 不需要存储权限，但保留以兼容旧版本
            val missingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                Log.d(TAG, "请求权限: $missingPermissions")
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "权限请求失败", e)
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                Log.d(TAG, "打开文件选择器")
                                //  使用 */* 选择所有文件类型
                                filePickerLauncher.launch("*/*")
                            } catch (e: Exception) {
                                Log.e(TAG, "打开文件选择器失败", e)
                                Toast.makeText(context, "打开文件选择器失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "选择输入文件",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (inputFileName.isNotBlank()) {
                                inputFileName
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
                        placeholder = { Text("请输入输出文件名") }, //  添加 placeholder
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        )
                    )
                }

                ArgumentsInputCard(
                    arguments = arguments,
                    onArgumentsChange = { arguments = it },
                    inputFileName = inputFileName
                )

                if (inputFileName.isNotBlank() && arguments.isNotBlank()) {
                    val fullCommand = buildPreviewCommand(arguments, inputFileName, outputFileName)
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
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (inputFilePath != null) {
                                val displayPath = inputFilePath?.take(100) ?: ""
                                val isTruncated = (inputFilePath?.length ?: 0) > 100
                                Text(
                                    text = "📁 文件路径: $displayPath${if (isTruncated) "..." else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExecuteButton(
                    onClick = {
                        try {
                            if (arguments.isNotBlank() && inputFilePath != null) {
                                val inputFile = File(inputFilePath!!)
                                if (!inputFile.exists()) {
                                    Toast.makeText(context, "输入文件不存在，请重新选择", Toast.LENGTH_SHORT).show()
                                    inputFilePath = null
                                    inputFileName = ""
                                    return@ExecuteButton
                                }

                                //  输出目录保持不变
                                val outputDir = File(context.getExternalFilesDir(null), "FFmpegOutput")
                                if (!outputDir.exists()) {
                                    outputDir.mkdirs()
                                }

                                //  如果用户没有指定输出文件名，使用默认名称
                                val finalOutputName = if (outputFileName.isBlank()) {
                                    val baseName = inputFile.name.substringBeforeLast(".")
                                    val extension = inputFile.name.substringAfterLast(".", "")
                                    if (extension.isNotEmpty()) {
                                        "${baseName}_output.$extension"
                                    } else {
                                        "${baseName}_output"
                                    }
                                } else {
                                    outputFileName
                                }

                                val outputPath = File(outputDir, finalOutputName).absolutePath

                                //  构建完整命令
                                val fullCommand = buildFullCommand(arguments, inputFilePath!!, outputPath)
                                Log.d(TAG, "执行命令: $fullCommand")

                                //  在回调中处理跳转
                                viewModel.executeCommandWithCallback(
                                    command = fullCommand,
                                    onComplete = { success, message ->
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()

                                        if (success) {
                                            if (!hasNavigated) {
                                                hasNavigated = true
                                                try {
                                                    val encodedPath = Uri.encode(outputPath)
                                                    navController.navigate("result/$encodedPath") {
                                                        launchSingleTop = true
                                                        popUpTo("command_screen") {
                                                            inclusive = false
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "跳转失败", e)
                                                    Toast.makeText(context, "跳转失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    hasNavigated = false
                                                }
                                            }
                                        }
                                    }
                                )
                            } else if (inputFilePath == null) {
                                Toast.makeText(context, "请先选择输入文件", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "请输入 FFmpeg 参数", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "执行命令失败", e)
                            Toast.makeText(context, "执行失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isExecuting = isExecuting,
                    enabled = arguments.isNotBlank() && inputFilePath != null && !isExecuting,
                    modifier = Modifier.weight(1f)
                )

                if (isExecuting) {
                    OutlinedButton(
                        onClick = {
                            try {
                                viewModel.cancelExecution()
                                Toast.makeText(context, "正在取消...", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e(TAG, "取消执行失败", e)
                            }
                        },
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

// 构建预览命令（用于显示）
private fun buildPreviewCommand(arguments: String, inputFileName: String, outputFileName: String): String {
    val cleanOutputFileName = if (outputFileName.isNotBlank()) {
        outputFileName.trim('"')
    } else {
        //  使用默认输出名
        val baseName = inputFileName.substringBeforeLast(".")
        val extension = inputFileName.substringAfterLast(".", "")
        if (extension.isNotEmpty()) {
            "${baseName}_output.$extension"
        } else {
            "${baseName}_output"
        }
    }

    return if (arguments.contains("-i")) {
        if (arguments.contains("output") || arguments.contains("out")) {
            arguments.replace(Regex("output\\.[a-zA-Z0-9]+"), cleanOutputFileName)
        } else {
            "$arguments $cleanOutputFileName"
        }
    } else {
        "-i \"$inputFileName\" $arguments $cleanOutputFileName"
    }
}

// 构建实际执行命令 - 使用文件路径
private fun buildFullCommand(arguments: String, inputFilePath: String, outputPath: String): String {
    // 使用双引号包裹路径（支持空格等特殊字符）
    val inputPath = "\"$inputFilePath\""
    val outputPathQuoted = "\"$outputPath\""

    return if (arguments.contains("-i")) {
        if (arguments.contains("output") || arguments.contains("out")) {
            arguments.replace(Regex("output\\.[a-zA-Z0-9]+"), outputPathQuoted)
        } else {
            "$arguments $outputPathQuoted"
        }
    } else {
        "-i $inputPath $arguments $outputPathQuoted"
    }
}

// 参数输入卡片
@Composable
fun ArgumentsInputCard(
    arguments: String,
    onArgumentsChange: (String) -> Unit,
    inputFileName: String,
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
            if (inputFileName.isNotBlank()) {
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
                        text = inputFileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, true)
                    )
                }
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }

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
                        arguments.isBlank() -> Text("⚠️ 请输入 FFmpeg 参数")
                        inputFileName.isBlank() -> Text("⚠️ 请先选择输入文件")
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

            SelectionContainer {
                Text(
                    text = executionResult.ifEmpty {
                        "暂无执行结果\n\n请选择输入文件并输入 FFmpeg 参数后执行"
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