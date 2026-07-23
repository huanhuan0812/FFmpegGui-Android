package com.huanhuan.ffmpeggui

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri

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
            name = name.substring(cut + 1)
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

// ============================================================
// 新增：文件存在性检查和删除功能（兼容 Android 11+）
// ============================================================

/**
 * 检查文件是否存在（兼容 Android 11+ Scoped Storage）
 *
 * @param context Context
 * @param filePath 文件路径
 * @return 文件是否存在
 */
fun fileExistsCompat(context: Context, filePath: String): Boolean {
    if (filePath.isEmpty()) {
        return false
    }

    return try {
        // 方法1：尝试直接访问（适用于 Android 10 及以下，或应用私有目录）
        val file = File(filePath)
        if (file.exists()) {
            Log.d(TAG, "✅ 文件存在 (直接访问): $filePath")
            return true
        }

        // 方法2：如果是 content:// URI，尝试通过 ContentResolver 访问
        if (filePath.startsWith("content://")) {
            val uri = filePath.toUri()
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    it.close()
                    Log.d(TAG, "✅ 文件存在 (ContentResolver): $filePath")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "ContentResolver 访问失败: ${e.message}")
            }
        }

        // 方法3：尝试通过 MediaStore 查询
        val uriFromPath = getFileUriFromPath(context, filePath)
        if (uriFromPath != null) {
            try {
                context.contentResolver.openInputStream(uriFromPath)?.use {
                    it.close()
                    Log.d(TAG, "✅ 文件存在 (MediaStore): $filePath")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "MediaStore 访问失败: ${e.message}")
            }
        }

        // 方法4：尝试通过 DocumentFile 访问（适用于 Android/data 目录）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val docFile = DocumentFile.fromFile(file)
                if (docFile.exists()) {
                    Log.d(TAG, "✅ 文件存在 (DocumentFile): $filePath")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "DocumentFile 访问失败: ${e.message}")
            }
        }

        // 方法5：尝试读取文件长度（某些情况下可以绕过权限检查）
        try {
            if (file.length() > 0) {
                Log.d(TAG, "✅ 文件存在 (文件长度检查): $filePath, 大小: ${file.length()}")
                return true
            }
        } catch ( _ : Exception) {
            // 忽略
        }

        Log.d(TAG, "❌ 文件不存在: $filePath")
        false
    } catch (e: Exception) {
        Log.e(TAG, "检查文件存在性异常: $filePath", e)
        false
    }
}

/**
 * 从文件路径获取 Uri（通过 MediaStore）
 */
fun getFileUriFromPath(context: Context, filePath: String): Uri? {
    if (filePath.isEmpty()) {
        return null
    }

    return try {
        // 如果已经是 content URI
        if (filePath.startsWith("content://")) {
            return filePath.toUri()
        }

        val file = File(filePath)
        val fileName = file.name

        // 如果是外部存储文件
        if (filePath.startsWith(Environment.getExternalStorageDirectory().absolutePath)) {
            // 查询 MediaStore
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

            // 方法1：通过 DATA 字段查询（已废弃但仍有兼容性）
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)

            try {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val id = cursor.getLong(idColumn)
                        return ContentUris.withAppendedId(collection, id)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "通过 DATA 字段查询失败: ${e.message}")
            }

            // 方法2：通过相对路径查询（Android 10+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val relativePath = file.absolutePath.substringAfter(Environment.getExternalStorageDirectory().absolutePath)
                    val selection2 = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                    val selectionArgs2 = arrayOf(relativePath, fileName)

                    context.contentResolver.query(
                        collection,
                        arrayOf(MediaStore.MediaColumns._ID),
                        selection2,
                        selectionArgs2,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                            val id = cursor.getLong(idColumn)
                            return ContentUris.withAppendedId(collection, id)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "通过 RELATIVE_PATH 查询失败: ${e.message}")
                }
            }
        }

        null
    } catch (e: Exception) {
        Log.e(TAG, "获取文件 Uri 失败: $filePath", e)
        null
    }
}

