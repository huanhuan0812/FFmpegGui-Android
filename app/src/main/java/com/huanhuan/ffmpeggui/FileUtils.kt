package com.huanhuan.ffmpeggui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream

private const val TAG = "FileUtils"

fun getPathFromUri(context: Context, uri: Uri): String? {
    return when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> {
            try {
                // 获取文件名
                val fileName = getFileName(context, uri) ?: "temp_${System.currentTimeMillis()}.tmp"
                Log.d(TAG, "原始文件名: $fileName")

                // 确保文件名有扩展名
                val finalFileName = ensureFileExtension(context, uri, fileName)
                Log.d(TAG, "最终文件名: $finalFileName")

                // 创建缓存文件
                val cacheFile = File(context.cacheDir, finalFileName)
                Log.d(TAG, "缓存文件路径: ${cacheFile.absolutePath}")

                // 如果文件已存在，删除旧文件
                if (cacheFile.exists()) {
                    Log.d(TAG, "删除已存在的缓存文件: ${cacheFile.delete()}")
                }

                // 复制文件
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    Log.d(TAG, "开始复制文件，输入流可用: ${inputStream.available()}")

                    FileOutputStream(cacheFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var length: Int
                        var totalBytes = 0L
                        while (inputStream.read(buffer).also { length = it } > 0) {
                            outputStream.write(buffer, 0, length)
                            totalBytes += length
                        }
                        Log.d(TAG, "文件复制完成，总大小: $totalBytes bytes")
                    }
                } ?: run {
                    Log.e(TAG, "无法打开输入流，uri: $uri")
                    return null
                }

                // 验证文件是否成功创建
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    Log.d(TAG, "✅ 缓存文件创建成功: ${cacheFile.absolutePath}, 大小: ${cacheFile.length()} bytes")
                    return cacheFile.absolutePath
                } else {
                    Log.e(TAG, "❌ 缓存文件创建失败或为空")
                    return null
                }
            } catch (e: Exception) {
                Log.e(TAG, "复制文件到缓存失败", e)
                return null
            }
        }
        ContentResolver.SCHEME_FILE -> {
            uri.path
        }
        else -> {
            Log.w(TAG, "未知的 URI scheme: ${uri.scheme}")
            null
        }
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null

    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
    }

    if (name == null) {
        name = uri.path
        val cut = name?.lastIndexOf('/')
        if (cut != -1 && cut != null) {
            name = name?.substring(cut + 1)
        }
    }

    Log.d(TAG, "获取文件名: $name")
    return name
}

// 确保文件有正确的扩展名
private fun ensureFileExtension(context: Context, uri: Uri, fileName: String): String {
    // 如果文件名已经有扩展名，直接返回
    if (fileName.contains('.')) {
        return fileName
    }

    // 尝试从 MIME 类型获取扩展名
    val mimeType = context.contentResolver.getType(uri)
    Log.d(TAG, "MIME类型: $mimeType")

    if (mimeType != null) {
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        Log.d(TAG, "从MIME获取的扩展名: $extension")
        if (extension != null) {
            return "$fileName.$extension"
        }
    }

    // 根据 MIME 类型默认扩展名
    when {
        mimeType?.startsWith("video/") == true -> return "$fileName.mp4"
        mimeType?.startsWith("audio/") == true -> return "$fileName.mp3"
        mimeType?.startsWith("image/") == true -> return "$fileName.jpg"
        else -> return "$fileName.mp4" // 默认使用 mp4
    }
}