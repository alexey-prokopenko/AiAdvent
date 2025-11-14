package com.example.aiadvent_1.memory

import com.google.gson.Gson

private val gson = Gson()

fun MemoryMetadata.toJson(): String = gson.toJson(this)

fun String.fromJson(): MemoryMetadata? = try {
    gson.fromJson(this, MemoryMetadata::class.java)
} catch (_: Exception) {
    null
}

