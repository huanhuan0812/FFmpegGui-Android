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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
fun ImageConvertScreen(
    onBack: () -> Unit,
    navController: NavController,
    viewModel: FFmpegViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var selectedImagePath by remember { mutableStateOf<String?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    var selectedOutputFormat by remember { mutableStateOf("jpg") }
    var quality by remember { mutableFloatStateOf(90f) }
    var resizeWidth by remember { mutableIntStateOf(0) }
    var resizeHeight by remember { mutableIntStateOf(0) }
    var maintainAspectRatio by remember { mutableStateOf(true) }

    val imageFormats = listOf("jpg", "png", "webp", "bmp", "gif", "tiff")
    val scrollState = rememberScrollState()

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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val path = getPathFromUri(context, it)
            selectedImagePath = path
            if (outputFileName.isEmpty()) {
                val name = File(path).nameWithoutExtension
                outputFileName = "${name}_converted"
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
                title = { Text("图片格式转换") },
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
                .verticalScroll(scrollState)
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
                        text = "选择图片文件",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择图片")
                    }

                    if (selectedImagePath != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = File(selectedImagePath!!).name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2
                        )

                        // 显示图片信息
                        val file = File(selectedImagePath!!)
                        val fileSize = file.length() / 1024 // KB
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "大小: ${fileSize} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        text = "输出格式",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // 格式选择
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        imageFormats.forEach { format ->
                            FilterChip(
                                selected = selectedOutputFormat == format,
                                onClick = { selectedOutputFormat = format },
                                label = { Text(format.uppercase()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 质量设置（仅对有损格式）
                    if (selectedOutputFormat in listOf("jpg", "webp")) {
                        Text(
                            text = "图片质量: ${quality.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = quality,
                            onValueChange = { quality = it },
                            valueRange = 1f..100f,
                            steps = 98,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 尺寸调整选项
                    Text(
                        text = "尺寸调整",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = if (resizeWidth == 0) "" else resizeWidth.toString(),
                            onValueChange = {
                                resizeWidth = it.toIntOrNull() ?: 0
                            },
                            label = { Text("宽度 (px)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = if (resizeHeight == 0) "" else resizeHeight.toString(),
                            onValueChange = {
                                resizeHeight = it.toIntOrNull() ?: 0
                            },
                            label = { Text("高度 (px)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // 保持宽高比选项
                    FilterChip(
                        selected = maintainAspectRatio,
                        onClick = { maintainAspectRatio = !maintainAspectRatio },
                        label = { Text("保持宽高比") },
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Text(
                        text = "（留空表示保持原始尺寸）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
                        LinearProgressIndicator(
                            progress = { viewModel.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = ProgressIndicatorDefaults.linearColor,
                            trackColor = ProgressIndicatorDefaults.linearTrackColor,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "正在处理: ${(viewModel.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 开始转换按钮
            Button(
                onClick = {
                    if (selectedImagePath == null) {
                        Toast.makeText(context, "请选择图片文件", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (outputFileName.isBlank()) {
                        Toast.makeText(context, "请输入输出文件名", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val outputDir = File(context.getExternalFilesDir(null), "FFmpegOutput/Images")
                    if (!outputDir.exists()) {
                        outputDir.mkdirs()
                    }

                    val outputFile = File(outputDir, "${outputFileName}_${timestamp}.${selectedOutputFormat}")

                    // 构建FFmpeg命令
                    val command = buildImageConvertCommand(
                        inputPath = selectedImagePath!!,
                        outputPath = outputFile.absolutePath,
                        format = selectedOutputFormat,
                        quality = quality,
                        width = resizeWidth,
                        height = resizeHeight,
                        maintainAspectRatio = maintainAspectRatio
                    )

                    viewModel.executeImageConvert(
                        inputPath = selectedImagePath!!,
                        outputPath = outputFile.absolutePath,
                        command = command,
                        onComplete = { success, message ->
                            // 检查屏幕是否仍然活跃
                            if (!isScreenActive) {
                                if (success) {
                                    Toast.makeText(context, "图片转换完成: ${outputFile.name}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                                return@executeImageConvert
                            }

                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            if (success) {
                                navController.navigate("result/${Uri.encode(outputFile.absolutePath)}") {
                                    launchSingleTop = true
                                    popUpTo("image_convert") {
                                        inclusive = false
                                    }
                                }
                            }
                        }
                    )
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
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始转换")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// 构建图片转换命令
private fun buildImageConvertCommand(
    inputPath: String,
    outputPath: String,
    format: String,
    quality: Float,
    width: Int,
    height: Int,
    maintainAspectRatio: Boolean
): String {
    val commandList = mutableListOf(
        "-i", inputPath,
        "-y"  // 覆盖输出文件
    )

    // 添加尺寸调整参数
    if (width > 0 || height > 0) {
        val scaleFilter = buildString {
            append("scale=")
            when {
                width > 0 && height > 0 -> {
                    if (maintainAspectRatio) {
                        append("'if(gt(a,$width/$height),$width,-2)':'if(gt(a,$width/$height),-2,$height)'")
                    } else {
                        append("$width:$height")
                    }
                }
                width > 0 -> append("$width:-2")
                height > 0 -> append("-2:$height")
            }
        }
        commandList.add("-vf")
        commandList.add(scaleFilter)
    }

    // 添加质量参数（对于有损格式）
    when (format.lowercase(Locale.getDefault())) {
        "jpg", "jpeg" -> {
            commandList.add("-q:v")
            commandList.add(((100 - quality) / 2).toInt().toString()) // q:v 范围 2-31，2最好，31最差
        }
        "webp" -> {
            commandList.add("-quality")
            commandList.add(quality.toInt().toString())
        }
        "png" -> {
            if (quality < 100) {
                commandList.add("-compression_level")
                commandList.add(((9 * (100 - quality) / 100)).toInt().toString()) // 0-9，9最高压缩
            }
        }
    }

    // 添加输出路径
    commandList.add(outputPath)

    // 将命令列表转换为字符串
    return commandList.joinToString(" ") {
        if (it.contains(" ") && !it.startsWith("'") && !it.endsWith("'")) "\"$it\"" else it
    }
}