package com.example.aiadvent_1.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExternalMemoryRepository(
    private val dao: MemoryEntryDao,
    private val jsonStore: JsonMemoryStore
) {

    suspend fun append(record: MemoryRecord) {
        appendAll(listOf(record))
    }

    suspend fun appendAll(records: List<MemoryRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        dao.insertEntries(records.map { it.toEntity() })
        syncJson()
    }

    suspend fun getRecent(limit: Int): List<MemoryRecord> = withContext(Dispatchers.IO) {
        dao.getRecentEntries(limit).map { it.toRecord() }.reversed()
    }

    suspend fun getAll(): List<MemoryRecord> = withContext(Dispatchers.IO) {
        dao.getAllEntries().map { it.toRecord() }
    }

    fun observeAll(): Flow<List<MemoryRecord>> =
        dao.observeAllEntries().map { entities -> entities.map { it.toRecord() } }

    suspend fun clearMemory() = withContext(Dispatchers.IO) {
        dao.clearAll()
        jsonStore.clear()
    }

    suspend fun syncSnapshot() = withContext(Dispatchers.IO) {
        syncJson()
    }

    private suspend fun syncJson() {
        val all = dao.getAllEntries().map { it.toRecord() }
        jsonStore.overwrite(all)
    }
}

