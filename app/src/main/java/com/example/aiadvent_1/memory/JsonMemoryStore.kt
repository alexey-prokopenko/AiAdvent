package com.example.aiadvent_1.memory

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class JsonMemoryStore(context: Context) {

    private val gson = Gson()
    private val memoryFile: File = File(context.filesDir, MEMORY_FILE_NAME)

    suspend fun overwrite(entries: List<MemoryRecord>) = withContext(Dispatchers.IO) {
        ensureParentExists()
        memoryFile.writeText(gson.toJson(entries))
    }

    suspend fun readAll(): List<MemoryRecord> = withContext(Dispatchers.IO) {
        if (!memoryFile.exists()) return@withContext emptyList()
        return@withContext try {
            val type = object : TypeToken<List<MemoryRecord>>() {}.type
            gson.fromJson(memoryFile.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (memoryFile.exists()) {
            memoryFile.delete()
        }
    }

    private fun ensureParentExists() {
        memoryFile.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
    }

    companion object {
        private const val MEMORY_FILE_NAME = "chat_memory.json"
    }
}

