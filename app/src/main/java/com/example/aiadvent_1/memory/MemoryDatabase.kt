package com.example.aiadvent_1.memory

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MemoryEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryEntryDao(): MemoryEntryDao
}

