package com.huanhuan.ffmpeggui

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.huanhuan.ffmpeggui.db.History
import com.huanhuan.ffmpeggui.db.HistoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

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

// ============================================================
// 修改：toHistory() - 添加 inputPath 和 endTime 字段
// ============================================================
fun ConversionTask.toHistory(): History {
    return History(
        id = this.endTime ?: this.startTime,
        key = this.id,
        name = "${this.type} - ${File(this.outputPath).name}",
        path = this.outputPath,
        inputPath = this.inputPath,  // 新增：保存输入路径
        timestamp = this.endTime ?: this.startTime,
        createdAt = this.startTime,
        endTime = this.endTime ?: 0L,  // 新增：保存结束时间
        size = try {
            File(this.outputPath).length().toInt()
        } catch ( _ : Exception) {
            0
        }
    )
}

// ============================================================
// 修改：toConversionTask() - 读取 inputPath 和 endTime 字段
// ============================================================
fun History.toConversionTask(): ConversionTask {
    return ConversionTask(
        id = this.key,
        inputPath = this.inputPath,  // 新增：读取输入路径
        outputPath = this.path,
        type = try {
            this.name.substringBefore(" - ")
        } catch (_: Exception) {
            "未知类型"
        },
        status = "完成",
        progress = 1f,
        startTime = this.createdAt,
        endTime = if (this.endTime > 0) this.endTime else null  // 新增：读取结束时间
    )
}

sealed class ProcessingEvent {
    data class Completed(val success: Boolean, val message: String, val outputPath: String) : ProcessingEvent()
    object Cancelled : ProcessingEvent()
}

class FFmpegViewModel : ViewModel() {

    var isProcessing by mutableStateOf(false)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var currentCommand by mutableStateOf("")
        private set

    var logOutput by mutableStateOf("")
        private set

    val historyTasks = mutableStateListOf<ConversionTask>()

    private val _processingEvents = MutableSharedFlow<ProcessingEvent>()
    val processingEvents: SharedFlow<ProcessingEvent> = _processingEvents.asSharedFlow()

    private var currentSession: FFmpegSession? = null
    private var isCancelled by mutableStateOf(false)
    private var processingJob: Job? = null
    private var totalDurationMs by mutableLongStateOf(0L)
    private var currentTimeMs: Double by mutableDoubleStateOf(0.0)
    private var estimatedTotalFrames by mutableIntStateOf(0)

    private var database: HistoryDatabase? = null
    private var appContext: Context? = null

    // 执行命令相关的状态
    private val _executionResult = MutableStateFlow("")
    val executionResult: StateFlow<String> = _executionResult.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    init {
        setupFFmpegCallbacks()
    }

    fun initDatabase(context: Context) {
        try {
            Log.d("FFmpegViewModel", "开始初始化数据库")
            this.appContext = context.applicationContext
            this.database = HistoryDatabase.getInstance(context)
            Log.d("FFmpegViewModel", "数据库实例获取成功")
            loadHistoryTasks()
        } catch (e: Exception) {
            Log.e("FFmpegViewModel", "数据库初始化失败", e)
            e.printStackTrace()
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            try {
                Log.d("FFmpegViewModel", "手动刷新历史记录")
                database?.historyDao()?.getAllHistories()?.collect { histories ->
                    Log.d("FFmpegViewModel", "刷新获取到 ${histories.size} 条历史记录")
                    historyTasks.clear()
                    historyTasks.addAll(histories.map { it.toConversionTask() })
                }
            } catch (e: Exception) {
                Log.e("FFmpegViewModel", "刷新历史记录失败", e)
                e.printStackTrace()
            }
        }
    }

    private fun loadHistoryTasks() {
        viewModelScope.launch {
            try {
                Log.d("FFmpegViewModel", "开始加载历史记录")
                database?.historyDao()?.getAllHistories()?.collect { histories ->
                    Log.d("FFmpegViewModel", "获取到 ${histories.size} 条历史记录")
                    historyTasks.clear()
                    historyTasks.addAll(histories.map { it.toConversionTask() })
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
                    val timeInMilliseconds = statistics.time
                    currentTimeMs = timeInMilliseconds

                    if (totalDurationMs > 0) {
                        progress =
                            (timeInMilliseconds.toFloat() / totalDurationMs.toFloat()).coerceIn(
                                0f,
                                0.99f
                            )
                    } else {
                        val frameNumber = statistics.videoFrameNumber
                        if (estimatedTotalFrames > 0 && frameNumber > 0) {
                            progress =
                                (frameNumber.toFloat() / estimatedTotalFrames.toFloat()).coerceIn(
                                    0f,
                                    0.99f
                                )
                        } else {
                            if (frameNumber > 0) {
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
                    parseProgressFromLog(log.message)
                    logOutput += log.message + "\n"
                    if (logOutput.length > 10000) {
                        logOutput = logOutput.takeLast(5000)
                    }
                }
            }
        }
    }

    private fun parseProgressFromLog(logMessage: String) {
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
                    progress =
                        (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 0.99f)
                }
            } catch (_: NumberFormatException) {
                // 忽略
            }
        }

        val framePattern = Regex("frame=\\s*(\\d+)")
        val frameMatch = framePattern.find(logMessage)

        frameMatch?.let {
            try {
                val frameNumber = it.groupValues[1].toInt()
                if (estimatedTotalFrames > 0 && frameNumber > 0) {
                    progress =
                        (frameNumber.toFloat() / estimatedTotalFrames.toFloat()).coerceIn(0f, 0.99f)
                }
            } catch (_: NumberFormatException) {
                // 忽略
            }
        }

        val percentPattern = Regex("progress=(\\d+\\.?\\d*)%")
        val percentMatch = percentPattern.find(logMessage)

        percentMatch?.let {
            try {
                val percent = it.groupValues[1].toFloat()
                progress = (percent / 100f).coerceIn(0f, 0.99f)
            } catch (_: NumberFormatException) {
                // 忽略
            }
        }
    }

