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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// 标签字段定义
// ============================================================
sealed class TagField(
    val key: String,
    val label: String,
    val category: String,
    val isSwitch: Boolean = false,
    val maxLines: Int = 1
) {
    // 基本标签
    object Title : TagField("title", "标题", "基本")
    object Artist : TagField("artist", "艺术家", "基本")
    object Album : TagField("album", "专辑", "基本")
    object Year : TagField("year", "年份", "基本")
    object Genre : TagField("genre", "流派", "基本")
    object Track : TagField("track", "曲目号", "基本")
    object Comment : TagField("comment", "备注", "基本", maxLines = 3)
    object Composer : TagField("composer", "作曲者", "基本")

    // 专辑信息
    object AlbumArtist : TagField("album_artist", "专辑艺术家", "专辑信息")
    object Disc : TagField("disc", "碟片号", "专辑信息")
    object DiscTotal : TagField("disctotal", "总碟片数", "专辑信息")
    object TrackTotal : TagField("tracktotal", "总曲目数", "专辑信息")
    object Media : TagField("media", "媒体类型", "专辑信息")
    object Compilation : TagField("compilation", "合辑", "专辑信息", isSwitch = true)
    object Gapless : TagField("gapless", "无缝播放", "专辑信息", isSwitch = true)

    // 音乐技术
    object Bpm : TagField("bpm", "BPM", "音乐技术")
    object Key : TagField("key", "调性", "音乐技术")
    object Tempo : TagField("tempo", "速度", "音乐技术")
    object InitialKey : TagField("initial_key", "起始调性", "音乐技术")
    object Mood : TagField("mood", "情绪/风格", "音乐技术")

    // 创作人员
    object Performer : TagField("performer", "表演者", "创作人员")
    object Lyricist : TagField("lyricist", "作词者", "创作人员")
    object Arranger : TagField("arranger", "编曲者", "创作人员")
    object Conductor : TagField("conductor", "指挥", "创作人员")
    object Orchestra : TagField("orchestra", "管弦乐团", "创作人员")
    object Ensemble : TagField("ensemble", "合奏团", "创作人员")

    // 制作信息
    object Producer : TagField("producer", "制作人", "制作信息")
    object Remixer : TagField("remixer", "混音师", "制作信息")
    object Engineer : TagField("engineer", "录音工程师", "制作信息")
    object Studio : TagField("studio", "录音室", "制作信息")
    object Location : TagField("location", "录音地点", "制作信息")

    // 版权与标识
    object Copyright : TagField("copyright", "版权信息", "版权与标识")
    object License : TagField("license", "许可证", "版权与标识")
    object Publisher : TagField("publisher", "唱片公司/出版者", "版权与标识")
    object Organization : TagField("organization", "组织/机构", "版权与标识")
    object Isrc : TagField("isrc", "ISRC 码", "版权与标识")
    object Iswc : TagField("iswc", "ISWC 码", "版权与标识")
    object CatalogNumber : TagField("catalognumber", "目录编号", "版权与标识")

    // 内容信息
    object Language : TagField("language", "语言", "内容信息")
    object Rating : TagField("rating", "评分 (1-5)", "内容信息")
    object OriginalDate : TagField("original_date", "原始发行日期", "内容信息")
    object OriginalYear : TagField("original_year", "原始发行年份", "内容信息")
    object Description : TagField("description", "描述", "内容信息", maxLines = 3)
    object Synopsis : TagField("synopsis", "概要", "内容信息", maxLines = 3)
    object Lyrics : TagField("lyrics", "歌词", "内容信息", maxLines = 5)

    // 网络与播客
    object Url : TagField("url", "URL", "网络与播客")
    object Website : TagField("website", "网站", "网络与播客")
    object Podcast : TagField("podcast", "播客", "网络与播客", isSwitch = true)
    object PodcastUrl : TagField("podcasturl", "播客 URL", "网络与播客")
    object PodcastCategory : TagField("podcastcategory", "播客分类", "网络与播客")

    companion object {
        val allFields = listOf(
            Title, Artist, Album, Year, Genre, Track, Comment, Composer,
            AlbumArtist, Disc, DiscTotal, TrackTotal, Media, Compilation, Gapless,
            Bpm, Key, Tempo, InitialKey, Mood,
            Performer, Lyricist, Arranger, Conductor, Orchestra, Ensemble,
            Producer, Remixer, Engineer, Studio, Location,
            Copyright, License, Publisher, Organization, Isrc, Iswc, CatalogNumber,
            Language, Rating, OriginalDate, OriginalYear, Description, Synopsis, Lyrics,
            Url, Website, Podcast, PodcastUrl, PodcastCategory
        )

        val categories = allFields.groupBy { it.category }
            .map { (category, fields) -> category to fields.sortedBy { it.label } }
            .sortedBy { it.first }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTagEditScreen(
    onBack: () -> Unit,
    navController: NavController,
    viewModel: FFmpegViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val scrollState = rememberScrollState()
    var isScreenActive by remember { mutableStateOf(true) }

    // 文件相关状态
    var selectedAudioPath by remember { mutableStateOf<String?>(null) }

    // ============================================================
    // 动态标签状态 - 使用 Map 存储所有标签值
    // ============================================================
    var tagValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var tagSwitches by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    // 当前已添加的标签列表
    var activeTags by remember { mutableStateOf<Set<String>>(setOf()) }

    // 添加标签下拉菜单状态
    var showAddTagMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    // 封面图片
    var coverPath by remember { mutableStateOf<String?>(null) }

    // 输出文件名
    var outputFileName by remember { mutableStateOf("") }

    // 加载状态
    var isLoadingMetadata by remember { mutableStateOf(false) }

    // ============================================================
    // 辅助函数
    // ============================================================
    fun getTagValue(key: String): String = tagValues[key] ?: ""
    fun getTagSwitch(key: String): Boolean = tagSwitches[key] ?: false

    fun updateTagValue(key: String, value: String) {
        tagValues = tagValues + (key to value)
    }

    fun updateTagSwitch(key: String, value: Boolean) {
        tagSwitches = tagSwitches + (key to value)
    }

    fun addTag(key: String) {
        activeTags = activeTags + key
        // 如果是开关类型，默认 false
        val field = TagField.allFields.find { it.key == key }
        if (field?.isSwitch == true) {
            tagSwitches = tagSwitches + (key to false)
        } else {
            tagValues = tagValues + (key to "")
        }
    }

    fun removeTag(key: String) {
        activeTags = activeTags - key
        tagValues = tagValues - key
        tagSwitches = tagSwitches - key
    }

    fun resetAllFields() {
        activeTags = emptySet()
        tagValues = emptyMap()
        tagSwitches = emptyMap()
        coverPath = null
        outputFileName = ""
    }

    // ============================================================
    // 权限请求
    // ============================================================
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            Toast.makeText(context, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // 加载元数据 - 只填充基本标签，其他由用户手动添加
    // ============================================================
    fun loadMetadata(path: String) {
        if (isLoadingMetadata) return
        isLoadingMetadata = true
        viewModel.viewModelScope.launch {
            try {
                val metadata = viewModel.getAudioMetadata(path)
                metadata?.let {
                    // 只自动填充有值的标签，并自动添加到 activeTags
                    val basicKeys = setOf("title", "artist", "album", "year", "genre", "track", "comment", "composer")
                    val newValues = mutableMapOf<String, String>()
                    val newActive = mutableSetOf<String>()

                    it.forEach { (key, value) ->
                        if (value.isNotBlank()) {
                            // 如果有值，自动添加该标签
                            newActive.add(key)
                            newValues[key] = value
                        }
                    }

                    // 如果有基本标签被填充，添加到活动标签
                    if (newActive.isNotEmpty()) {
                        activeTags = activeTags + newActive
                        tagValues = tagValues + newValues
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMetadata = false
            }
        }
    }

    // ============================================================
    // 音频文件选择器
    // ============================================================
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val path = getPathFromUri(context, it)
            selectedAudioPath = path
            resetAllFields()
            coverPath = null
            if (path != null) {
                val name = File(path).nameWithoutExtension
                outputFileName = "${name}_tagged"
                loadMetadata(path)
            }
        }
    }

    // ============================================================
    // 封面图片选择器
    // ============================================================
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coverPath = getPathFromUri(context, it)
            if (coverPath == null) {
                Toast.makeText(context, "无法获取图片路径", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================================================
    // 请求权限
    // ============================================================
    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
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

    // ============================================================
    // 监听生命周期
    // ============================================================
    DisposableEffect(lifecycleOwner) {
        onDispose {
            isScreenActive = false
        }
    }

    // ============================================================
    // 监听处理事件
    // ============================================================
    LaunchedEffect(viewModel) {
        viewModel.processingEvents.collect { event ->
            when (event) {
                is ProcessingEvent.Completed -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    if (event.success && isScreenActive) {
                        val encodedPath = Uri.encode(event.outputPath)
                        navController.navigate("result/${encodedPath}") {
                            launchSingleTop = true
                            popUpTo("audio_tag_edit") {
                                inclusive = false
                            }
                        }
                    }
                }
                ProcessingEvent.Cancelled -> {
                    Toast.makeText(context, "操作已取消", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ============================================================
    // 构建元数据 Map
    // ============================================================
    fun buildMetadataMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        activeTags.forEach { key ->
            val field = TagField.allFields.find { it.key == key }
            if (field?.isSwitch == true) {
                val value = tagSwitches[key] ?: false
                if (value) {
                    result[key] = "1"
                }
            } else {
                val value = tagValues[key] ?: ""
                if (value.isNotBlank()) {
                    result[key] = value
                }
            }
        }
        return result
    }

    fun hasMetadataChanges(): Boolean {
        return buildMetadataMap().isNotEmpty() || coverPath != null
    }

    // ============================================================
    // 获取可添加的标签列表（按分类分组）
    // ============================================================
    val availableTags = TagField.categories
        .map { (category, fields) ->
            category to fields.filter { it.key !in activeTags }
        }
        .filter { it.second.isNotEmpty() }

    // ============================================================
    // UI
    // ============================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ============================================================
        // 选择音频文件
        // ============================================================
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "选择音频文件",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择音频")
                }

                if (selectedAudioPath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = File(selectedAudioPath!!).name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2
                                )
                                Text(
                                    text = formatFileSize(File(selectedAudioPath!!).length()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                if (isLoadingMetadata) {
                                    Text(
                                        text = "加载标签信息...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============================================================
        // 标签编辑区域 - 动态显示已添加的标签
        // ============================================================
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "标签编辑",
                        style = MaterialTheme.typography.titleMedium
                    )
                    // 添加标签按钮
                    OutlinedButton(
                        onClick = { showAddTagMenu = true },
                        enabled = selectedAudioPath != null && availableTags.any { it.second.isNotEmpty() },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加标签", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 添加标签下拉菜单
                DropdownMenu(
                    expanded = showAddTagMenu,
                    onDismissRequest = { showAddTagMenu = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    availableTags.forEach { (category, fields) ->
                        if (fields.isNotEmpty()) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            fields.forEach { field ->
                                DropdownMenuItem(
                                    text = { Text(field.label) },
                                    onClick = {
                                        addTag(field.key)
                                        showAddTagMenu = false
                                    }
                                )
                            }
                            if (availableTags.lastOrNull()?.first != category) {
                                HorizontalDivider(
                                    Modifier,
                                    DividerDefaults.Thickness,
                                    DividerDefaults.color
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 显示已添加的标签
                if (activeTags.isEmpty()) {
                    Text(
                        text = "点击「添加标签」来选择要编辑的标签",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    // 按分类分组显示
                    val groupedActive = TagField.categories
                        .map { (category, fields) ->
                            category to fields.filter { it.key in activeTags }
                        }
                        .filter { it.second.isNotEmpty() }

                    groupedActive.forEachIndexed { index, (category, fields) ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.material3.HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        fields.forEach { field ->
                            TagInputRow(
                                field = field,
                                value = if (field.isSwitch) {
                                    (tagSwitches[field.key] ?: false).toString()
                                } else {
                                    tagValues[field.key] ?: ""
                                },
                                onValueChange = { newValue ->
                                    if (field.isSwitch) {
                                        updateTagSwitch(field.key, newValue.toBooleanStrictOrNull() ?: false)
                                    } else {
                                        updateTagValue(field.key, newValue)
                                    }
                                },
                                onRemove = { removeTag(field.key) },
                                isSwitch = field.isSwitch,
                                switchValue = tagSwitches[field.key] ?: false,
                                onSwitchChange = { updateTagSwitch(field.key, it) },
                                maxLines = field.maxLines
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============================================================
        // 封面图片
        // ============================================================
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "专辑封面",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { coverPickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedAudioPath != null
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (coverPath != null) "更换封面" else "选择封面图片")
                }
                if (coverPath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = File(coverPath!!).name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { coverPath = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "移除封面", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============================================================
        // 输出设置
        // ============================================================
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "输出设置",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = outputFileName,
                    onValueChange = { outputFileName = it },
                    label = { Text("输出文件名（不含扩展名）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = selectedAudioPath != null
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ============================================================
        // 处理状态
        // ============================================================
        if (viewModel.isProcessing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "正在处理...",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${(viewModel.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { viewModel.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    if (viewModel.currentCommand.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = viewModel.currentCommand,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ============================================================
        // 保存按钮
        // ============================================================
        Button(
            onClick = {
                if (selectedAudioPath == null) {
                    Toast.makeText(context, "请选择音频文件", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (outputFileName.isBlank()) {
                    Toast.makeText(context, "请输入输出文件名", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val metadata = buildMetadataMap()
                if (metadata.isEmpty() && coverPath == null) {
                    Toast.makeText(context, "没有要修改的标签或封面", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val outputDir = File(context.getExternalFilesDir(null), "FFmpegOutput/TaggedAudio")
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }

                val inputFile = File(selectedAudioPath!!)
                val extension = inputFile.extension
                val outputFile = File(outputDir, "${outputFileName}_${timestamp}.${extension}")

                val message = buildString {
                    append("保存标签")
                    if (coverPath != null) append(" + 封面")
                    if (metadata.isNotEmpty()) append(" (${metadata.size} 个标签)")
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                viewModel.editAudioTags(
                    inputPath = selectedAudioPath!!,
                    outputPath = outputFile.absolutePath,
                    metadata = metadata,
                    coverPath = coverPath,
                    onComplete = { _, _ -> }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !viewModel.isProcessing && selectedAudioPath != null
        ) {
            if (viewModel.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("处理中...")
            } else {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存标签")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (viewModel.isProcessing) {
            OutlinedButton(
                onClick = {
                    viewModel.cancelCurrentProcessing()
                    Toast.makeText(context, "正在取消...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ============================================================
// 标签输入行组件
// ============================================================
@Composable
fun TagInputRow(
    field: TagField,
    value: String,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    isSwitch: Boolean = false,
    switchValue: Boolean = false,
    onSwitchChange: (Boolean) -> Unit = {},
    maxLines: Int = 1
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSwitch) {
            // 开关类型
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = field.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = switchValue,
                    onCheckedChange = onSwitchChange
                )
            }
        } else {
            // 文本输入类型
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                modifier = Modifier.weight(1f),
                singleLine = maxLines == 1,
                maxLines = maxLines,
                textStyle = if (maxLines > 1) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "移除标签",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}