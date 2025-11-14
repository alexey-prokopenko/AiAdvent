package com.example.aiadvent_1.memory

data class MemoryRecord(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: MemoryMetadata? = null
)

data class MemoryMetadata(
    val responseTimeMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

