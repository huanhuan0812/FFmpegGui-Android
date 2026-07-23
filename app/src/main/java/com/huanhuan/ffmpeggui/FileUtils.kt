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
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

private const val TAG = "FileUtils"

/**
 * 从 Uri 获取文件路径，优先返回真实路径，避免不必要的缓存复制
 *
 * @return 文件路径，如果无法获取则返回 null
 */
fun getPathFromUri(context: Context, uri: Uri): String? {
    return when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> {
            // 优先尝试获取真实路径
            val realPath = getRealPathFromContentUri(context, uri)
            if (realPath != null) {
                Log.d(TAG, "✅ 获取到真实路径: $realPath")
                return realPath
            }

            // 如果无法获取真实路径，且文件来自 MediaStore，尝试直接通过 ID 访问
            val mediaPath = getPathFromMediaStore(context, uri)
            if (mediaPath != null) {
                Log.d(TAG, "✅ 从 MediaStore 获取路径: $mediaPath")
                return mediaPath
            }

            // 最后才回退到缓存复制
            Log.d(TAG, "⚠️ 无法获取真实路径，回退到缓存复制")
            copyToCache(context, uri)
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

/**
 * 从 Content URI 获取真实文件路径（适用于 MediaStore 中的文件）
 */
private fun getRealPathFromContentUri(context: Context, uri: Uri): String? {
    // 检查是否是 MediaStore 的 URI
    val isMediaStoreUri = uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()) ||
            uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()) ||
            uri.toString().startsWith(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()) ||
            uri.toString().startsWith(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL).toString())

    if (!isMediaStoreUri) {
        return null
    }

    return try {
        // 方法1：通过 DATA 字段获取（已废弃但仍有兼容性）
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            if (cursor.moveToFirst()) {
                val path = cursor.getString(dataIndex)
                if (path != null && File(path).exists()) {
                    return path
                }
            }
        }

        // 方法2：通过 ID 和外部存储路径组合
        val id = ContentUris.parseId(uri)
        if (id > 0) {
            val filePath = getFilePathFromId(context, uri, id)
            if (filePath != null && File(filePath).exists()) {
                return filePath
            }
        }

        null
    } catch (e: Exception) {
        Log.d(TAG, "获取真实路径失败: ${e.message}")
        null
    }
}

/**
 * 通过 MediaStore ID 获取文件路径
 */
private fun getFilePathFromId(context: Context, uri: Uri, id: Long): String? {
    val collection = when {
        uri.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()) -> {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        uri.toString().contains(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()) -> {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        uri.toString().contains(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()) -> {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        else -> {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
    }

    val queryUri = ContentUris.withAppendedId(collection, id)
    val projection = arrayOf(MediaStore.MediaColumns.DATA)

    context.contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            return cursor.getString(dataIndex)
        }
    }

    return null
}

/**
 * 从 MediaStore 获取文件路径（通用方法）
 */
private fun getPathFromMediaStore(context: Context, uri: Uri): String? {
    // 尝试通过 ID 查询
    try {
        val id = ContentUris.parseId(uri)
        if (id > 0) {
            val filePath = getFilePathFromId(context, uri, id)
            if (filePath != null && File(filePath).exists()) {
                return filePath
            }
        }
    } catch ( _ : Exception) {
        // parseId 可能失败，继续其他方法
    }

    // 尝试通过文件名和大小匹配
    val fileName = getFileName(context, uri)
    val fileSize = getFileSizeFromUri(context, uri)

    if (fileName != null && fileSize > 0) {
        val path = findFileByAttributes(context, fileName, fileSize)
        if (path != null) {
            return path
        }
    }

    return null
}

/**
 * 通过文件名和大小在 MediaStore 中查找文件
 */
private fun findFileByAttributes(context: Context, fileName: String, fileSize: Long): String? {
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
    val selectionArgs = arrayOf(fileName, fileSize.toString())
    val projection = arrayOf(MediaStore.MediaColumns.DATA)

    context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val path = cursor.getString(dataIndex)
            if (path != null && File(path).exists()) {
                return path
            }
        }
    }

    return null
}

/**
 * 获取文件大小（通过 ContentResolver）
 */
private fun getFileSizeFromUri(context: Context, uri: Uri): Long {
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            pfd.statSize
        } ?: -1
    } catch ( _ : Exception) {
        -1
    }
}

/**
 * 复制到缓存目录（作为最后的回退方案）
 */
private fun copyToCache(context: Context, uri: Uri): String? {
    return try {
        val fileName = getFileName(context, uri) ?: "temp_${System.currentTimeMillis()}.tmp"
        val finalFileName = ensureFileExtension(context, uri, fileName)
        val cacheFile = File(context.cacheDir, finalFileName)

        // 删除已存在的旧文件
        if (cacheFile.exists()) {
            cacheFile.delete()
        }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(cacheFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
            }
        } ?: return null

        if (cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "✅ 缓存文件创建成功: ${cacheFile.absolutePath}, 大小: ${cacheFile.length()} bytes")
            return cacheFile.absolutePath
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "复制文件到缓存失败", e)
        null
    }
}

