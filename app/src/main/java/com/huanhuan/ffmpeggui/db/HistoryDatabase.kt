package com.huanhuan.ffmpeggui.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

// 注解中声明实体类、版本号，以及是否导出模式
@Database(entities = [History::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {

    // 提供 DAO 的抽象方法
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        fun getInstance(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                                context.applicationContext,
                                HistoryDatabase::class.java,
                                "history_database" // 数据库文件名
                            ).fallbackToDestructiveMigration(false)  // 如果迁移有问题，重新创建数据库
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}