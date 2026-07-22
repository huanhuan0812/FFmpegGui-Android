// FFmpegTutorialData.kt
package com.huanhuan.ffmpeggui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

data class FFmpegParameter(
    val name: String,
    val shortName: String? = null,
    val category: String,
    val description: String,
    val usage: String,
    val example: String,
    val aliases: List<String> = emptyList()
)

data class FFmpegCategory(
    val name: String,
    val icon: ImageVector,
    val description: String,
    val parameters: List<FFmpegParameter>
)

object FFmpegTutorialData {
    val categories = listOf(
        FFmpegCategory(
            name = "基础参数",
            icon = Icons.Default.Info,
            description = "FFmpeg 最基础、最常用的参数",
            parameters = listOf(
                FFmpegParameter(
                    name = "输入文件 (-i)",
                    shortName = "-i",
                    category = "基础参数",
                    description = "指定输入文件路径，支持多个输入文件",
                    usage = "-i [input_file]",
                    example = "-i input.mp4",
                    aliases = listOf("-i")
                ),
                FFmpegParameter(
                    name = "输出文件",
                    shortName = null,
                    category = "基础参数",
                    description = "指定输出文件路径，可以自动根据扩展名选择格式",
                    usage = "[output_file]",
                    example = "output.mp4",
                    aliases = emptyList()
                ),
                FFmpegParameter(
                    name = "覆盖输出 (-y)",
                    shortName = "-y",
                    category = "基础参数",
                    description = "自动覆盖已存在的输出文件，无需确认",
                    usage = "-y",
                    example = "-y output.mp4",
                    aliases = listOf("-y")
                ),
                FFmpegParameter(
                    name = "不覆盖 (-n)",
                    shortName = "-n",
                    category = "基础参数",
                    description = "不覆盖已存在的输出文件，直接退出",
                    usage = "-n",
                    example = "-n output.mp4",
                    aliases = listOf("-n")
                ),
                FFmpegParameter(
                    name = "静默模式 (-hide_banner)",
                    shortName = "-hide_banner",
                    category = "基础参数",
                    description = "隐藏 FFmpeg 版本信息和编译配置信息",
                    usage = "-hide_banner",
                    example = "-hide_banner -i input.mp4 output.mp4",
                    aliases = listOf("-hide_banner")
                )
            )
        ),
        FFmpegCategory(
            name = "视频编码",
            icon = Icons.Default.VideoLibrary,
            description = "视频编码相关的参数设置",
            parameters = listOf(
                FFmpegParameter(
                    name = "视频编码器 (-c:v)",
                    shortName = "-c:v",
                    category = "视频编码",
                    description = "指定视频编码器。常见编码器：libx264 (H.264), libx265 (H.265), libvpx-vp9 (VP9), libaom-av1 (AV1)",
                    usage = "-c:v [codec]",
                    example = "-c:v libx264",
                    aliases = listOf("-vcodec", "-codec:v")
                ),
                FFmpegParameter(
                    name = "视频码率 (-b:v)",
                    shortName = "-b:v",
                    category = "视频编码",
                    description = "指定视频码率，影响视频质量和文件大小。单位可以是 k (千比特) 或 M (兆比特)",
                    usage = "-b:v [bitrate]",
                    example = "-b:v 2M",
                    aliases = listOf("-bitrate:v")
                ),
                FFmpegParameter(
                    name = "CRF 值 (-crf)",
                    shortName = "-crf",
                    category = "视频编码",
                    description = "恒定质量因子，0-51 范围。数值越小质量越高，文件越大。推荐值：18-28",
                    usage = "-crf [value]",
                    example = "-crf 23",
                    aliases = listOf("-crf")
                ),
                FFmpegParameter(
                    name = "预设 (-preset)",
                    shortName = "-preset",
                    category = "视频编码",
                    description = "编码速度与压缩率的权衡。可选：ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow",
                    usage = "-preset [preset]",
                    example = "-preset medium",
                    aliases = listOf("-preset")
                ),
                FFmpegParameter(
                    name = "帧率 (-r)",
                    shortName = "-r",
                    category = "视频编码",
                    description = "设置视频帧率，常用值：24, 25, 30, 60",
                    usage = "-r [fps]",
                    example = "-r 30",
                    aliases = listOf("-framerate")
                ),
                FFmpegParameter(
                    name = "分辨率 (-s)",
                    shortName = "-s",
                    category = "视频编码",
                    description = "设置视频分辨率，格式为 WxH。可以缩放视频尺寸",
                    usage = "-s [width]x[height]",
                    example = "-s 1920x1080",
                    aliases = listOf("-video_size")
                ),
                FFmpegParameter(
                    name = "像素格式 (-pix_fmt)",
                    shortName = "-pix_fmt",
                    category = "视频编码",
                    description = "设置像素格式。常用：yuv420p, yuv422p, yuv444p, rgb24",
                    usage = "-pix_fmt [format]",
                    example = "-pix_fmt yuv420p",
                    aliases = listOf("-pixel_format")
                )
            )
        ),
        FFmpegCategory(
            name = "音频编码",
            icon = Icons.Default.Audiotrack,
            description = "音频编码相关的参数设置",
            parameters = listOf(
                FFmpegParameter(
                    name = "音频编码器 (-c:a)",
                    shortName = "-c:a",
                    category = "音频编码",
                    description = "指定音频编码器。常见：aac (AAC), mp3 (MP3), libopus (Opus), pcm_s16le (WAV)",
                    usage = "-c:a [codec]",
                    example = "-c:a aac",
                    aliases = listOf("-acodec", "-codec:a")
                ),
                FFmpegParameter(
                    name = "音频码率 (-b:a)",
                    shortName = "-b:a",
                    category = "音频编码",
                    description = "指定音频码率。常用值：128k, 192k, 320k",
                    usage = "-b:a [bitrate]",
                    example = "-b:a 192k",
                    aliases = listOf("-abitrate")
                ),
                FFmpegParameter(
                    name = "采样率 (-ar)",
                    shortName = "-ar",
                    category = "音频编码",
                    description = "设置音频采样率。常用值：44100, 48000, 96000",
                    usage = "-ar [sample_rate]",
                    example = "-ar 48000",
                    aliases = listOf("-sample_rate")
                ),
                FFmpegParameter(
                    name = "声道数 (-ac)",
                    shortName = "-ac",
                    category = "音频编码",
                    description = "设置音频声道数。1=单声道, 2=立体声, 6=5.1环绕声",
                    usage = "-ac [channels]",
                    example = "-ac 2",
                    aliases = listOf("-channels")
                ),
                FFmpegParameter(
                    name = "音量调节 (-volume)",
                    shortName = "-volume",
                    category = "音频编码",
                    description = "调整音频音量，256 为原始音量，512 为两倍音量",
                    usage = "-volume [value]",
                    example = "-volume 256",
                    aliases = listOf("-vol")
                )
            )
        ),
        FFmpegCategory(
            name = "滤镜处理",
            icon = Icons.Default.Filter,
            description = "视频和音频滤镜处理",
            parameters = listOf(
                FFmpegParameter(
                    name = "视频滤镜 (-vf)",
                    shortName = "-vf",
                    category = "滤镜处理",
                    description = "应用视频滤镜。支持多种滤镜，如缩放、裁剪、旋转、水印等",
                    usage = "-vf [filter]",
                    example = "-vf scale=1280:720",
                    aliases = listOf("-filter:v", "-video_filter")
                ),
                FFmpegParameter(
                    name = "缩放滤镜 (scale)",
                    shortName = "scale",
                    category = "滤镜处理",
                    description = "缩放视频尺寸。可以指定宽高，-1 表示自动保持比例",
                    usage = "scale=[width]:[height]",
                    example = "scale=1280:-1",
                    aliases = listOf("scale")
                ),
                FFmpegParameter(
                    name = "裁剪滤镜 (crop)",
                    shortName = "crop",
                    category = "滤镜处理",
                    description = "裁剪视频区域。格式：宽:高:起始X:起始Y",
                    usage = "crop=[w]:[h]:[x]:[y]",
                    example = "crop=640:480:0:0",
                    aliases = listOf("crop")
                ),
                FFmpegParameter(
                    name = "旋转滤镜 (rotate)",
                    shortName = "rotate",
                    category = "滤镜处理",
                    description = "旋转视频。单位是弧度，可以结合三角函数使用",
                    usage = "rotate=[radians]",
                    example = "rotate=PI/2",
                    aliases = listOf("rotate")
                ),
                FFmpegParameter(
                    name = "音频滤镜 (-af)",
                    shortName = "-af",
                    category = "滤镜处理",
                    description = "应用音频滤镜。如音量调整、均衡器、混音等",
                    usage = "-af [filter]",
                    example = "-af volume=2",
                    aliases = listOf("-filter:a", "-audio_filter")
                ),
                FFmpegParameter(
                    name = "水印叠加 (overlay)",
                    shortName = "overlay",
                    category = "滤镜处理",
                    description = "在视频上叠加图片或另一个视频作为水印",
                    usage = "overlay=[x]:[y]",
                    example = "overlay=10:10",
                    aliases = listOf("overlay")
                )
            )
        ),
        FFmpegCategory(
            name = "时间控制",
            icon = Icons.Default.Timer,
            description = "控制视频/音频的时长和截取",
            parameters = listOf(
                FFmpegParameter(
                    name = "开始时间 (-ss)",
                    shortName = "-ss",
                    category = "时间控制",
                    description = "指定开始截取的时间点。格式：HH:MM:SS 或秒数",
                    usage = "-ss [time]",
                    example = "-ss 00:01:30",
                    aliases = listOf("-seek")
                ),
                FFmpegParameter(
                    name = "持续时间 (-t)",
                    shortName = "-t",
                    category = "时间控制",
                    description = "指定截取的持续时间。格式：HH:MM:SS 或秒数",
                    usage = "-t [duration]",
                    example = "-t 00:00:30",
                    aliases = listOf("-duration")
                ),
                FFmpegParameter(
                    name = "结束时间 (-to)",
                    shortName = "-to",
                    category = "时间控制",
                    description = "指定结束时间点。格式：HH:MM:SS 或秒数",
                    usage = "-to [time]",
                    example = "-to 00:02:00",
                    aliases = listOf("-end_time")
                )
            )
        ),
        FFmpegCategory(
            name = "格式封装",
            icon = Icons.Default.FolderOpen,
            description = "容器格式和封装相关参数",
            parameters = listOf(
                FFmpegParameter(
                    name = "格式转换 (-f)",
                    shortName = "-f",
                    category = "格式封装",
                    description = "强制指定输出格式。常用：mp4, avi, mov, mkv, flv, webm",
                    usage = "-f [format]",
                    example = "-f mp4",
                    aliases = listOf("-format")
                ),
                FFmpegParameter(
                    name = "复制流 (-c copy)",
                    shortName = "-c copy",
                    category = "格式封装",
                    description = "直接复制流而不重新编码，速度极快，无质量损失",
                    usage = "-c copy",
                    example = "-c copy output.mp4",
                    aliases = listOf("-codec copy")
                ),
                FFmpegParameter(
                    name = "复制视频 (-c:v copy)",
                    shortName = "-c:v copy",
                    category = "格式封装",
                    description = "仅复制视频流，不重新编码视频",
                    usage = "-c:v copy",
                    example = "-c:v copy -c:a aac output.mp4",
                    aliases = listOf("-vcodec copy")
                ),
                FFmpegParameter(
                    name = "复制音频 (-c:a copy)",
                    shortName = "-c:a copy",
                    category = "格式封装",
                    description = "仅复制音频流，不重新编码音频",
                    usage = "-c:a copy",
                    example = "-c:v libx264 -c:a copy output.mp4",
                    aliases = listOf("-acodec copy")
                )
            )
        ),
        FFmpegCategory(
            name = "高级参数",
            icon = Icons.Default.Terminal,
            description = "FFmpeg 的高级和调试参数",
            parameters = listOf(
                FFmpegParameter(
                    name = "线程数 (-threads)",
                    shortName = "-threads",
                    category = "高级参数",
                    description = "指定编码使用的线程数，加快编码速度",
                    usage = "-threads [count]",
                    example = "-threads 4",
                    aliases = listOf("-threads")
                ),
                FFmpegParameter(
                    name = "日志级别 (-loglevel)",
                    shortName = "-loglevel",
                    category = "高级参数",
                    description = "设置日志输出级别。可选：quiet, panic, fatal, error, warning, info, verbose, debug, trace",
                    usage = "-loglevel [level]",
                    example = "-loglevel error",
                    aliases = listOf("-v")
                ),
                FFmpegParameter(
                    name = "帧数限制 (-frames:v)",
                    shortName = "-frames:v",
                    category = "高级参数",
                    description = "限制处理的视频帧数，用于测试或提取特定帧",
                    usage = "-frames:v [count]",
                    example = "-frames:v 100",
                    aliases = listOf("-vframes")
                ),
                FFmpegParameter(
                    name = "元数据 (-metadata)",
                    shortName = "-metadata",
                    category = "高级参数",
                    description = "设置或修改文件的元数据，如标题、作者等",
                    usage = "-metadata [key]=[value]",
                    example = "-metadata title=\"My Video\"",
                    aliases = listOf("-metadata")
                ),
                FFmpegParameter(
                    name = "比特率容差 (-bt)",
                    shortName = "-bt",
                    category = "高级参数",
                    description = "设置比特率容差，影响编码的比特率控制精度",
                    usage = "-bt [tolerance]",
                    example = "-bt 200k",
                    aliases = listOf("-tolerance")
                ),
                FFmpegParameter(
                    name = "GOP 大小 (-g)",
                    shortName = "-g",
                    category = "高级参数",
                    description = "设置 GOP (Group of Pictures) 大小，影响关键帧间隔",
                    usage = "-g [size]",
                    example = "-g 250",
                    aliases = listOf("-gop")
                )
            )
        ),
        FFmpegCategory(
            name = "常用示例",
            icon = Icons.Default.Star,
            description = "实际场景的完整命令示例",
            parameters = listOf(
                FFmpegParameter(
                    name = "视频格式转换",
                    shortName = null,
                    category = "常用示例",
                    description = "将视频转换为 MP4 格式，使用 H.264 编码和 AAC 音频",
                    usage = "ffmpeg -i input.avi -c:v libx264 -c:a aac -b:v 2M -b:a 192k output.mp4",
                    example = "ffmpeg -i input.avi -c:v libx264 -c:a aac -b:v 2M -b:a 192k output.mp4",
                    aliases = emptyList()
                ),
                FFmpegParameter(
                    name = "视频压缩",
                    shortName = null,
                    category = "常用示例",
                    description = "使用 CRF 方法压缩视频，在质量和文件大小间取得平衡",
                    usage = "ffmpeg -i input.mp4 -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 128k output_compressed.mp4",
                    example = "ffmpeg -i input.mp4 -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 128k output_compressed.mp4",
                    aliases = emptyList()
                ),
                FFmpegParameter(
                    name = "截取片段",
                    shortName = null,
                    category = "常用示例",
                    description = "截取视频从 00:01:30 开始，持续 30 秒的片段",
                    usage = "ffmpeg -i input.mp4 -ss 00:01:30 -t 00:00:30 -c copy output_clip.mp4",
                    example = "ffmpeg -i input.mp4 -ss 00:01:30 -t 00:00:30 -c copy output_clip.mp4",
                    aliases = emptyList()
                ),
                FFmpegParameter(
                    name = "提取音频",
                    shortName = null,
                    category = "常用示例",
                    description = "从视频中提取音频并保存为 MP3 格式",
                    usage = "ffmpeg -i input.mp4 -vn -c:a libmp3lame -b:a 192k output_audio.mp3",
                    example = "ffmpeg -i input.mp4 -vn -c:a libmp3lame -b:a 192k output_audio.mp3",
                    aliases = emptyList()
                ),
                FFmpegParameter(
                    name = "视频缩放",
                    shortName = null,
                    category = "常用示例",
                    description = "将视频缩放到 1080p 分辨率，保持比例",
                    usage = "ffmpeg -i input.mp4 -vf scale=1920:1080 -c:v libx264 -c:a copy output_scaled.mp4",
                    example = "ffmpeg -i input.mp4 -vf scale=1920:1080 -c:v libx264 -c:a copy output_scaled.mp4",
                    aliases = emptyList()
                ),
                FFmpegParameter(
                    name = "添加水印",
                    shortName = null,
                    category = "常用示例",
                    description = "在视频右上角添加图片水印",
                    usage = "ffmpeg -i input.mp4 -i watermark.png -filter_complex \"overlay=W-w-10:10\" output_watermarked.mp4",
                    example = "ffmpeg -i input.mp4 -i watermark.png -filter_complex \"overlay=W-w-10:10\" output_watermarked.mp4",
                    aliases = emptyList()
                ),
                FFmpegParameter(
                    name = "GIF 制作",
                    shortName = null,
                    category = "常用示例",
                    description = "将视频片段转换为 GIF 动图",
                    usage = "ffmpeg -i input.mp4 -ss 00:00:10 -t 00:00:05 -vf fps=10,scale=320:-1 output.gif",
                    example = "ffmpeg -i input.mp4 -ss 00:00:10 -t 00:00:05 -vf fps=10,scale=320:-1 output.gif",
                    aliases = emptyList()
                )
            )
        )
    )
}