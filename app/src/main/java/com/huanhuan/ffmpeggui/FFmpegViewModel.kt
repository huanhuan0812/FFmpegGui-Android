package com.huanhuan.ffmpeggui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.huanhuan.ffmpeggui.db.HistoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

import com.huanhuan.ffmpeggui.db.History

data class ConversionTask(
    val id: String,
    val inputPath: String,
    val outputPath: String,
    val type: String,
    var status: String,
    var progress: Float,
    var startTime: Long,
    var endTime: Long? = null
)

fun ConversionTask.toHistory(): History {
    return History(
        id = this.endTime ?: this.startTime,  // 使用task的时间作为主键
        key = this.id,  // 使用task的id作为key
        name = "${this.type} - ${File(this.outputPath).name}",  // 生成显示名称
        path = this.outputPath,  // 保存输出路径
        timestamp = this.endTime ?: this.startTime,  // 使用结束时间或开始时间
        createdAt = this.startTime,  // 创建时间
        size = File(this.outputPath).length().toInt()  // 文件大小
    )
}

// 添加转换函数 - 将History转换为ConversionTask（用于加载历史记录）
// 修改转换函数 - 将History转换为ConversionTask（添加空值处理）
fun History.toConversionTask(): ConversionTask {
    return ConversionTask(
        id = this.key,
        inputPath = "",  // History中没有保存inputPath，可能需要从其他地方获取或留空
        outputPath = this.path,
        type = try {
            this.name.substringBefore(" - ")  // 从name中解析type
        } catch (e: Exception) {
            "未知类型"
        },
        status = "完成",  // 默认状态为完成
        progress = 1f,  // 默认进度100%
        startTime = this.createdAt,
        endTime = this.timestamp
    )
}

// 添加处理结果事件类
sealed class ProcessingEvent {
    data class Completed(val success: Boolean, val message: String, val outputPath: String) : ProcessingEvent()
    object Cancelled : ProcessingEvent()
}

class FFmpegViewModel : ViewModel() {

    var isProcessing by mutableStateOf(false)
        private set

    var progress by mutableStateOf(0f)
        private set

    var currentCommand by mutableStateOf("")
        private set

    var logOutput by mutableStateOf("")
        private set

    val historyTasks = mutableStateListOf<ConversionTask>()

    // 使用 SharedFlow 发送处理结果
    private val _processingEvents = MutableSharedFlow<ProcessingEvent>()
    val processingEvents: SharedFlow<ProcessingEvent> = _processingEvents.asSharedFlow()

    // 当前正在执行的会话
    private var currentSession: FFmpegSession? = null

    // 当前任务是否被取消
    private var isCancelled by mutableStateOf(false)

    // 用于安全处理回调的 Job
    private var processingJob: Job? = null

    // 媒体总时长（毫秒）
    private var totalDurationMs by mutableStateOf(0L)

    // 当前处理时间（毫秒）
    private var currentTimeMs: Double by mutableStateOf(0.0)

    // 估算的总帧数
    private var estimatedTotalFrames by mutableStateOf(0)

    private var database: HistoryDatabase? = null

    init {
        // 只在 ViewModel 初始化时设置一次回调
        setupFFmpegCallbacks()
    }

    // 添加初始化方法
    fun initDatabase(context: Context) {
        try {
            Log.d("FFmpegViewModel", "开始初始化数据库")
            this.database = HistoryDatabase.getInstance(context)
            Log.d("FFmpegViewModel", "数据库实例获取成功")

            // 初始化时加载历史记录
            loadHistoryTasks()
        } catch (e: Exception) {
            Log.e("FFmpegViewModel", "数据库初始化失败", e)
            e.printStackTrace()
        }
    }

    // 从数据库加载历史记录
    // 从数据库加载历史记录
    private fun loadHistoryTasks() {
        viewModelScope.launch {
            try {
                Log.d("FFmpegViewModel", "开始加载历史记录")
                database?.historyDao()?.getAllHistories()?.collect { histories ->
                    Log.d("FFmpegViewModel", "获取到 ${histories.size} 条历史记录")
                    // 清空当前列表
                    historyTasks.clear()
                    // 将History转换为ConversionTask并添加到列表
                    historyTasks.addAll(histories.map {
                        Log.d("FFmpegViewModel", "转换历史记录: ${it.id}")
                        it.toConversionTask()
                    })
                }
            } catch (e: Exception) {
                Log.e("FFmpegViewModel", "加载历史记录失败", e)
                e.printStackTrace()
            }
        }
    }

