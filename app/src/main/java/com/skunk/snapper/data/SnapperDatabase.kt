package com.skunk.snapper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Catch::class, Spot::class], version = 6, exportSchema = true)
abstract class SnapperDatabase : RoomDatabase() {
    abstract fun catchDao(): CatchDao
    abstract fun spotDao(): SpotDao

    companion object {
        @Volatile private var INSTANCE: SnapperDatabase? = null

        /** v5 adds the saved-spots table (favorite fishing spots); catches are untouched. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // DDL copied verbatim from Room's exported schema (app/schemas/.../5.json).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spots` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `autoName` TEXT NOT NULL, " +
                        "`lat` REAL NOT NULL, `lng` REAL NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v6 adds an optional photo to saved spots. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `spots` ADD COLUMN `photoPath` TEXT")
            }
        }

        fun get(context: Context): SnapperDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SnapperDatabase::class.java,
                    "snapper.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    // Backstop only for unforeseen version states; the migration above
                    // preserves catches on the normal v4 → v5 upgrade.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
