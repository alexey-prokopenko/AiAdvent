package com.example.aiadvent_1

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isSummary: Boolean = false
) 