/**
 * 删除文件（兼容 Android 11+ Scoped Storage）
 *
 * @param context Context
 * @param filePath 文件路径
 * @return 是否删除成功
 */
fun deleteFileCompat(context: Context, filePath: String): Boolean {
    if (filePath.isEmpty()) {
        return false
    }

    Log.d(TAG, "尝试删除文件: $filePath")

    return try {
        // 方法1：直接删除（适用于应用私有目录或 Android 10 以下）
        val file = File(filePath)
        if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "✅ 文件删除成功 (直接删除): $filePath")
                return true
            }
            Log.d(TAG, "直接删除失败，尝试其他方法")
        }

        // 方法2：如果是 content:// URI，尝试通过 ContentResolver 删除
        if (filePath.startsWith("content://")) {
            val uri = filePath.toUri()
            try {
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    Log.d(TAG, "✅ 文件删除成功 (ContentResolver): $filePath")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "ContentResolver 删除失败: ${e.message}")
            }
        }

        // 方法3：尝试通过 MediaStore 删除
        val uriFromPath = getFileUriFromPath(context, filePath)
        if (uriFromPath != null) {
            try {
                val deleted = context.contentResolver.delete(uriFromPath, null, null)
                if (deleted > 0) {
                    Log.d(TAG, "✅ 文件删除成功 (MediaStore): $filePath")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "MediaStore 删除失败: ${e.message}")
            }
        }

        // 方法4：尝试通过 DocumentFile 删除（适用于 Android/data 目录）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val docFile = DocumentFile.fromFile(file)
                if (docFile.exists()) {
                    val deleted = docFile.delete()
                    if (deleted) {
                        Log.d(TAG, "✅ 文件删除成功 (DocumentFile): $filePath")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "DocumentFile 删除失败: ${e.message}")
            }
        }

        // 方法5：尝试清空文件内容后删除
        try {
            val fos = FileOutputStream(file, false)
            fos.close()
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "✅ 文件删除成功 (清空后删除): $filePath")
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "清空后删除失败: ${e.message}")
        }

        Log.w(TAG, "❌ 文件删除失败: $filePath")
        false
    } catch (e: Exception) {
        Log.e(TAG, "删除文件异常: $filePath", e)
        false
    }
}

/**
 * 批量删除文件
 *
 * @param context Context
 * @param filePaths 文件路径列表
 * @return 成功删除的数量
 */
fun deleteFilesCompat(context: Context, filePaths: List<String>): Int {
    var successCount = 0
    filePaths.forEach { filePath ->
        if (deleteFileCompat(context, filePath)) {
            successCount++
        }
    }
    Log.d(TAG, "批量删除完成: 成功 $successCount/${filePaths.size}")
    return successCount
}

/**
 * 检查文件是否可读（兼容 Android 11+）
 */
fun isFileReadableCompat(context: Context, filePath: String): Boolean {
    if (filePath.isEmpty()) {
        return false
    }

    return try {
        // 方法1：直接检查
        val file = File(filePath)
        if (file.exists() && file.canRead()) {
            return true
        }

        // 方法2：尝试通过 ContentResolver 读取
        val uri = getFileUriFromPath(context, filePath)
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    it.close()
                    return true
                }
            } catch ( _ : Exception) {
                // 忽略
            }
        }

        false
    } catch ( _ : Exception) {
        false
    }
}

/**
 * 获取文件大小（兼容 Android 11+）
 *
 * @return 文件大小（字节），-1 表示获取失败
 */
fun getFileSizeCompat(context: Context, filePath: String): Long {
    if (filePath.isEmpty()) {
        return -1
    }

    return try {
        // 方法1：直接获取
        val file = File(filePath)
        if (file.exists()) {
            return file.length()
        }

        // 方法2：通过 ContentResolver 获取
        val uri = getFileUriFromPath(context, filePath)
        if (uri != null) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.let {
                    val size = it.statSize
                    it.close()
                    return size
                }
            } catch ( _ : Exception) {
                // 忽略
            }
        }

        -1
    } catch ( _ : Exception) {
        -1
    }
}