    private fun setupFFmpegCallbacks() {
        com.arthenica.ffmpegkit.FFmpegKitConfig.enableStatisticsCallback { statistics ->
            viewModelScope.launch {
                if (isActive && !isCancelled) {
                    // 方法1：基于时间计算进度
                    val timeInMilliseconds = statistics.time
                    currentTimeMs = timeInMilliseconds

                    if (totalDurationMs > 0) {
                        // 基于总时长计算真实进度
                        progress = (timeInMilliseconds.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 0.99f)
                    } else {
                        // 方法2：基于帧数估算进度
                        val frameNumber = statistics.videoFrameNumber
                        if (estimatedTotalFrames > 0 && frameNumber > 0) {
                            progress = (frameNumber.toFloat() / estimatedTotalFrames.toFloat()).coerceIn(0f, 0.99f)
                        } else {
                            // 方法3：使用帧数百分比显示动画效果（当无法获取真实进度时）
                            if (frameNumber > 0) {
                                // 使用取模制造动画效果，但保持在合理范围内
                                progress = ((frameNumber % 100) / 100f) * 0.8f + 0.1f
                            }
                        }
                    }
                }
            }
        }

        com.arthenica.ffmpegkit.FFmpegKitConfig.enableLogCallback { log ->
            viewModelScope.launch {
                if (isActive && log != null && !isCancelled) {
                    // 从日志中解析更精确的进度信息
                    parseProgressFromLog(log.message)

                    logOutput += log.message + "\n"
                    // 限制日志长度
                    if (logOutput.length > 10000) {
                        logOutput = logOutput.takeLast(5000)
                    }
                }
            }
        }
    }

    // 从日志中解析进度信息
    private fun parseProgressFromLog(logMessage: String) {
        // 解析时间格式: time=00:01:23.45
        val timePattern = Regex("time=(\\d{2}):(\\d{2}):(\\d{2}\\.\\d{2})")
        val matchResult = timePattern.find(logMessage)

        matchResult?.let {
            try {
                val hours = it.groupValues[1].toInt()
                val minutes = it.groupValues[2].toInt()
                val seconds = it.groupValues[3].toFloat()
                val currentTimeInSeconds = hours * 3600 + minutes * 60 + seconds
                currentTimeMs = (currentTimeInSeconds * 1000).toDouble()

                if (totalDurationMs > 0) {
                    progress = (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 0.99f)
                }
            } catch (e: NumberFormatException) {
                // 忽略解析错误
            }
        }

        // 解析帧数信息: frame= 1234
        val framePattern = Regex("frame=\\s*(\\d+)")
        val frameMatch = framePattern.find(logMessage)

        frameMatch?.let {
            try {
                val frameNumber = it.groupValues[1].toInt()
                if (estimatedTotalFrames > 0 && frameNumber > 0) {
                    progress = (frameNumber.toFloat() / estimatedTotalFrames.toFloat()).coerceIn(0f, 0.99f)
                }
            } catch (e: NumberFormatException) {
                // 忽略解析错误
            }
        }

        // 解析进度百分比: 比如 "progress=35.2%"
        val percentPattern = Regex("progress=(\\d+\\.?\\d*)%")
        val percentMatch = percentPattern.find(logMessage)

        percentMatch?.let {
            try {
                val percent = it.groupValues[1].toFloat()
                progress = (percent / 100f).coerceIn(0f, 0.99f)
            } catch (e: NumberFormatException) {
                // 忽略解析错误
            }
        }
    }

