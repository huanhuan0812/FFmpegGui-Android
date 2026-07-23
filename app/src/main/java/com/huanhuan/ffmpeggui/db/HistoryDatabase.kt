package com.huanhuan.ffmpeggui.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 注解中声明实体类、版本号，以及是否导出模式
@Database(entities = [History::class], version = 2, exportSchema = false)  // 版本号升级到2
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
                    "history_database"
                )
                    .addMigrations(MIGRATION_1_2)  // 添加迁移
                    .fallbackToDestructiveMigration(false)  // 如果迁移有问题，不重新创建数据库
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // 从版本1迁移到版本2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加新列
                db.execSQL("ALTER TABLE history ADD COLUMN input_path TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE history ADD COLUMN end_time INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}