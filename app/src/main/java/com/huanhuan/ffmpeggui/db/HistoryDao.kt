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
    suspend fun insert(history: History) : Unit

    @Update
    suspend fun update(history: History) : Unit

    @Delete
    suspend fun delete(history: History) : Unit

    //按时间查询
    @Query("SELECT * FROM history ORDER BY created_at DESC")
    fun getAllHistories(): Flow<List<History>>

    @Query("DELETE FROM history")
    suspend fun deleteAll() : Unit

}