    // 获取媒体信息（总时长、总帧数等）
    private suspend fun getMediaInfo(filePath: String): Pair<Long, Int> {
        return withContext(Dispatchers.IO) {
            try {
                // 使用 FFprobe 获取媒体信息
                val mediaInfoSession = FFprobeKit.getMediaInformation(filePath)
                val mediaInformation = mediaInfoSession.mediaInformation

                // 获取总时长（秒）
                val durationStr = mediaInformation?.getDuration()
                val duration = durationStr?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0L

                // 估算总帧数（基于时长和典型帧率）
                val format = mediaInformation?.getFilename()
                var totalFrames = 0

                // 尝试获取视频流信息
                val streams = mediaInformation?.streams
                streams?.firstOrNull { it.type == "video" }?.let { videoStream ->
                    //val frameRateStr = videoStream.getStringAttribute("r_frame_rate")
                    val frameRateStr = videoStream.realFrameRate
                    //val nbFramesStr = videoStream.getStringAttribute("nb_frames")

                    //totalFrames = nbFramesStr?.toIntOrNull() ?: 0

                    if (/*totalFrames == 0 &&*/ duration > 0 && frameRateStr != null) {
                        // 从帧率估算
                        val frameRateParts = frameRateStr.split('/')
                        if (frameRateParts.size == 2) {
                            val fps = frameRateParts[0].toDouble() / frameRateParts[1].toDouble()
                            totalFrames = (duration / 1000.0 * fps).toInt()
                        }
                    }
                }

                return@withContext Pair(duration, totalFrames)
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext Pair(0L, 0)
            }
        }
    }

