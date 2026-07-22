package com.huanhuan.ffmpeggui

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
        id = this.endTime ?: this.startTime,
        key = this.id,
        name = "${this.type} - ${File(this.outputPath).name}",
        path = this.outputPath,
        timestamp = this.endTime ?: this.startTime,
        createdAt = this.startTime,
        size = File(this.outputPath).length().toInt()
    )
}

fun History.toConversionTask(): ConversionTask {
    return ConversionTask(
        id = this.key,
        inputPath = "",
        outputPath = this.path,
        type = try {
            this.name.substringBefore(" - ")
        } catch (e: Exception) {
            "未知类型"
        },
        status = "完成",
        progress = 1f,
        startTime = this.createdAt,
        endTime = this.timestamp
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
            this.database = HistoryDatabase.getInstance(context)
            Log.d("FFmpegViewModel", "数据库实例获取成功")
            loadHistoryTasks()
        } catch (e: Exception) {
            Log.e("FFmpegViewModel", "数据库初始化失败", e)
            e.printStackTrace()
        }
    }

    private fun loadHistoryTasks() {
        viewModelScope.launch {
            try {
                Log.d("FFmpegViewModel", "开始加载历史记录")
                database?.historyDao()?.getAllHistories()?.collect { histories ->
                    Log.d("FFmpegViewModel", "获取到 ${histories.size} 条历史记录")
                    historyTasks.clear()
                    historyTasks.addAll(histories.map {
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
                    val timeInMilliseconds = statistics.time
                    currentTimeMs = timeInMilliseconds

                    if (totalDurationMs > 0) {
                        progress = (timeInMilliseconds.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 0.99f)
                    } else {
                        val frameNumber = statistics.videoFrameNumber
                        if (estimatedTotalFrames > 0 && frameNumber > 0) {
                            progress = (frameNumber.toFloat() / estimatedTotalFrames.toFloat()).coerceIn(0f, 0.99f)
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
                    progress = (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 0.99f)
                }
            } catch (e: NumberFormatException) {
                // 忽略
            }
        }

        val framePattern = Regex("frame=\\s*(\\d+)")
        val frameMatch = framePattern.find(logMessage)

        frameMatch?.let {
            try {
                val frameNumber = it.groupValues[1].toInt()
                if (estimatedTotalFrames > 0 && frameNumber > 0) {
                    progress = (frameNumber.toFloat() / estimatedTotalFrames.toFloat()).coerceIn(0f, 0.99f)
                }
            } catch (e: NumberFormatException) {
                // 忽略
            }
        }

        val percentPattern = Regex("progress=(\\d+\\.?\\d*)%")
        val percentMatch = percentPattern.find(logMessage)

        percentMatch?.let {
            try {
                val percent = it.groupValues[1].toFloat()
                progress = (percent / 100f).coerceIn(0f, 0.99f)
            } catch (e: NumberFormatException) {
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
                        bitrate.endsWith("k") && bitrate.substring(0, bitrate.length-1).toIntOrNull()?.let { it <= 96 } == true -> {
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

    fun clearAllHistory() {
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

        historyTasks.clear()
    }

    fun clearHistoryItem(task: ConversionTask) {
        try {
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

            historyTasks.remove(task)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        cancelCurrentProcessing()
        com.arthenica.ffmpegkit.FFmpegKitConfig.enableStatisticsCallback(null)
        com.arthenica.ffmpegkit.FFmpegKitConfig.enableLogCallback(null)
    }

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

    fun convertImageSimple(
        inputPath: String,
        outputPath: String,
        format: String,
        onComplete: (Boolean, String) -> Unit
    ) {
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

    // ============================================================
    // 🔧 核心修复：executeCommand 方法 - 使用 executeAsync
    // ============================================================
    fun executeCommand(command: String) {
        viewModelScope.launch {
            _isExecuting.value = true
            _executionResult.value = "正在执行命令: $command\n\n"

            try {
                // 创建日志回调来实现实时输出
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
                    // 🔧 使用 executeAsync 直接传入完整命令字符串
                    // FFmpegKit 内部会正确处理带引号的路径
                    currentSession = FFmpegKit.executeAsync(
                        command,
                        { session ->
                            viewModelScope.launch(Dispatchers.Main) {
                                handleSessionResult(session)
                            }
                        },
                        logCallback,
                        null // 统计回调，这里不需要
                    )
                }

            } catch (e: Exception) {
                _executionResult.update { it + "\n❌ 执行异常: ${e.message}" }
                _isExecuting.value = false
            }
        }
    }

    private fun handleSessionResult(session: FFmpegSession) {
        val returnCode = session.returnCode
        val output = StringBuilder()

        when {
            ReturnCode.isSuccess(returnCode) -> {
                output.append("\n✅ 命令执行成功\n")
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
    }

    fun clearResult() {
        _executionResult.value = ""
    }

    fun cancelExecution() {
        currentSession?.cancel()
        _isExecuting.value = false
    }

    // 在 FFmpegViewModel.kt 中添加

    // ============================================================
// 🔧 新增：带回调的 executeCommand 方法
// ============================================================
    fun executeCommandWithCallback(
        command: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isExecuting.value = true
            _executionResult.value = "正在执行命令: $command\n\n"

            try {
                // 创建日志回调来实现实时输出
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

    // 带回调的结果处理
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
}