package com.huanhuan.ffmpeggui.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class History(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "path")
    val path: String,
    @ColumnInfo(name = "input_path")  // 新增：输入文件路径
    val inputPath: String = "",        // 默认值为空字符串
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "end_time")     // 新增：结束时间
    val endTime: Long = 0L,            // 默认值为0
    @ColumnInfo(name = "size")
    val size: Int = 0
)