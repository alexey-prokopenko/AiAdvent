package com.example.aiadvent_1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val deepSeekService = DeepSeekService()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        val userMessage = ChatMessage(message, true)
        _messages.value = _messages.value + userMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Передаем всю историю сообщений (включая только что добавленное сообщение пользователя)
                val response = deepSeekService.generateResponse(_messages.value)
                val aiMessage = ChatMessage(response, false)
                _messages.value = _messages.value + aiMessage
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
    }
} 