/**
 * 获取文件名
 */
fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null

    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex)
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

/**
 * 确保文件有正确的扩展名
 */
private fun ensureFileExtension(context: Context, uri: Uri, fileName: String): String {
    if (fileName.contains('.')) {
        return fileName
    }

    val mimeType = context.contentResolver.getType(uri)
    Log.d(TAG, "MIME类型: $mimeType")

    if (mimeType != null) {
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (extension != null) {
            return "$fileName.$extension"
        }
    }

    return when {
        mimeType?.startsWith("video/") == true -> "$fileName.mp4"
        mimeType?.startsWith("audio/") == true -> "$fileName.mp3"
        mimeType?.startsWith("image/") == true -> "$fileName.jpg"
        else -> "$fileName.mp4"
    }
}

// ============================================================
// 文件存在性检查和删除功能（兼容 Android 11+）
// ============================================================

/**
 * 检查文件是否存在
 */
fun fileExistsCompat(context: Context, filePath: String): Boolean {
    if (filePath.isEmpty()) return false

    return try {
        val file = File(filePath)
        if (file.exists()) return true

        if (filePath.startsWith("content://")) {
            val uri = filePath.toUri()
            context.contentResolver.openInputStream(uri)?.use { it.close() }
            return true
        }

        val uriFromPath = getFileUriFromPath(context, filePath)
        if (uriFromPath != null) {
            context.contentResolver.openInputStream(uriFromPath)?.use { it.close() }
            return true
        }

        false
    } catch (e: Exception) {
        Log.e(TAG, "检查文件存在性异常", e)
        false
    }
}

/**
 * 从文件路径获取 Uri
 */
fun getFileUriFromPath(context: Context, filePath: String): Uri? {
    if (filePath.isEmpty()) return null

    return try {
        if (filePath.startsWith("content://")) {
            return filePath.toUri()
        }

        val file = File(filePath)
        val fileName = file.name

        if (filePath.startsWith(Environment.getExternalStorageDirectory().absolutePath)) {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

            // 方法1：通过 DATA 字段查询
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)

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

            // 方法2：通过 RELATIVE_PATH 查询（Android 10+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            }
        }

        null
    } catch (e: Exception) {
        Log.e(TAG, "获取文件 Uri 失败", e)
        null
    }
}

/**
 * 删除文件
 */
fun deleteFileCompat(context: Context, filePath: String): Boolean {
    if (filePath.isEmpty()) return false

    Log.d(TAG, "尝试删除文件: $filePath")

    return try {
        val file = File(filePath)
        if (file.exists() && file.delete()) {
            Log.d(TAG, "✅ 文件删除成功: $filePath")
            return true
        }

        if (filePath.startsWith("content://")) {
            val uri = filePath.toUri()
            if (context.contentResolver.delete(uri, null, null) > 0) {
                Log.d(TAG, "✅ 文件删除成功 (ContentResolver): $filePath")
                return true
            }
        }

        val uriFromPath = getFileUriFromPath(context, filePath)
        if (uriFromPath != null && context.contentResolver.delete(uriFromPath, null, null) > 0) {
            Log.d(TAG, "✅ 文件删除成功 (MediaStore): $filePath")
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val docFile = DocumentFile.fromFile(file)
            if (docFile.exists() && docFile.delete()) {
                Log.d(TAG, "✅ 文件删除成功 (DocumentFile): $filePath")
                return true
            }
        }

        Log.w(TAG, "❌ 文件删除失败: $filePath")
        false
    } catch (e: Exception) {
        Log.e(TAG, "删除文件异常", e)
        false
    }
}

/**
 * 批量删除文件
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
 * 检查文件是否可读
 */
fun isFileReadableCompat(context: Context, filePath: String): Boolean {
    if (filePath.isEmpty()) return false

    return try {
        val file = File(filePath)
        if (file.exists() && file.canRead()) return true

        val uri = getFileUriFromPath(context, filePath)
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { it.close() }
            return true
        }

        false
    } catch (_: Exception) {
        false
    }
}

/**
 * 获取文件大小
 */
fun getFileSizeCompat(context: Context, filePath: String): Long {
    if (filePath.isEmpty()) return -1

    return try {
        val file = File(filePath)
        if (file.exists()) return file.length()

        val uri = getFileUriFromPath(context, filePath)
        if (uri != null) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                return pfd.statSize
            }
        }

        -1
    } catch (_: Exception) {
        -1
    }
}