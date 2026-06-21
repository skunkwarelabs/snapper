package com.skunk.snapper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotDao {
    @Query("SELECT * FROM spots ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Spot>>

    @Insert
    suspend fun insert(item: Spot): Long

    @Update
    suspend fun update(item: Spot)

    @Delete
    suspend fun delete(item: Spot)
}
