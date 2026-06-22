package com.skunk.snapper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CatchDao {
    @Query("SELECT * FROM catches ORDER BY caughtAt DESC")
    fun observeAll(): Flow<List<Catch>>

    @Insert
    suspend fun insert(item: Catch): Long

    @Update
    suspend fun update(item: Catch)

    @Delete
    suspend fun delete(item: Catch)
}
