package com.huanhuan.ffmpeggui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    init {
        FFmpegKitConfig.enableStatisticsCallback(object : StatisticsCallback {
            override fun apply(statistics: Statistics) {
                progress = statistics.getVideoFrameNumber().toFloat() / 100
                if (progress > 1f) progress = 0.99f
            }
        })

        FFmpegKitConfig.enableLogCallback(object : LogCallback {
            override fun apply(log: com.arthenica.ffmpegkit.Log?) {
                log?.toString().let{
                    logOutput+=it + "\n"
                }
            }
        })
    }

    fun extractAudio(inputPath: String, outputPath: String, onComplete: (Boolean, String) -> Unit) {
        executeFFmpegCommand(
            command = "-i \"$inputPath\" -q:a 0 -map a \"$outputPath\"",
            taskId = UUID.randomUUID().toString(),
            type = "音频提取",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    fun convertAudio(inputPath: String, outputPath: String, format: String, onComplete: (Boolean, String) -> Unit) {
        executeFFmpegCommand(
            command = "-i \"$inputPath\" -acodec ${getAudioCodec(format)} \"$outputPath\"",
            taskId = UUID.randomUUID().toString(),
            type = "音频转换",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    fun convertVideo(inputPath: String, outputPath: String, format: String, onComplete: (Boolean, String) -> Unit) {
        val codec = when (format.lowercase()) {
            "mp4" -> "libx264"
            "avi" -> "mpeg4"
            "mkv" -> "libx265"
            "mov" -> "libx264"
            else -> "libx264"
        }

        executeFFmpegCommand(
            command = "-i \"$inputPath\" -c:v $codec -preset ultrafast \"$outputPath\"",
            taskId = UUID.randomUUID().toString(),
            type = "视频转换",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }

    private fun getAudioCodec(format: String): String {
        return when (format.lowercase()) {
            "mp3" -> "libmp3lame"
            "aac" -> "aac"
            "flac" -> "flac"
            "wav" -> "pcm_s16le"
            "ogg" -> "libvorbis"
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
        viewModelScope.launch {
            isProcessing = true
            progress = 0f
            currentCommand = command
            logOutput = ""

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

            val result = withContext(Dispatchers.IO) {
                FFmpegKit.execute(command)
            }

            task.endTime = System.currentTimeMillis()

            val success = result.returnCode.isValueSuccess
            task.status = if (success) "完成" else "失败"
            task.progress = if (success) 1f else 0f

            isProcessing = false

            val message = if (success) {
                "处理成功: $outputPath"
            } else {
                "处理失败: ${result.returnCode} - ${result.output}"
            }

            onComplete(success, message)
        }
    }

    fun clearHistory() {
        historyTasks.clear()
    }

    // 在 FFmpegViewModel.kt 中添加以下方法

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

        // 构建音频转换命令
        val command = buildString {
            append("-i \"$inputPath\"")

            // 音频编码器
            append(" -acodec $codec")

            // 比特率设置（如果不是无损格式）
            if (format != "flac" && format != "wav" && bitrate.isNotBlank()) {
                append(" -b:a $bitrate")
            }

            // 采样率
            if (sampleRate.isNotBlank()) {
                append(" -ar $sampleRate")
            }

            // 声道数
            if (channels.isNotBlank()) {
                append(" -ac $channels")
            }

            // 输出文件
            append(" -y \"$outputPath\"")
        }

        executeFFmpegCommand(
            command = command,
            taskId = UUID.randomUUID().toString(),
            type = "音频转换 - ${format.uppercase()}",
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
        // 视频编码器映射
        val vcodec = when (videoCodec.lowercase()) {
            "h264" -> "libx264"
            "h265" -> "libx265"
            "mpeg4" -> "mpeg4"
            "vp9" -> "libvpx-vp9"
            else -> "libx264"
        }

        // 音频编码器映射
        val acodec = when (audioCodec.lowercase()) {
            "aac" -> "aac"
            "mp3" -> "libmp3lame"
            "copy" -> "copy"
            else -> "aac"
        }

        // 质量预设
        val preset = when (quality.lowercase()) {
            "low" -> "veryfast"
            "medium" -> "medium"
            "high" -> "slow"
            else -> "medium"
        }

        // 视频质量参数
        val crf = when (quality.lowercase()) {
            "low" -> "28"
            "medium" -> "23"
            "high" -> "18"
            else -> "23"
        }

        // 分辨率设置
        val scale = when (resolution.lowercase()) {
            "1080p" -> ",scale=1920:1080"
            "720p" -> ",scale=1280:720"
            "480p" -> ",scale=854:480"
            "360p" -> ",scale=640:360"
            else -> ""
        }

        // 构建视频转换命令
        val command = buildString {
            append("-i \"$inputPath\"")

            // 视频编码设置
            if (vcodec == "copy" && acodec == "copy") {
                // 直接复制流
                append(" -c copy")
            } else {
                // 视频编码
                append(" -c:v $vcodec")
                if (vcodec == "libx264" || vcodec == "libx265") {
                    append(" -preset $preset")
                    append(" -crf $crf")
                }

                // 分辨率调整
                if (scale.isNotEmpty()) {
                    append(" -vf \"format=yuv420p$scale\"")
                }

                // 音频编码
                append(" -c:a $acodec")
                if (acodec != "copy") {
                    when (quality) {
                        "low" -> append(" -b:a 96k")
                        "medium" -> append(" -b:a 128k")
                        "high" -> append(" -b:a 192k")
                    }
                }
            }

            // 输出格式特定设置
            when (format.lowercase()) {
                "mp4" -> append(" -movflags +faststart")
                "webm" -> {
                    if (vcodec == "libvpx-vp9") {
                        append(" -cpu-used -5 -deadline realtime")
                    }
                }
            }

            // 输出文件
            append(" -y \"$outputPath\"")
        }

        executeFFmpegCommand(
            command = command,
            taskId = UUID.randomUUID().toString(),
            type = "视频转换 - ${format.uppercase()}",
            inputPath = inputPath,
            outputPath = outputPath,
            onComplete = onComplete
        )
    }
}