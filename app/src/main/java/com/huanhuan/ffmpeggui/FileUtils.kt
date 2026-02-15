package com.huanhuan.ffmpeggui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

fun getPathFromUri(context: Context, uri: Uri): String? {
    return when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> {
            val fileName = getFileName(context, uri) ?: "temp_file"
            val file = File(context.cacheDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            file.absolutePath
        }
        ContentResolver.SCHEME_FILE -> {
            uri.path
        }
        else -> null
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
        if (cut != -1) {
            name = name?.substring(cut!! + 1)
        }
    }

    return name
}