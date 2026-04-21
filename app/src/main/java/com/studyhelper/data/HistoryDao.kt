package com.studyhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertEntry(entry: HistoryEntry)

    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC")
    fun getAllEntries(): Flow<List<HistoryEntry>>

    @Delete
    suspend fun deleteEntry(entry: HistoryEntry)
}
