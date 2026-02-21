package com.huanhuan.ffmpeggui.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(history: History) : Void

    @Update
    suspend fun update(history: History) : Void

    @Delete
    suspend fun delete(history: History) : Void

    //按时间查询
    @Query("SELECT * FROM history ORDER BY created_at DESC")
    fun getAllHistories(): Flow<List<History>>

}