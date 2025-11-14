package com.example.aiadvent_1.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MemoryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<MemoryEntryEntity>)

    @Query("SELECT * FROM memory_entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<MemoryEntryEntity>

    @Query("SELECT * FROM memory_entries ORDER BY timestamp ASC")
    suspend fun getAllEntries(): List<MemoryEntryEntity>

    @Query("SELECT * FROM memory_entries ORDER BY timestamp ASC")
    fun observeAllEntries(): Flow<List<MemoryEntryEntity>>

    @Query("DELETE FROM memory_entries")
    suspend fun clearAll()
}

