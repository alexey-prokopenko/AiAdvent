package com.example.aiadvent_1

import android.app.Application
import androidx.room.Room
import com.example.aiadvent_1.memory.ExternalMemoryRepository
import com.example.aiadvent_1.memory.JsonMemoryStore
import com.example.aiadvent_1.memory.MemoryDatabase

class AiAdventApp : Application() {

    val database: MemoryDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            MemoryDatabase::class.java,
            "chat_memory.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val externalMemoryRepository: ExternalMemoryRepository by lazy {
        ExternalMemoryRepository(
            database.memoryEntryDao(),
            JsonMemoryStore(applicationContext)
        )
    }

    val deepSeekService: DeepSeekService by lazy { DeepSeekService() }
}

