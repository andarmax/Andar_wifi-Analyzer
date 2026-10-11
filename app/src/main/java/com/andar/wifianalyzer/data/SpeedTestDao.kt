package com.andar.wifianalyzer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestDao {
    @Query("SELECT * FROM speed_test_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<SpeedTestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SpeedTestEntity): Long

    @Query("DELETE FROM speed_test_history")
    suspend fun clearHistory()

    suspend fun clearAll() = clearHistory()
}