    private suspend fun getMediaInfo(filePath: String): Pair<Long, Int> {
        return withContext(Dispatchers.IO) {
            try {
                val mediaInfoSession = FFprobeKit.getMediaInformation(filePath)
                val mediaInformation = mediaInfoSession.mediaInformation
                val durationStr = mediaInformation?.duration
                val duration = durationStr?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0L

                var totalFrames = 0
                val streams = mediaInformation?.streams
                streams?.firstOrNull { it.type == "video" }?.let { videoStream ->
                    val frameRateStr = videoStream.realFrameRate
                    if (duration > 0 && frameRateStr != null) {
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

    // ============================================================
    // 文件操作方法（使用 FileUtils 兼容函数）
    // ============================================================

    private fun getContext(): Context? = appContext

    // ============================================================
    // 公开的文件操作方法（使用统一的 FileUtils 工具）
    // ============================================================

    fun clearHistoryItem(task: ConversionTask) {
        try {
            val context = getContext()
            if (context != null) {
                val deleted = deleteFileCompat(context, task.outputPath)
                Log.d("FFmpegViewModel", "删除文件: ${task.outputPath}, 结果: $deleted")
            } else {
                Log.e("FFmpegViewModel", "Context 为空，无法删除文件")
            }

            viewModelScope.launch {
                try {
                    database?.historyDao()?.delete(task.toHistory())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            historyTasks.remove(task)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAllHistory() {
        val context = getContext()
        if (context != null) {
            historyTasks.forEach { task ->
                try {
                    deleteFileCompat(context, task.outputPath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            Log.e("FFmpegViewModel", "Context 为空，无法删除文件")
        }

        viewModelScope.launch {
            try {
                database?.historyDao()?.deleteAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        historyTasks.clear()
    }

    fun clearCompletedOutputFiles() {
        val context = getContext()
        if (context != null) {
            historyTasks.forEach { task ->
                if (task.status == "完成") {
                    try {
                        deleteFileCompat(context, task.outputPath)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            Log.e("FFmpegViewModel", "Context 为空，无法删除文件")
        }
    }

    // ============================================================
    // 原有方法
    // ============================================================

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
        bitrate: String = "192k",
        sampleRate: String = "44100",
        channels: String = "2",
        onComplete: (Boolean, String) -> Unit
    ) {
        val codec = getAudioCodec(format)
        val commandList = mutableListOf(
            "-i", inputPath,
            "-y",
            "-vn"
        )

        commandList.add("-acodec")
        commandList.add(codec)

        if (sampleRate.isNotBlank()) {
            commandList.add("-ar")
            commandList.add(sampleRate)
        }

        if (channels.isNotBlank()) {
            commandList.add("-ac")
            commandList.add(channels)
        }

        if (bitrate.isNotBlank()) {
            when (format.lowercase(Locale.getDefault())) {
                "mp3", "aac", "m4a" -> {
                    commandList.add("-b:a")
                    commandList.add(bitrate)
                }

                "ogg" -> {
                    commandList.add("-b:a")
                    commandList.add(bitrate)
                    commandList.add("-compression_level")
                    commandList.add("10")
                    when {
                        bitrate.endsWith("k") && bitrate.substring(0, bitrate.length - 1)
                            .toIntOrNull()
                            ?.let { it <= 96 } == true -> {
                            commandList.add("-application")
                            commandList.add("lowdelay")
                        }

                        else -> {
                            commandList.add("-application")
                            commandList.add("audio")
                        }
                    }
                    commandList.add("-frame_duration")
                    commandList.add("20")
                }
            }
        }

        commandList.add(outputPath)

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

    fun convertImage(
        inputPath: String,
        outputPath: String,
        format: String,
        quality: Int = 90,
        width: Int = 0,
        height: Int = 0,
        maintainAspectRatio: Boolean = true,
        compressionLevel: Int = 6,
        dither: Boolean = true,
        colors: Int = 256,
        lossless: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) {
        val command = buildString {
            append("-i \"$inputPath\" -y")

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
                    else -> append("\"scale=-2:$height\"")
                }
            }

            when (format.lowercase(Locale.getDefault())) {
                "jpg", "jpeg" -> {
                    val jpegQuality = ((100 - quality) / 2).coerceIn(2, 31)
                    append(" -q:v $jpegQuality")
                    append(" -huffman optimal")
                }

                "png" -> {
                    val pngCompression = compressionLevel.coerceIn(0, 9)
                    append(" -compression_level $pngCompression")
                    append(" -pred mixed")
                }

                "webp" -> {
                    if (lossless) {
                        append(" -lossless 1 -quality $quality")
                        append(" -method 6")
                    } else {
                        append(" -quality $quality")
                        append(" -sharp_yuv 1")
                    }
                }

                "bmp" -> {
                    append(" -pix_fmt bgr24")
                }

                "gif" -> {
                    val paletteGenCmd = if (dither) {
                        "palettegen=stats_mode=single"
                    } else {
                        "palettegen=stats_mode=single:max_colors=$colors"
                    }

                    val filterComplex = if (width > 0 || height > 0) {
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
                    append(" -compression_algo lzw")
                    append(" -pix_fmt rgb24")
                }

                "ico" -> {
                    if (width == 0 || height == 0) {
                        append(" -vf \"scale=16:16,scale=32:32,scale=48:48,scale=64:64,scale=128:128,scale=256:256\"")
                    }
                }

                "heif", "heic" -> {
                    append(" -c:v libheif")
                    append(" -quality $quality")
                    if (lossless) {
                        append(" -lossless 1")
                    }
                }

                "avif" -> {
                    append(" -c:v libaom-av1")
                    append(" -crf ${63 - (quality * 63 / 100)}")
                    append(" -b:v 0")
                    append(" -strict experimental")
                }

                "jp2", "j2k" -> {
                    append(" -c:v jpeg2000")
                    append(" -quality $quality")
                    if (lossless) {
                        append(" -pred 1")
                    }
                }

                "pdf" -> {
                    append(" -c:v pdf")
                    append(" -pix_fmt rgb24")
                }

                "psd" -> {
                    append(" -c:v psd")
                    append(" -pix_fmt rgba")
                }

                "tga" -> {
                    append(" -c:v targa")
                    append(" -pix_fmt bgra")
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

            if (!startTime.isNullOrBlank()) {
                append(" -ss $startTime")
            }
            if (!duration.isNullOrBlank()) {
                append(" -t $duration")
            }

            val filterComplex = if (useDither) {
                "fps=$fps,scale=$scale:-1:flags=lanczos,split[split1][split2];[split1]palettegen[pal];[split2][pal]paletteuse=dither=sierra2_4a"
            } else {
                "fps=$fps,scale=$scale:-1:flags=lanczos,split[split1][split2];[split1]palettegen=max_colors=$colors[pal];[split2][pal]paletteuse=dither=none"
            }

            append(" -filter_complex \"$filterComplex\"")

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

    private fun getAudioCodec(format: String): String {
        return when (format.lowercase(Locale.getDefault())) {
            "mp3" -> "libmp3lame"
            "aac" -> "aac"
            "flac" -> "flac"
            "wav" -> "pcm_s16le"
            "ogg" -> "libopus"
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
        cancelCurrentProcessing()

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

        processingJob = viewModelScope.launch {
            try {
                val (duration, totalFrames) = getMediaInfo(inputPath)
                totalDurationMs = duration
                estimatedTotalFrames = totalFrames

                val result = withContext(Dispatchers.IO) {
                    currentSession = FFmpegKit.execute(command)
                    currentSession
                }

                if (isCancelled || !isActive) {
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

                if (success) {
                    progress = 1f
                }

                val message = if (success) {
                    "处理成功: ${File(outputPath).name}"
                } else {
                    "处理失败: ${result?.returnCode} - ${result?.output}"
                }

                _processingEvents.emit(
                    ProcessingEvent.Completed(
                        success = success,
                        message = message,
                        outputPath = outputPath
                    )
                )

                onComplete(success, message)

            } catch (e: Exception) {
                e.printStackTrace()

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

    fun cancelCurrentProcessing() {
        if (isProcessing) {
            isCancelled = true
            currentSession?.cancel()
            processingJob?.cancel()
            currentSession = null
            isProcessing = false
        }
    }

    // ============================================================
    // 执行命令相关方法
    // ============================================================

    fun executeCommandWithCallback(
        command: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isExecuting.value = true
            _executionResult.value = "正在执行命令: $command\n\n"

            try {
                val logCallback = LogCallback { log ->
                    viewModelScope.launch(Dispatchers.Main) {
                        val message = log.message ?: ""
                        if (message.isNotBlank()) {
                            _executionResult.update { currentResult ->
                                currentResult + message
                            }
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    currentSession = FFmpegKit.executeAsync(
                        command,
                        { session ->
                            viewModelScope.launch(Dispatchers.Main) {
                                val success = handleSessionResultWithCallback(session)
                                val message = if (success) {
                                    "✅ 命令执行成功"
                                } else {
                                    "❌ 命令执行失败"
                                }
                                onComplete(success, message)
                            }
                        },
                        logCallback,
                        null
                    )
                }

            } catch (e: Exception) {
                val errorMessage = "❌ 执行异常: ${e.message}"
                _executionResult.update { it + "\n$errorMessage" }
                _isExecuting.value = false
                onComplete(false, errorMessage)
            }
        }
    }

    private fun handleSessionResultWithCallback(session: FFmpegSession): Boolean {
        val returnCode = session.returnCode
        val output = StringBuilder()

        var isSuccess = false
        when {
            ReturnCode.isSuccess(returnCode) -> {
                output.append("\n✅ 命令执行成功\n")
                isSuccess = true
            }

            ReturnCode.isCancel(returnCode) -> {
                output.append("\n⚠️ 命令已被取消\n")
            }

            else -> {
                output.append("\n❌ 命令执行失败\n")
            }
        }

        val outputLog = session.output
        if (!outputLog.isNullOrEmpty() && !_executionResult.value.contains(outputLog)) {
            output.append("\n📋 完整输出:\n")
            output.append(outputLog)
        }

        val failStackTrace = session.failStackTrace
        if (failStackTrace != null) {
            output.append("\n📊 错误堆栈:\n")
            output.append(failStackTrace)
        }

        _executionResult.update { it + output.toString() }
        _isExecuting.value = false
        currentSession = null

        return isSuccess
    }

    fun clearResult() {
        _executionResult.value = ""
    }

    fun cancelExecution() {
        currentSession?.cancel()
        _isExecuting.value = false
    }

    override fun onCleared() {
        super.onCleared()
        cancelCurrentProcessing()
        com.arthenica.ffmpegkit.FFmpegKitConfig.enableStatisticsCallback(null)
        com.arthenica.ffmpegkit.FFmpegKitConfig.enableLogCallback(null)
    }

    // ============================================================
// 音频信息相关
// ============================================================

    data class AudioInfo(
        val filePath: String,
        val fileName: String,
        val fileSize: String,
        val duration: String,
        val durationSeconds: Double,
        val bitRate: String,
        val sampleRate: String,
        val channels: String,
        val codec: String,
        val codecLongName: String,
        val bitDepth: String? = null,
        val streamCount: Int = 0,
        val isVideoPresent: Boolean = false,
        val formatName: String = ""
    )

    suspend fun getAudioInfo(filePath: String): AudioInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Log.e("FFmpegViewModel", "文件不存在: $filePath")
                    return@withContext null
                }

                val mediaInfoSession = FFprobeKit.getMediaInformation(filePath)
                val mediaInformation = mediaInfoSession.mediaInformation

                if (mediaInformation == null) {
                    Log.e("FFmpegViewModel", "无法获取媒体信息: ${mediaInfoSession.failStackTrace}")
                    return@withContext null
                }

                // 获取音频流
                val streams = mediaInformation.streams
                val audioStream = streams?.firstOrNull { it.type == "audio" }

                if (audioStream == null) {
                    Log.e("FFmpegViewModel", "未找到音频流")
                    return@withContext null
                }

                // 检查是否有视频流
                val hasVideo = streams.any { it.type == "video" }

                // 解析时长
                val durationStr = mediaInformation.duration ?: "0"
                val durationSeconds = durationStr.toDoubleOrNull() ?: 0.0
                val duration = formatDuration(durationSeconds)

                // 解析文件大小
                val fileSizeBytes = file.length()
                val fileSize = formatFileSize(fileSizeBytes)

                // 解析比特率
                val bitRate = audioStream.bitrate ?: mediaInformation.bitrate
                val bitRateFormatted = bitRate?.toLongOrNull()?.let {
                    if (it > 0) "${it / 1000} kbps" else "未知"
                } ?: "未知"

                // 解析采样率
                val sampleRate = audioStream.sampleRate
                val sampleRateFormatted = sampleRate?.toIntOrNull()?.let {
                    if (it >= 1000) "${it / 1000} kHz" else "${it} Hz"
                } ?: "未知"

                // ============================================================
                // 修复：声道解析
                // ============================================================
                val channelLayout = audioStream.channelLayout
                val channelsFormatted = when {
                    channelLayout.isNullOrBlank() -> "未知"
                    channelLayout.equals(
                        "mono",
                        ignoreCase = true
                    ) || channelLayout == "1" -> "单声道 (1)"

                    channelLayout.equals(
                        "stereo",
                        ignoreCase = true
                    ) || channelLayout == "2" -> "立体声 (2)"

                    channelLayout.equals(
                        "5.1",
                        ignoreCase = true
                    ) || channelLayout == "6" -> "5.1 声道 (6)"

                    channelLayout.equals(
                        "7.1",
                        ignoreCase = true
                    ) || channelLayout == "8" -> "7.1 声道 (8)"

                    else -> {
                        val numberMatch = Regex("\\d+").find(channelLayout)
                        if (numberMatch != null) {
                            val num = numberMatch.value.toIntOrNull()
                            when (num) {
                                1 -> "单声道 (1)"
                                2 -> "立体声 (2)"
                                6 -> "5.1 声道 (6)"
                                8 -> "7.1 声道 (8)"
                                else -> "${num} 声道"
                            }
                        } else {
                            channelLayout
                        }
                    }
                }

                // ============================================================
                // 修复：编码器解析
                // ============================================================
                val codec = audioStream.codec ?: audioStream.codec ?: "未知"
                val codecLongName = audioStream.codecLong ?: audioStream.codecLong ?: ""

                //Log.d("FFmpegViewModel", "解析音频信息: codec=$codec, codecLongName=$codecLongName")
                // 如果 codecLongName 为空，根据 codec 生成描述
                val finalCodecLongName = if (codecLongName.isBlank() && codec != "未知") {
                    when (codec.lowercase()) {
                        "mp3", "libmp3lame" -> "MP3 (MPEG-1 Audio Layer III)"
                        "aac" -> "Advanced Audio Coding"
                        "flac" -> "Free Lossless Audio Codec"
                        "opus" -> "Opus Interactive Audio Codec"
                        "vorbis" -> "Vorbis Audio Codec"
                        "pcm_s16le" -> "PCM 16-bit little-endian"
                        "pcm_s24le" -> "PCM 24-bit little-endian"
                        "pcm_s32le" -> "PCM 32-bit little-endian"
                        "pcm_f32le" -> "PCM 32-bit float little-endian"
                        "pcm_f64le" -> "PCM 64-bit float little-endian"
                        "alac" -> "Apple Lossless Audio Codec"
                        "ac3" -> "Dolby Digital AC-3"
                        "eac3" -> "Enhanced AC-3"
                        "dts" -> "DTS Coherent Acoustics"
                        "truehd" -> "Dolby TrueHD"
                        "mlp" -> "Meridian Lossless Packing"
                        "wavpack" -> "WavPack Audio Codec"
                        "tta" -> "True Audio (TTA)"
                        "ape" -> "Monkey's Audio"
                        "wma" -> "Windows Media Audio"
                        "wmalossless" -> "Windows Media Audio Lossless"
                        "ra" -> "RealAudio"
                        "amr" -> "Adaptive Multi-Rate"
                        "amr_wb" -> "Adaptive Multi-Rate Wideband"
                        "g723_1" -> "G.723.1"
                        "g729" -> "G.729"
                        "speex" -> "Speex Audio Codec"
                        "gsm" -> "GSM Audio"
                        "aac_latm" -> "AAC LATM"
                        "aac_fixed" -> "AAC Fixed"
                        "aac_mf" -> "AAC (MediaFoundation)"
                        "libfdk_aac" -> "Fraunhofer FDK AAC"
                        "libopus" -> "Opus (libopus)"
                        "libvorbis" -> "Vorbis (libvorbis)"
                        "libmp3lame" -> "MP3 (LAME)"
                        "libshine" -> "MP3 (Shine)"
                        "libtwolame" -> "MP2 (TwoLAME)"
                        "libspeex" -> "Speex (libspeex)"
                        else -> codec
                    }
                } else {
                    codecLongName
                }

                // 解析位深
                val bitDepth = audioStream.sampleFormat?.let { fmt ->
                    when {
                        fmt.contains("s16") || fmt.contains("s16p") -> "16-bit"
                        fmt.contains("s24") || fmt.contains("s24p") -> "24-bit"
                        fmt.contains("s32") || fmt.contains("s32p") -> "32-bit"
                        fmt.contains("flt") || fmt.contains("fltp") -> "浮点"
                        fmt.contains("dbl") || fmt.contains("dblp") -> "双精度"
                        else -> null
                    }
                } ?: audioStream.bitrate?.toIntOrNull()?.let {
                    if (it > 0) "${it}-bit" else null
                }

                // 获取格式名称
                val formatName = mediaInformation.format ?: ""

                return@withContext AudioInfo(
                    filePath = filePath,
                    fileName = file.name,
                    fileSize = fileSize,
                    duration = duration,
                    durationSeconds = durationSeconds,
                    bitRate = bitRateFormatted,
                    sampleRate = sampleRateFormatted,
                    channels = channelsFormatted,
                    codec = codec,
                    codecLongName = finalCodecLongName,
                    bitDepth = bitDepth,
                    streamCount = streams.size ?: 0,
                    isVideoPresent = hasVideo,
                    formatName = formatName
                )

            } catch (e: Exception) {
                Log.e("FFmpegViewModel", "获取音频信息失败", e)
                return@withContext null
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatDuration(seconds: Double): String {
        val totalSeconds = seconds.toLong()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "${sizeBytes} B"
            sizeBytes < 1024 * 1024 -> String.format("%.2f KB", sizeBytes / 1024.0)
            sizeBytes < 1024 * 1024 * 1024 -> String.format(
                "%.2f MB",
                sizeBytes / (1024.0 * 1024.0)
            )

            else -> String.format("%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    // ============================================================
// 视频信息相关
// ============================================================

    data class VideoInfo(
        val filePath: String,
        val fileName: String,
        val fileSize: String,
        val duration: String,
        val durationSeconds: Double,
        val bitRate: String,
        val width: Int,
        val height: Int,
        val resolution: String,
        val frameRate: String,
        val videoCodec: String,
        val videoCodecLongName: String,
        val audioCodec: String,
        val audioCodecLongName: String,
        val sampleRate: String,
        val channels: String,
        val audioBitRate: String,
        val pixelFormat: String? = null,
        val colorSpace: String? = null,
        val hdr: Boolean = false,
        val hasAudio: Boolean = false,
        val streamCount: Int = 0,
        val formatName: String = ""
    )

    @SuppressLint("DefaultLocale")
    suspend fun getVideoInfo(filePath: String): VideoInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Log.e("FFmpegViewModel", "文件不存在: $filePath")
                    return@withContext null
                }

                val mediaInfoSession = FFprobeKit.getMediaInformation(filePath)
                val mediaInformation = mediaInfoSession.mediaInformation

                if (mediaInformation == null) {
                    Log.e("FFmpegViewModel", "无法获取媒体信息: ${mediaInfoSession.failStackTrace}")
                    return@withContext null
                }

                val streams = mediaInformation.streams
                if (streams.isNullOrEmpty()) {
                    Log.e("FFmpegViewModel", "未找到任何流")
                    return@withContext null
                }

                // 获取视频流
                val videoStream = streams.firstOrNull { it.type == "video" }
                if (videoStream == null) {
                    Log.e("FFmpegViewModel", "未找到视频流")
                    return@withContext null
                }

                // 获取音频流
                val audioStream = streams.firstOrNull { it.type == "audio" }

                // 解析时长
                val durationStr = mediaInformation.duration ?: "0"
                val durationSeconds = durationStr.toDoubleOrNull() ?: 0.0
                val duration = formatDuration(durationSeconds)

                // 解析文件大小
                val fileSizeBytes = file.length()
                val fileSize = formatFileSize(fileSizeBytes)

                // 解析视频尺寸
                val width = videoStream.width?.toInt() ?: 0
                val height = videoStream.height?.toInt() ?: 0
                val resolution = if (width > 0 && height > 0) "${width}x${height}" else "未知"

                // 解析帧率
                val frameRate = videoStream.realFrameRate ?: ""
                val frameRateFormatted = if (frameRate.isNotBlank()) {
                    try {
                        val parts = frameRate.split('/')
                        if (parts.size == 2) {
                            val fps =
                                parts[0].toDoubleOrNull()?.div(parts[1].toDoubleOrNull() ?: 1.0)
                            if (fps != null && fps > 0) {
                                String.format("%.2f fps", fps)
                            } else {
                                frameRate
                            }
                        } else {
                            frameRate
                        }
                    } catch (_: Exception) {
                        frameRate
                    }
                } else {
                    "未知"
                }

                // 解析视频编码器
                val videoCodec = videoStream.codec ?: "未知"
                val videoCodecLongName = videoStream.codecLong ?: ""

                // 解析像素格式
                val pixelFormat = videoStream.getStringProperty("pix_fmt") ?: "未知"

                // 解析色彩空间
                val colorSpace = videoStream.getStringProperty("color_space") ?: "未知"

                //Log.d("FFmpegViewModel","色彩空间: $colorSpace")

                // 输出全部property信息用于调试
//                val allProperties = mediaInformation.allProperties
//                Log.d("FFmpegViewModel", "所有属性: ${allProperties.toString()}")
//                Log.d("FFmpegViewModel", "视频流属性: ${videoStream.allProperties.toString()}")


                // 检测 HDR（基于色彩空间和色深）
                val hdr = colorSpace.let {
                    it.contains("2020", ignoreCase = true) ||
                            it.contains("2100", ignoreCase = true) ||
                            it.contains("st2084", ignoreCase = true) ||
                            it.contains("smpte", ignoreCase = true)
                } ?: false

                // 解析比特率
                val bitRate = videoStream.bitrate ?: mediaInformation.bitrate
                val bitRateFormatted = bitRate?.toLongOrNull()?.let {
                    if (it > 0) {
                        when {
                            it >= 1000000 -> String.format("%.2f Mbps", it / 1000000.0)
                            it >= 1000 -> String.format("%.2f kbps", it / 1000.0)
                            else -> "${it} bps"
                        }
                    } else {
                        "未知"
                    }
                } ?: "未知"

                // 解析音频信息
                val audioCodec = audioStream?.codec ?: "无音频流"
                val audioCodecLongName = audioStream?.codecLong ?: ""
                val audioBitRate = audioStream?.bitrate?.toLongOrNull()?.let {
                    if (it > 0) {
                        when {
                            it >= 1000000 -> String.format("%.2f Mbps", it / 1000000.0)
                            it >= 1000 -> String.format("%.2f kbps", it / 1000.0)
                            else -> "${it} bps"
                        }
                    } else {
                        "未知"
                    }
                } ?: "未知"

                val sampleRate = audioStream?.sampleRate?.toIntOrNull()?.let {
                    if (it >= 1000) "${it / 1000} kHz" else "${it} Hz"
                } ?: "未知"

                val channelLayout = audioStream?.channelLayout
                val channelsFormatted = when {
                    channelLayout.isNullOrBlank() -> "未知"
                    channelLayout.equals(
                        "mono",
                        ignoreCase = true
                    ) || channelLayout == "1" -> "单声道 (1)"

                    channelLayout.equals(
                        "stereo",
                        ignoreCase = true
                    ) || channelLayout == "2" -> "立体声 (2)"

                    channelLayout.equals(
                        "5.1",
                        ignoreCase = true
                    ) || channelLayout == "6" -> "5.1 声道 (6)"

                    channelLayout.equals(
                        "7.1",
                        ignoreCase = true
                    ) || channelLayout == "8" -> "7.1 声道 (8)"

                    else -> {
                        val numberMatch = Regex("\\d+").find(channelLayout)
                        if (numberMatch != null) {
                            val num = numberMatch.value.toIntOrNull()
                            when (num) {
                                1 -> "单声道 (1)"
                                2 -> "立体声 (2)"
                                6 -> "5.1 声道 (6)"
                                8 -> "7.1 声道 (8)"
                                else -> "${num} 声道"
                            }
                        } else {
                            channelLayout
                        }
                    }
                }

                // 获取格式名称
                val formatName = mediaInformation.format ?: ""

                return@withContext VideoInfo(
                    filePath = filePath,
                    fileName = file.name,
                    fileSize = fileSize,
                    duration = duration,
                    durationSeconds = durationSeconds,
                    bitRate = bitRateFormatted,
                    width = width,
                    height = height,
                    resolution = resolution,
                    frameRate = frameRateFormatted,
                    videoCodec = videoCodec,
                    videoCodecLongName = videoCodecLongName,
                    audioCodec = audioCodec,
                    audioCodecLongName = audioCodecLongName,
                    sampleRate = sampleRate,
                    channels = channelsFormatted,
                    audioBitRate = audioBitRate,
                    pixelFormat = pixelFormat,
                    colorSpace = colorSpace,
                    hdr = hdr,
                    hasAudio = audioStream != null,
                    streamCount = streams.size,
                    formatName = formatName
                )

            } catch (e: Exception) {
                Log.e("FFmpegViewModel", "获取视频信息失败", e)
                return@withContext null
            }
        }
    }

    // ============================================================
// 音频元数据相关
// ============================================================

    /**
     * 获取音频文件的元数据（标签信息）
     */
    suspend fun getAudioMetadata(filePath: String): Map<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val mediaInfoSession = FFprobeKit.getMediaInformation(filePath)
                val mediaInformation = mediaInfoSession.mediaInformation
                if (mediaInformation == null) {
                    Log.e("FFmpegViewModel", "无法获取媒体信息: ${mediaInfoSession.failStackTrace}")
                    return@withContext null
                }

                // 获取所有元数据标签 (JSONObject)
                val tags = mediaInformation.tags
                if (tags == null || tags.length() == 0) {
                    return@withContext emptyMap()
                }

                val result = mutableMapOf<String, String>()
                val keys = tags.keys()

                // ============================================================
                // 扩展标签映射 - 支持更多标准标签
                // ============================================================
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = tags.optString(key, "")
                    if (value.isNotEmpty()) {
                        when (key.lowercase()) {
                            // 基本标签
                            "title" -> result["title"] = value
                            "artist" -> result["artist"] = value
                            "album" -> result["album"] = value
                            "album_artist", "albumartist" -> result["album_artist"] = value
                            "date", "year" -> result["year"] = value
                            "genre" -> result["genre"] = value
                            "track", "tracknumber" -> result["track"] = value
                            "tracktotal" -> result["tracktotal"] = value
                            "disc", "discnumber" -> result["disc"] = value
                            "disctotal" -> result["disctotal"] = value
                            "comment" -> result["comment"] = value
                            "composer" -> result["composer"] = value
                            "encoder" -> result["encoder"] = value

                            // 扩展标签
                            "performer" -> result["performer"] = value
                            "lyricist" -> result["lyricist"] = value
                            "arranger" -> result["arranger"] = value
                            "conductor" -> result["conductor"] = value
                            "orchestra" -> result["orchestra"] = value
                            "ensemble" -> result["ensemble"] = value

                            "bpm" -> result["bpm"] = value
                            "tempo" -> result["tempo"] = value
                            "key" -> result["key"] = value
                            "mood" -> result["mood"] = value

                            "copyright" -> result["copyright"] = value
                            "license" -> result["license"] = value
                            "organization" -> result["organization"] = value
                            "publisher" -> result["publisher"] = value

                            "isrc" -> result["isrc"] = value
                            "iswc" -> result["iswc"] = value
                            "catalognumber", "catalog_number" -> result["catalognumber"] = value

                            "url" -> result["url"] = value
                            "website" -> result["website"] = value

                            "language" -> result["language"] = value
                            "lyrics" -> result["lyrics"] = value

                            "description" -> result["description"] = value
                            "synopsis" -> result["synopsis"] = value

                            "artist_sort" -> result["artist_sort"] = value
                            "album_sort" -> result["album_sort"] = value
                            "title_sort" -> result["title_sort"] = value

                            "rating" -> result["rating"] = value
                            "acoustid_id" -> result["acoustid"] = value
                            "musicbrainz_track_id" -> result["musicbrainz_track_id"] = value
                            "musicbrainz_album_id" -> result["musicbrainz_album_id"] = value
                            "musicbrainz_artist_id" -> result["musicbrainz_artist_id"] = value
                            "musicbrainz_releasegroup_id" -> result["musicbrainz_releasegroup_id"] = value

                            "replaygain_track_gain" -> result["replaygain_track_gain"] = value
                            "replaygain_track_peak" -> result["replaygain_track_peak"] = value
                            "replaygain_album_gain" -> result["replaygain_album_gain"] = value
                            "replaygain_album_peak" -> result["replaygain_album_peak"] = value

                            "initial_key" -> result["initial_key"] = value
                            "remixer" -> result["remixer"] = value
                            "producer" -> result["producer"] = value
                            "engineer" -> result["engineer"] = value
                            "studio" -> result["studio"] = value
                            "location" -> result["location"] = value

                            "compilation" -> result["compilation"] = value
                            "podcast" -> result["podcast"] = value
                            "podcasturl" -> result["podcasturl"] = value
                            "podcastcategory" -> result["podcastcategory"] = value

                            "gapless" -> result["gapless"] = value
                            "media" -> result["media"] = value
                            "original_date" -> result["original_date"] = value
                            "original_year" -> result["original_year"] = value
                        }
                    }
                }

                // 补充字段（如果日期存在但年份不存在）
                if (!result.containsKey("year")) {
                    tags.optString("date", "").takeIf { it.isNotEmpty() }?.let {
                        // 提取年份（如 "2024-01-01" -> "2024"）
                        val yearMatch = Regex("\\d{4}").find(it)
                        if (yearMatch != null) {
                            result["year"] = yearMatch.value
                        } else {
                            result["year"] = it
                        }
                    }
                }

                return@withContext result
            } catch (e: Exception) {
                Log.e("FFmpegViewModel", "获取音频元数据失败", e)
                return@withContext null
            }
        }
    }

    /**
     * 编辑音频标签（元数据）- 支持完整标签集
     */
    fun editAudioTags(
        inputPath: String,
        outputPath: String,
        metadata: Map<String, String>,
        coverPath: String? = null,
        onComplete: (Boolean, String) -> Unit
    ) {
        // 构建 FFmpeg 命令
        val command = buildString {
            append("-i \"$inputPath\"")
            if (coverPath != null && File(coverPath).exists()) {
                append(" -i \"$coverPath\"")
            }

            // 添加所有元数据（支持更多标签）
            metadata.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    // 将内部键名映射到 FFmpeg 元数据键名
                    val ffmpegKey = when (key) {
                        "album_artist" -> "album_artist"
                        "tracktotal" -> "tracktotal"
                        "disc" -> "disc"
                        "disctotal" -> "disctotal"
                        "performer" -> "performer"
                        "lyricist" -> "lyricist"
                        "arranger" -> "arranger"
                        "conductor" -> "conductor"
                        "orchestra" -> "orchestra"
                        "ensemble" -> "ensemble"
                        "bpm" -> "bpm"
                        "tempo" -> "tempo"
                        "key" -> "key"
                        "mood" -> "mood"
                        "copyright" -> "copyright"
                        "license" -> "license"
                        "organization" -> "organization"
                        "publisher" -> "publisher"
                        "isrc" -> "isrc"
                        "iswc" -> "iswc"
                        "catalognumber" -> "catalognumber"
                        "url" -> "url"
                        "website" -> "website"
                        "language" -> "language"
                        "lyrics" -> "lyrics"
                        "description" -> "description"
                        "synopsis" -> "synopsis"
                        "artist_sort" -> "artist_sort"
                        "album_sort" -> "album_sort"
                        "title_sort" -> "title_sort"
                        "rating" -> "rating"
                        "acoustid" -> "acoustid_id"
                        "musicbrainz_track_id" -> "musicbrainz_track_id"
                        "musicbrainz_album_id" -> "musicbrainz_album_id"
                        "musicbrainz_artist_id" -> "musicbrainz_artist_id"
                        "musicbrainz_releasegroup_id" -> "musicbrainz_releasegroup_id"
                        "replaygain_track_gain" -> "replaygain_track_gain"
                        "replaygain_track_peak" -> "replaygain_track_peak"
                        "replaygain_album_gain" -> "replaygain_album_gain"
                        "replaygain_album_peak" -> "replaygain_album_peak"
                        "initial_key" -> "initial_key"
                        "remixer" -> "remixer"
                        "producer" -> "producer"
                        "engineer" -> "engineer"
                        "studio" -> "studio"
                        "location" -> "location"
                        "compilation" -> "compilation"
                        "podcast" -> "podcast"
                        "podcasturl" -> "podcasturl"
                        "podcastcategory" -> "podcastcategory"
                        "gapless" -> "gapless"
                        "media" -> "media"
                        "original_date" -> "original_date"
                        "original_year" -> "original_year"
                        else -> key  // 直接使用原键名
                    }
                    append(" -metadata $ffmpegKey=\"${value.replace("\"", "\\\"")}\"")
                }
            }

            // 如果提供了封面，映射流并设置封面
            if (coverPath != null && File(coverPath).exists()) {
                append(" -map 0:a")  // 音频流
                append(" -map 1")    // 封面图片流
                append(" -c copy")   // 全部复制（不重新编码）
                append(" -metadata:s:v title=\"Cover\"")
                append(" -disposition:v attached_pic")
            } else {
                append(" -c:a copy")
                append(" -vn")  // 禁止视频
            }
            append(" -y \"$outputPath\"")
        }

        val taskId = UUID.randomUUID().toString()
        val taskType = "标签编辑"

        executeFFmpegCommand(
            command = command,
            taskId = taskId,
            type = taskType,
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }
}