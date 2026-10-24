package com.kirox.app.core.demo

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DemoAccount::class, DemoContact::class, DemoChat::class, DemoMessage::class, DemoReaction::class, DemoCall::class, DemoMeta::class],
    version = 1,
    exportSchema = false
)
abstract class DemoDatabase : RoomDatabase() {
    abstract fun demoDao(): DemoDao

    companion object {
        @Volatile private var instance: DemoDatabase? = null
        fun get(context: Context): DemoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DemoDatabase::class.java, "kirox_demo.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build().also { instance = it }
        }
    }
}
