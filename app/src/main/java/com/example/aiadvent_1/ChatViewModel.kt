package com.example.aiadvent_1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadvent_1.memory.ExternalMemoryRepository
import com.example.aiadvent_1.memory.MemoryRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val deepSeekService: DeepSeekService,
    private val memoryRepository: ExternalMemoryRepository
) : ViewModel() {

    companion object {
        private const val MEMORY_CONTEXT_LIMIT = 12
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        restoreChatHistory()
    }

    private fun restoreChatHistory() {
        viewModelScope.launch {
            try {
                val history = memoryRepository.getAll().map {
                    ChatMessage(
                        text = it.content,
                        isFromUser = it.role == "user",
                        timestamp = it.timestamp
                    )
                }
                _messages.value = history
            } catch (e: Exception) {
                // Ignore restoration errors to avoid blocking UI
            }
        }
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return

        val userMessage = ChatMessage(message, true)
        _messages.value = _messages.value + userMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val userRecord = MemoryRecord(role = "user", content = message, timestamp = userMessage.timestamp)
                memoryRepository.append(userRecord)

                val context = memoryRepository.getRecent(MEMORY_CONTEXT_LIMIT)
                val response = deepSeekService.generateResponse(message, context)
                val aiMessage = ChatMessage(response.message, false)
                _messages.value = _messages.value + aiMessage

                val aiRecord = MemoryRecord(
                    role = "assistant",
                    content = aiMessage.text,
                    timestamp = aiMessage.timestamp,
                    metadata = response.metadata
                )
                memoryRepository.append(aiRecord)
            } catch (e: Exception) {
                val errorMessage = ChatMessage("Произошла ошибка: ${e.message}", false)
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        viewModelScope.launch {
            memoryRepository.clearMemory()
        }
    }
}