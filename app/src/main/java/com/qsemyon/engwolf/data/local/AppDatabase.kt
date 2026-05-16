package com.qsemyon.engwolf.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WordEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            val db = Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "engwolf_db").build()
            INSTANCE = db
            db
        }
    }
}