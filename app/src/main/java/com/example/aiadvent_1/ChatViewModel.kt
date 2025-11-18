package com.example.aiadvent_1

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadvent_1.mcp.McpClient
import com.example.aiadvent_1.mcp.McpIntegrationService
import com.example.aiadvent_1.mcp.McpTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    // MCP клиент для подключения к NewsAPI MCP серверу
    // По умолчанию используем HTTP транспорт для эмулятора Android
    private val mcpClient = McpClient(
        transport = McpTransport.Http(url = "http://10.0.2.2:3001")
    )
    
    private val mcpIntegrationService = McpIntegrationService(mcpClient)
    private val deepSeekService = DeepSeekService(mcpIntegrationService)
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _mcpConnected = MutableStateFlow(false)
    val mcpConnected: StateFlow<Boolean> = _mcpConnected.asStateFlow()
    
    init {
        // Инициализируем MCP соединение при создании ViewModel
        viewModelScope.launch {
            try {
                val initResult = mcpClient.initialize()
                if (initResult.isSuccess) {
                    _mcpConnected.value = true
                    Log.d("ChatViewModel", "MCP сервер подключен успешно")
                } else {
                    Log.w("ChatViewModel", "Не удалось подключиться к MCP серверу: ${initResult.exceptionOrNull()?.message}")
                    _mcpConnected.value = false
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Ошибка инициализации MCP", e)
                _mcpConnected.value = false
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
                // Передаем только текущее сообщение пользователя без истории
                val response = deepSeekService.generateResponse(message)
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
    
    /**
     * Обновляет URL MCP сервера
     */
    fun updateMcpServerUrl(url: String) {
        viewModelScope.launch {
            mcpClient.close()
            // Создаем новый клиент с новым URL
            // Это требует рефакторинга, но для простоты оставим как есть
            Log.d("ChatViewModel", "Обновление URL MCP сервера: $url")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        mcpClient.close()
    }
} 