    fun extractAudio(
        inputPath: String,
        outputPath: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        executeFFmpegCommand(
            command = "-i \"$inputPath\" -q:a 0 -map a -y \"$outputPath\"",
            taskId = UUID.randomUUID().toString(),
            type = "音频提取",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    fun convertAudio(
        inputPath: String,
        outputPath: String,
        format: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        executeFFmpegCommand(
            command = "-i \"$inputPath\" -acodec ${getAudioCodec(format)} -y \"$outputPath\"",
            taskId = UUID.randomUUID().toString(),
            type = "音频转换",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    fun convertVideo(
        inputPath: String,
        outputPath: String,
        format: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val codec = when (format.lowercase(Locale.getDefault())) {
            "mp4" -> "libx264"
            "avi" -> "mpeg4"
            "mkv" -> "libx265"
            "mov" -> "libx264"
            else -> "libx264"
        }

        executeFFmpegCommand(
            command = "-i \"$inputPath\" -c:v $codec -preset ultrafast -y \"$outputPath\"",
            taskId = UUID.randomUUID().toString(),
            type = "视频转换",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    fun convertAudio(
        inputPath: String,
        outputPath: String,
        format: String,
        bitrate: String = "192k",
        sampleRate: String = "44100",
        channels: String = "2",
        onComplete: (Boolean, String) -> Unit
    ) {
        val codec = getAudioCodec(format)

        // 构建命令列表而不是字符串，避免引号问题
        val commandList = mutableListOf(
            "-i", inputPath,
            "-y",  // 覆盖输出文件
            "-vn"  // 无视频
        )

        // 添加音频编码器
        commandList.add("-acodec")
        commandList.add(codec)

        // 添加采样率
        if (sampleRate.isNotBlank()) {
            commandList.add("-ar")
            commandList.add(sampleRate)
        }

        // 添加声道数
        if (channels.isNotBlank()) {
            commandList.add("-ac")
            commandList.add(channels)
        }

        // 添加比特率（如果适用）
        if (bitrate.isNotBlank()) {
            when (format.lowercase(Locale.getDefault())) {
                "mp3", "aac", "m4a" -> {
                    commandList.add("-b:a")
                    commandList.add(bitrate)
                }
                "ogg" -> {
                    // Opus 编码器特定参数
                    commandList.add("-b:a")
                    commandList.add(bitrate)

                    // Opus 优化参数
                    commandList.add("-compression_level")
                    commandList.add("10")  // 最高压缩质量

                    // 根据比特率选择应用场景
                    when {
                        bitrate.endsWith("k") && bitrate.substring(0, bitrate.length-1).toIntOrNull()?.let { it <= 96 } == true -> {
                            // 低比特率时使用语音优化
                            commandList.add("-application")
                            commandList.add("lowdelay")
                        }
                        else -> {
                            // 高比特率时使用音频优化
                            commandList.add("-application")
                            commandList.add("audio")
                        }
                    }

                    // 设置帧大小（20ms 是标准值）
                    commandList.add("-frame_duration")
                    commandList.add("20")
                }
                // flac 和 wav 不需要比特率设置
            }
        }

        // 添加输出路径
        commandList.add(outputPath)

        // 将命令列表转换为字符串用于显示
        val commandString = commandList.joinToString(" ") {
            if (it.contains(" ")) "\"$it\"" else it
        }

        executeFFmpegCommand(
            command = commandString,
            taskId = UUID.randomUUID().toString(),
            type = "音频转换 - ${format.uppercase(Locale.getDefault())}",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    fun convertVideo(
        inputPath: String,
        outputPath: String,
        format: String,
        videoCodec: String = "h264",
        audioCodec: String = "aac",
        quality: String = "medium",
        resolution: String = "original",
        onComplete: (Boolean, String) -> Unit
    ) {
        val vcodec = when (videoCodec.lowercase(Locale.getDefault())) {
            "h264" -> "libx264"
            "h265" -> "libx265"
            "mpeg4" -> "mpeg4"
            "vp9" -> "libvpx-vp9"
            else -> "libx264"
        }

        val acodec = when (audioCodec.lowercase(Locale.getDefault())) {
            "aac" -> "aac"
            "mp3" -> "libmp3lame"
            "copy" -> "copy"
            else -> "aac"
        }

        val preset = when (quality.lowercase(Locale.getDefault())) {
            "low" -> "veryfast"
            "medium" -> "medium"
            "high" -> "slow"
            else -> "medium"
        }

        val crf = when (quality.lowercase(Locale.getDefault())) {
            "low" -> "28"
            "medium" -> "23"
            "high" -> "18"
            else -> "23"
        }

        val scale = when (resolution.lowercase(Locale.getDefault())) {
            "1080p" -> ",scale=1920:1080"
            "720p" -> ",scale=1280:720"
            "480p" -> ",scale=854:480"
            "360p" -> ",scale=640:360"
            else -> ""
        }

        val command = buildString {
            append("-i \"$inputPath\"")

            if (vcodec == "copy" && acodec == "copy") {
                append(" -c copy")
            } else {
                append(" -c:v $vcodec")
                if (vcodec == "libx264" || vcodec == "libx265") {
                    append(" -preset $preset")
                    append(" -crf $crf")
                }

                if (scale.isNotEmpty()) {
                    append(" -vf \"format=yuv420p$scale\"")
                }

                append(" -c:a $acodec")
                if (acodec != "copy") {
                    when (quality) {
                        "low" -> append(" -b:a 96k")
                        "medium" -> append(" -b:a 128k")
                        "high" -> append(" -b:a 192k")
                    }
                }
            }

            when (format.lowercase(Locale.getDefault())) {
                "mp4" -> append(" -movflags +faststart")
                "webm" -> {
                    if (vcodec == "libvpx-vp9") {
                        append(" -cpu-used -5 -deadline realtime")
                    }
                }
            }

            append(" -y \"$outputPath\"")
        }

        executeFFmpegCommand(
            command = command,
            taskId = UUID.randomUUID().toString(),
            type = "视频转换 - ${format.uppercase(Locale.getDefault())}",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    private fun getAudioCodec(format: String): String {
        return when (format.lowercase(Locale.getDefault())) {
            "mp3" -> "libmp3lame"
            "aac" -> "aac"
            "flac" -> "flac"
            "wav" -> "pcm_s16le"
            "ogg" -> "libopus"  // 从 libvorbis 改为 libopus
            "m4a" -> "aac"
            else -> "copy"
        }
    }

    private fun executeFFmpegCommand(
        command: String,
        taskId: String,
        type: String,
        inputPath: String,
        outputPath: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        // 取消之前的处理
        cancelCurrentProcessing()

        // 重置状态
        isCancelled = false
        isProcessing = true
        progress = 0f
        currentCommand = command
        logOutput = ""
        totalDurationMs = 0L
        currentTimeMs = 0.0
        estimatedTotalFrames = 0

        val task = ConversionTask(
            id = taskId,
            inputPath = inputPath,
            outputPath = outputPath,
            type = type,
            status = "进行中",
            progress = 0f,
            startTime = System.currentTimeMillis()
        )
        historyTasks.add(0, task)

        viewModelScope.launch {
            try {
                database?.historyDao()?.insert(task.toHistory())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 在 ViewModelScope 中启动处理
        processingJob = viewModelScope.launch {
            try {
                // 先获取媒体信息
                val (duration, totalFrames) = getMediaInfo(inputPath)
                totalDurationMs = duration
                estimatedTotalFrames = totalFrames

                // 在 IO 线程执行 FFmpeg 命令
                val result = withContext(Dispatchers.IO) {
                    currentSession = FFmpegKit.execute(command)
                    currentSession
                }

                // 检查是否被取消
                if (isCancelled || !isActive) {
                    // 更新任务状态
                    val taskIndex = historyTasks.indexOfFirst { it.id == taskId }
                    if (taskIndex >= 0) {
                        historyTasks[taskIndex] = historyTasks[taskIndex].copy(
                            status = "已取消",
                            endTime = System.currentTimeMillis()
                        )

                        viewModelScope.launch {
                            try {
                                database?.historyDao()?.update(historyTasks[taskIndex].toHistory())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                    }
                    _processingEvents.emit(ProcessingEvent.Cancelled)
                    return@launch
                }

                val success = result?.returnCode?.isValueSuccess == true

                // 更新任务状态和进度
                val taskIndex = historyTasks.indexOfFirst { it.id == taskId }
                if (taskIndex >= 0) {
                    historyTasks[taskIndex] = historyTasks[taskIndex].copy(
                        status = if (success) "完成" else "失败",
                        progress = if (success) 1f else 0f,
                        endTime = System.currentTimeMillis()
                    )
                }

                viewModelScope.launch {
                    try {
                        database?.historyDao()?.update(historyTasks[taskIndex].toHistory())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 如果成功，设置进度为100%
                if (success) {
                    progress = 1f
                }

                val message = if (success) {
                    "处理成功: ${File(outputPath).name}"
                } else {
                    "处理失败: ${result?.returnCode} - ${result?.output}"
                }
                //Log-----------------------------------------------------------------------------------Log
//                if(!success){
//                    Log.d("FFmpegViewModel", "命令执行失败: $command")
//                }

                // 发送处理完成事件
                _processingEvents.emit(
                    ProcessingEvent.Completed(
                        success = success,
                        message = message,
                        outputPath = outputPath
                    )
                )

                // 调用回调
                onComplete(success, message)

            } catch (e: Exception) {
                e.printStackTrace()

                // 更新任务状态为失败
                val taskIndex = historyTasks.indexOfFirst { it.id == taskId }
                if (taskIndex >= 0) {
                    historyTasks[taskIndex] = historyTasks[taskIndex].copy(
                        status = "失败",
                        progress = 0f,
                        endTime = System.currentTimeMillis()
                    )
                }

                val errorMessage = "处理异常: ${e.message}"
                _processingEvents.emit(
                    ProcessingEvent.Completed(
                        success = false,
                        message = errorMessage,
                        outputPath = outputPath
                    )
                )
                onComplete(false, errorMessage)
            } finally {
                isProcessing = false
                currentSession = null
                processingJob = null
            }
        }
    }

    // 取消当前处理
    fun cancelCurrentProcessing() {
        if (isProcessing) {
            isCancelled = true
            currentSession?.cancel()
            processingJob?.cancel()
            currentSession = null
            isProcessing = false
        }
    }

    // 清理所有历史记录和对应的输出文件
    fun clearAllHistory() {
        // 删除所有输出文件
        historyTasks.forEach { task ->
            try {
                val outputFile = File(task.outputPath)
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            try {
                database?.historyDao()?.deleteAll()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 清空历史列表
        historyTasks.clear()
    }

    // 清理单个历史记录及其对应的输出文件
    fun clearHistoryItem(task: ConversionTask) {
        try {
            // 删除输出文件
            val outputFile = File(task.outputPath)
            if (outputFile.exists()) {
                outputFile.delete()
            }

            viewModelScope.launch {
                try {
                    database?.historyDao()?.delete(task.toHistory())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 从历史列表中移除
            historyTasks.remove(task)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 清理所有已完成的输出文件（不删除历史记录）
    fun clearCompletedOutputFiles() {
        historyTasks.forEach { task ->
            if (task.status == "完成") {
                try {
                    val outputFile = File(task.outputPath)
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun clearHistory() {

        viewModelScope.launch {
            try {
                database?.historyDao()?.deleteAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        historyTasks.clear()
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 销毁时取消所有处理
        cancelCurrentProcessing()
        // 清除回调
        com.arthenica.ffmpegkit.FFmpegKitConfig.enableStatisticsCallback(null)
        com.arthenica.ffmpegkit.FFmpegKitConfig.enableLogCallback(null)
    }

    // 在 FFmpegViewModel 类中添加
    fun executeImageConvert(
        inputPath: String,
        outputPath: String,
        command: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        executeFFmpegCommand(
            command = command,
            taskId = UUID.randomUUID().toString(),
            type = "图片转换",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    fun convertImage(
        inputPath: String,
        outputPath: String,
        format: String,
        quality: Int = 90,
        width: Int = 0,
        height: Int = 0,
        maintainAspectRatio: Boolean = true,
        compressionLevel: Int = 6,  // 用于 PNG 压缩级别 0-9
        dither: Boolean = true,      // 用于 GIF 抖动
        colors: Int = 256,           // 用于 GIF 颜色数
        lossless: Boolean = false,   // 用于 WebP 无损模式
        onComplete: (Boolean, String) -> Unit
    ) {
        val command = buildString {
            append("-i \"$inputPath\" -y")

            // 尺寸调整
            if (width > 0 || height > 0) {
                append(" -vf ")
                when {
                    width > 0 && height > 0 -> {
                        if (maintainAspectRatio) {
                            append("\"scale='if(gt(a,$width/$height),$width,-2)':'if(gt(a,$width/$height),-2,$height)'\"")
                        } else {
                            append("\"scale=$width:$height\"")
                        }
                    }
                    width > 0 -> append("\"scale=$width:-2\"")
                    true -> append("\"scale=-2:$height\"")
                }
            }

            // 格式特定参数
            when (format.lowercase(Locale.getDefault())) {
                "jpg", "jpeg" -> {
                    // JPEG: 质量 2-31 (2最好,31最差)
                    val jpegQuality = ((100 - quality) / 2).coerceIn(2, 31)
                    append(" -q:v $jpegQuality")
                    // 使用优化的Huffman编码
                    append(" -huffman optimal")
                }

                "png" -> {
                    // PNG: 压缩级别 0-9 (0=无压缩, 9=最大压缩)
                    val pngCompression = compressionLevel.coerceIn(0, 9)
                    append(" -compression_level $pngCompression")
                    // 使用预测器提高压缩率
                    append(" -pred mixed")
                }

                "webp" -> {
                    if (lossless) {
                        // 无损WebP
                        append(" -lossless 1 -quality $quality")
                        // 压缩方法 0-6 (6最慢但压缩率最高)
                        append(" -method 6")
                    } else {
                        // 有损WebP
                        append(" -quality $quality")
                        // 锐化边缘
                        append(" -sharp_yuv 1")
                    }
                }

                "bmp" -> {
                    // BMP: 无压缩选项，但可以指定色彩深度
                    append(" -pix_fmt bgr24")
                }

                "gif" -> {
                    // GIF 特定参数
                    val paletteGenCmd = if (dither) {
                        // 使用抖动的调色板
                        "palettegen=stats_mode=single"
                    } else {
                        "palettegen=stats_mode=single:max_colors=$colors"
                    }

                    // 先处理输入，然后应用调色板
                    val filterComplex = if (width > 0 || height > 0) {
                        // 如果已经有尺寸调整，需要组合滤镜
                        val scale = when {
                            width > 0 && height > 0 -> "scale=$width:$height"
                            width > 0 -> "scale=$width:-2"
                            height > 0 -> "scale=-2:$height"
                            else -> "scale=iw:ih"
                        }
                        "\"${scale}[s];[s]${paletteGenCmd}[p];[s][p]paletteuse${if (dither) "" else "=dither=none"}\""
                    } else {
                        "\"[0:v]${paletteGenCmd}[p];[0:v][p]paletteuse${if (dither) "" else "=dither=none"}\""
                    }

                    append(" -filter_complex $filterComplex")
                    append(" -map \"[out]\"")
                }

                "tiff" -> {
                    // TIFF: 压缩选项
                    append(" -compression_algo lzw")  // lzw, zip, jpeg, none
                    append(" -pix_fmt rgb24")
                }

                "ico" -> {
                    // ICO: 可以包含多个尺寸
                    if (width == 0 || height == 0) {
                        // 默认创建多个常见尺寸
                        append(" -vf \"scale=16:16,scale=32:32,scale=48:48,scale=64:64,scale=128:128,scale=256:256\"")
                    }
                }

                "heif", "heic" -> {
                    // HEIF: 高效图像格式
                    append(" -c:v libheif")
                    append(" -quality $quality")
                    if (lossless) {
                        append(" -lossless 1")
                    }
                }

                "avif" -> {
                    // AVIF: AV1图像格式
                    append(" -c:v libaom-av1")
                    append(" -crf ${63 - (quality * 63 / 100)}")  // 0-63, 0最好
                    append(" -b:v 0")
                    append(" -strict experimental")
                }

                "jp2", "j2k" -> {
                    // JPEG2000
                    append(" -c:v jpeg2000")
                    append(" -quality $quality")
                    if (lossless) {
                        append(" -pred 1")  // 无损预测
                    }
                }

                "pdf" -> {
                    // PDF 输出
                    append(" -c:v pdf")
                    append(" -pix_fmt rgb24")
                }

                "psd" -> {
                    // Photoshop 文档
                    append(" -c:v psd")
                    append(" -pix_fmt rgba")
                }

                "tga" -> {
                    // Targa
                    append(" -c:v targa")
                    append(" -pix_fmt bgra")
                }

                "pcx", "pict", "pnm", "pgm", "ppm", "pbm" -> {
                    // 其他格式使用默认编码器
                    // 这些格式通常不需要特殊参数
                }
            }

            append(" \"$outputPath\"")
        }

        executeFFmpegCommand(
            command = command,
            taskId = UUID.randomUUID().toString(),
            type = "图片转换 - ${format.uppercase(Locale.getDefault())}",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    // 添加一个简化的图片转换方法
    fun convertImageSimple(
        inputPath: String,
        outputPath: String,
        format: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        // 简单转换，使用默认参数
        convertImage(
            inputPath = inputPath,
            outputPath = outputPath,
            format = format,
            quality = 90,
            width = 0,
            height = 0,
            maintainAspectRatio = true,
            onComplete = onComplete
        )
    }

    fun convertVideoToGif(
        inputPath: String,
        outputPath: String,
        fps: Int,
        scale: Int,
        quality: Int,
        loopCount: Int,
        colors: Int,
        startTime: String?,
        duration: String?,
        useDither: Boolean,
        onComplete: (Boolean, String) -> Unit
    ) {
        val command = buildString {
            append("-i \"$inputPath\" -y")

            // 添加时间裁剪
            if (!startTime.isNullOrBlank()) {
                append(" -ss $startTime")
            }
            if (!duration.isNullOrBlank()) {
                append(" -t $duration")
            }

            // 简化版滤镜图 - 直接使用palettegen和paletteuse
            val filterComplex = if (useDither) {
                "fps=$fps,scale=$scale:-1:flags=lanczos,split[split1][split2];[split1]palettegen[pal];[split2][pal]paletteuse=dither=sierra2_4a"
            } else {
                "fps=$fps,scale=$scale:-1:flags=lanczos,split[split1][split2];[split1]palettegen=max_colors=$colors[pal];[split2][pal]paletteuse=dither=none"
            }

            append(" -filter_complex \"$filterComplex\"")

            // 设置循环次数
            if (loopCount >= 0) {
                append(" -loop $loopCount")
            }

            append(" \"$outputPath\"")
        }

        executeFFmpegCommand(
            command = command,
            taskId = UUID.randomUUID().toString(),
            type = "视频转GIF",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }
}