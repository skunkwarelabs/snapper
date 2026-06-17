package com.skunk.snapper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Catch::class], version = 4, exportSchema = false)
abstract class SnapperDatabase : RoomDatabase() {
    abstract fun catchDao(): CatchDao

    companion object {
        @Volatile private var INSTANCE: SnapperDatabase? = null

        fun get(context: Context): SnapperDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SnapperDatabase::class.java,
                    "snapper.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
