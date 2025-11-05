package com.example.aiadvent_1

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val yandexGPTService = YandexGPTService()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * Отправляет обычное текстовое сообщение
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        val userMessage = ChatMessage(message, true)
        _messages.value = _messages.value + userMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = yandexGPTService.generateResponse(message)
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
    
    fun sendJsonMessage(message: String, systemPrompt: String? = null) {
        if (message.isBlank()) return

        _messages.value += ChatMessage(message, true)
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val jsonResponse = yandexGPTService.generateJsonResponse(message, systemPrompt)
                
                if (jsonResponse == null) {
                    _messages.value += ChatMessage("❌ Не удалось получить ответ от сервера", false)
                    return@launch
                }
                
                val response = yandexGPTService.parseAgentResponse(jsonResponse)
                
                val text = if (response != null) {
                    buildString {
                        append("${if (response.status == "success") "✅" else "❌"} ${response.message}\n\n")
                        response.data?.takeIf { it.isNotEmpty() }?.let { data ->
                            append("📊 Данные:\n")
                            data.forEach { (k, v) -> append("  • $k: $v\n") }
                        }
                        response.error?.let { append("⚠️ $it") }
                    }
                } else {
                    // Если не удалось распарсить, показываем исходный ответ с предупреждением
                    "⚠️ Не удалось обработать ответ в формате JSON:\n\n$jsonResponse"
                }
                
                _messages.value += ChatMessage(text, false)
            } catch (e: Exception) {
                _messages.value += ChatMessage("❌ Ошибка: ${e.message}", false)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearChat() {
        _messages.value = emptyList()
    }
} 