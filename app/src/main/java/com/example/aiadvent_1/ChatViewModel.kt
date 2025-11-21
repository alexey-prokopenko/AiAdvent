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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class ChatViewModel : ViewModel() {
    companion object {
        private const val REMINDER_TAG = "NewsReminder"
    }
    
    // MCP клиенты для подключения к NewsAPI и OpenWeatherMap MCP серверам
    // По умолчанию используем HTTP транспорт для эмулятора Android
    private val newsMcpClient = McpClient(
        transport = McpTransport.Http(url = "http://10.0.2.2:3001") // NewsAPI сервер
    )
    
    private val weatherMcpClient = McpClient(
        transport = McpTransport.Http(url = "http://10.0.2.2:3002") // OpenWeatherMap сервер
    )
    
    private val mcpIntegrationService = McpIntegrationService(
        mcpClients = listOf(newsMcpClient, weatherMcpClient)
    )
    private val deepSeekService = DeepSeekService(
        mcpIntegrationService,
        onReminderStarted = {
            Log.d(REMINDER_TAG, "Callback: Reminder запущен, начинаем периодическую проверку")
            startReminderPolling()
        },
        onIntermediateResponse = { intermediateResponse ->
            // Добавляем промежуточный ответ сразу в чат
            val intermediateMessage = ChatMessage(intermediateResponse, false)
            _messages.value = _messages.value + intermediateMessage
            Log.d("ChatViewModel", "Получен промежуточный ответ: ${intermediateResponse.take(100)}...")
        }
    )
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _mcpConnected = MutableStateFlow(false)
    val mcpConnected: StateFlow<Boolean> = _mcpConnected.asStateFlow()
    
    // Фоновая задача для автоматической проверки reminder
    private var reminderPollingJob: Job? = null
    private var isReminderActive = false
    private val reminderIntervalMs = 40_000L // 40 секунд
    
    init {
        // Инициализируем MCP соединения при создании ViewModel
        viewModelScope.launch {
            try {
                val initResult = mcpIntegrationService.initializeAll()
                if (initResult.isSuccess) {
                    _mcpConnected.value = true
                    Log.d("ChatViewModel", "MCP серверы подключены успешно")
                } else {
                    Log.w("ChatViewModel", "Не удалось подключиться к MCP серверам: ${initResult.exceptionOrNull()?.message}")
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
                
                // Добавляем финальный ответ только если он не пустой и отличается от последнего промежуточного
                // (промежуточные ответы уже добавлены через callback)
                if (response.isNotBlank()) {
                    val lastMessage = _messages.value.lastOrNull()
                    // Добавляем финальный ответ только если он отличается от последнего сообщения
                    if (lastMessage == null || lastMessage.text != response) {
                        val aiMessage = ChatMessage(response, false)
                        _messages.value = _messages.value + aiMessage
                    }
                } else {
                    // Если финальный ответ пустой (потому что все уже было отправлено как промежуточные),
                    // просто логируем это
                    Log.d("ChatViewModel", "Финальный ответ пустой - все результаты уже отправлены как промежуточные")
                }
                
                // Дополнительная проверка по тексту ответа (на случай, если callback не сработал)
                checkAndStartReminderPolling(response)
            } catch (e: Exception) {
                val errorMessage = ChatMessage("Произошла ошибка: ${e.message}", false)
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Проверяет ответ LLM и запускает периодическую проверку reminder, если он был запущен
     */
    private fun checkAndStartReminderPolling(response: String) {
        // Проверяем, содержит ли ответ информацию о запуске reminder
        val reminderStarted = response.contains("Reminder запущен", ignoreCase = true) ||
                             response.contains("reminder запущен", ignoreCase = true) ||
                             response.contains("будет собирать новости", ignoreCase = true) ||
                             response.contains("запущен", ignoreCase = true) && response.contains("reminder", ignoreCase = true)
        
        if (reminderStarted && !isReminderActive) {
            Log.d(REMINDER_TAG, "Обнаружен запуск reminder по тексту ответа")
            Log.d(REMINDER_TAG, "Обнаружен запуск reminder по тексту ответа, начинаем периодическую проверку")
            startReminderPolling()
        }
    }
    
    /**
     * Запускает фоновую задачу для периодической проверки reminder и отправки данных в LLM
     */
    private fun startReminderPolling() {
        if (isReminderActive) {
            return
        }
        
        isReminderActive = true
        reminderPollingJob?.cancel()
        
        reminderPollingJob = viewModelScope.launch {
            Log.d(REMINDER_TAG, "Запущена периодическая проверка reminder")
            
            delay(reminderIntervalMs)
            
            while (isActive && isReminderActive) {
                try {
                    val reminderResult = mcpIntegrationService.callTool(
                        "reminder",
                        """{"action": "get"}"""
                    )
                    
                    reminderResult.onSuccess { reminderData ->
                        // Проверяем, есть ли данные
                        if (reminderData.contains("нет данных", ignoreCase = true) ||
                            reminderData.contains("пока нет", ignoreCase = true) ||
                            reminderData.contains("запустите reminder", ignoreCase = true)) {
                            return@onSuccess
                        }
                        
                        // Ограничиваем размер данных для избежания ошибок "Content Exists Risk"
                        val limitedData = if (reminderData.length > 3000) {
                            reminderData.take(3000) + "\n\n[Данные обрезаны для безопасности]"
                        } else {
                            reminderData
                        }
                        
                        // Отправляем данные в LLM для создания summary
                        val summaryResponse = try {
                            deepSeekService.generateResponse(
                                "Создай summary на основе следующих данных новостей из reminder:\n\n$limitedData"
                            )
                        } catch (e: Exception) {
                            Log.e(REMINDER_TAG, "Ошибка при создании summary: ${e.message}", e)
                            if (e.message?.contains("Content Exists Risk", ignoreCase = true) == true ||
                                e.message?.contains("недопустимые данные", ignoreCase = true) == true) {
                                "⚠️ Не удалось создать summary из-за ограничений безопасности API. Попробуйте запросить новости позже или с другими параметрами."
                            } else {
                                "⚠️ Ошибка при создании summary: ${e.message}"
                            }
                        }
                        
                        // Добавляем summary в сообщения
                        val summaryMessage = ChatMessage(summaryResponse, false)
                        _messages.value = _messages.value + summaryMessage
                    }.onFailure { error ->
                        Log.e(REMINDER_TAG, "Ошибка при вызове reminder: ${error.message}", error)
                        if (error.message?.contains("остановлен", ignoreCase = true) == true) {
                            isReminderActive = false
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(REMINDER_TAG, "Исключение при проверке reminder: ${e.message}", e)
                }
                
                delay(reminderIntervalMs)
            }
        }
    }
    
    /**
     * Останавливает периодическую проверку reminder
     */
    private fun stopReminderPolling() {
        isReminderActive = false
        reminderPollingJob?.cancel()
        reminderPollingJob = null
    }
    
    fun clearChat() {
        _messages.value = emptyList()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopReminderPolling()
        mcpIntegrationService.closeAll()
    }
} 