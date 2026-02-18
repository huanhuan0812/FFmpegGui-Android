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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
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

    // 新增高级选项状态
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var compressionLevel by remember { mutableFloatStateOf(6f) }  // PNG压缩
    var losslessWebP by remember { mutableStateOf(false) }        // WebP无损
    var gifDither by remember { mutableStateOf(true) }            // GIF抖动
    var gifColors by remember { mutableFloatStateOf(256f) }       // GIF颜色数
    var selectedCategory by remember { mutableStateOf("常用") }    // 格式分类

    // 扩展图片格式列表，按分类组织
    val imageFormatCategories = mapOf(
        "常用" to listOf("jpg", "png", "webp", "gif"),
        "专业" to listOf("tiff", "bmp", "tga", "pcx"),
        "现代" to listOf("avif", "heif", "jp2", "webp"),
        "其他" to listOf("ico", "psd", "pdf", "pict", "pnm", "dds")
    )

    val scrollState = rememberScrollState()

    // 添加一个状态来跟踪屏幕是否仍然活动
    var isScreenActive by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            isScreenActive = false
        }
    }

    // 权限处理代码保持不变...
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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
                title = { Text("图片格式转换") }
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
            // 文件选择卡片（保持不变）
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

                        val file = File(selectedImagePath!!)
                        val fileSize = file.length() / 1024
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "大小: ${fileSize} KB | 格式: ${file.extension.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 格式分类标签
                    Text(
                        text = "格式分类",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        imageFormatCategories.keys.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "输出格式",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // 根据选中的分类显示格式
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        imageFormatCategories[selectedCategory]?.forEach { format ->
                            FilterChip(
                                selected = selectedOutputFormat == format,
                                onClick = { selectedOutputFormat = format },
                                label = { Text(format.uppercase()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 基本质量设置
                    when (selectedOutputFormat) {
                        "jpg", "jpeg", "webp", "avif", "heif", "jp2" -> {
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
                    }

                    // 尺寸调整选项（保持不变）
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // 高级选项切换
                    Button(
                        onClick = { showAdvancedOptions = !showAdvancedOptions },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showAdvancedOptions) "隐藏高级选项" else "显示高级选项")
                    }

                    // 高级选项面板
                    if (showAdvancedOptions) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // PNG 压缩设置
                        if (selectedOutputFormat == "png") {
                            Text(
                                text = "PNG 压缩级别: ${compressionLevel.toInt()} (0=无压缩, 9=最大压缩)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = compressionLevel,
                                onValueChange = { compressionLevel = it },
                                valueRange = 0f..9f,
                                steps = 9
                            )
                        }

                        // WebP 无损选项
                        if (selectedOutputFormat == "webp") {
                            FilterChip(
                                selected = losslessWebP,
                                onClick = { losslessWebP = !losslessWebP },
                                label = { Text("无损模式") }
                            )
                        }

                        // GIF 高级选项
                        if (selectedOutputFormat == "gif") {
                            FilterChip(
                                selected = gifDither,
                                onClick = { gifDither = !gifDither },
                                label = { Text("使用抖动") }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "颜色数: ${gifColors.toInt()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = gifColors,
                                onValueChange = { gifColors = it },
                                valueRange = 2f..256f,
                                steps = 254
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 处理状态显示（保持不变）
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

                    viewModel.convertImage(
                        inputPath = selectedImagePath!!,
                        outputPath = outputFile.absolutePath,
                        format = selectedOutputFormat,
                        quality = quality.toInt(),
                        width = resizeWidth,
                        height = resizeHeight,
                        maintainAspectRatio = maintainAspectRatio,
                        compressionLevel = compressionLevel.toInt(),
                        dither = gifDither,
                        colors = gifColors.toInt(),
                        lossless = losslessWebP,
                        onComplete = { success, message ->
                            if (!isScreenActive) {
                                if (success) {
                                    Toast.makeText(context, "图片转换完成: ${outputFile.name}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                                return@convertImage
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

            // 格式说明
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "格式说明",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (selectedOutputFormat) {
                            "jpg" -> "JPEG: 有损压缩，适合照片，文件小"
                            "png" -> "PNG: 无损压缩，支持透明，适合图标和截图"
                            "webp" -> "WebP: 现代格式，支持有损/无损，文件更小"
                            "gif" -> "GIF: 支持动画，颜色有限，适合简单动画"
                            "bmp" -> "BMP: 无压缩，文件大，Windows位图"
                            "tiff" -> "TIFF: 灵活格式，支持多种压缩，适合打印"
                            "avif" -> "AVIF: AV1图像格式，压缩率极高"
                            "heif" -> "HEIF: 高效图像格式，iPhone常用"
                            "jp2" -> "JPEG2000: 支持无损/有损，渐进传输"
                            "ico" -> "ICO: Windows图标格式"
                            "psd" -> "PSD: Photoshop源文件格式"
                            "pdf" -> "PDF: 便携文档格式"
                            else -> "点击查看格式说明"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}