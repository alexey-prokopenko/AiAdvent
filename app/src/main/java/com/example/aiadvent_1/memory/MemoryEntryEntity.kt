package com.example.aiadvent_1.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_entries")
data class MemoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long,
    val metadataJson: String?
)

fun MemoryRecord.toEntity(): MemoryEntryEntity = MemoryEntryEntity(
    role = role,
    content = content,
    timestamp = timestamp,
    metadataJson = metadata?.toJson()
)

fun MemoryEntryEntity.toRecord(): MemoryRecord = MemoryRecord(
    role = role,
    content = content,
    timestamp = timestamp,
    metadata = metadataJson?.fromJson()
)

