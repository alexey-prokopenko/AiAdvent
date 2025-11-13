package com.example.aiadvent_1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

class ChatViewModel : ViewModel() {
    private val deepSeekService = DeepSeekService()
    private val compressionThreshold = 5 // Количество сообщений перед сжатием
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isCompressing = MutableStateFlow(false)
    val isCompressing: StateFlow<Boolean> = _isCompressing.asStateFlow()
    
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        val userMessage = ChatMessage(message, true)
        _messages.value = _messages.value + userMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Получаем историю для отправки в API (summary + последние сообщения)
                val conversationHistory = getConversationHistoryForApi()
                
                // Генерируем ответ с учетом истории
                val response = deepSeekService.generateResponse(message, conversationHistory)
                val aiMessage = ChatMessage(response, false)
                _messages.value = _messages.value + aiMessage
                
                // Проверяем, нужно ли сжимать историю
                checkAndCompressHistory()
            } catch (e: Exception) {
                val errorMessage = ChatMessage("Произошла ошибка: ${e.message}", false)
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Получает историю диалога для отправки в API.
     * Включает все summary и последние сообщения, которые еще не были сжаты.
     * Исключает последнее сообщение пользователя, так как оно передается отдельно.
     */
    private fun getConversationHistoryForApi(): List<ChatMessage> {
        val currentMessages = _messages.value
        
        // Если сообщений нет или только одно (текущее сообщение пользователя), возвращаем пустой список
        if (currentMessages.isEmpty()) {
            return emptyList()
        }
        
        val result = mutableListOf<ChatMessage>()
        
        // Находим последний summary (если есть)
        var lastSummaryIndex = -1
        for (i in currentMessages.indices.reversed()) {
            if (currentMessages[i].isSummary) {
                lastSummaryIndex = i
                break
            }
        }
        
        // Добавляем summary, если он есть
        if (lastSummaryIndex >= 0) {
            result.add(currentMessages[lastSummaryIndex])
        }
        
        // Добавляем все сообщения после последнего summary, кроме последнего (текущего сообщения пользователя)
        val startIndex = if (lastSummaryIndex >= 0) lastSummaryIndex + 1 else 0
        val endIndex = currentMessages.size - 1 // Исключаем последнее сообщение (текущее сообщение пользователя)
        
        if (endIndex >= startIndex) {
            result.addAll(currentMessages.subList(startIndex, endIndex))
        }
        
        return result
    }
    
    /**
     * Проверяет, нужно ли сжимать историю, и выполняет сжатие при необходимости.
     * Сжимает каждые 5 сообщений (не считая summary) после последнего summary.
     * Исключает последние 2 сообщения (текущее сообщение пользователя и ответ AI).
     */
    private suspend fun checkAndCompressHistory() {
        val currentMessages = _messages.value
        
        // Нужно минимум 2 сообщения (текущее сообщение пользователя и ответ AI) + compressionThreshold для сжатия
        if (currentMessages.size < compressionThreshold + 2) {
            return
        }
        
        // Находим последний summary (если есть)
        var lastSummaryIndex = -1
        for (i in currentMessages.indices.reversed()) {
            if (currentMessages[i].isSummary) {
                lastSummaryIndex = i
                break
            }
        }
        
        // Определяем диапазон сообщений для проверки (после последнего summary, но исключая последние 2)
        val startIndex = if (lastSummaryIndex >= 0) lastSummaryIndex + 1 else 0
        val endIndex = currentMessages.size - 2 // Исключаем последние 2 сообщения (текущее сообщение пользователя и ответ AI)
        
        if (endIndex <= startIndex) {
            return
        }
        
        val messagesAfterSummary = currentMessages.subList(startIndex, endIndex)
        
        // Подсчитываем количество "реальных" сообщений (не summary) после последнего summary
        val realMessages = messagesAfterSummary.filter { !it.isSummary }
        
        // Если реальных сообщений меньше порога, сжатие не требуется
        if (realMessages.size < compressionThreshold) {
            return
        }
        
        // Определяем, какие сообщения нужно сжать
        // Берем последние compressionThreshold сообщений, которые не являются summary
        val messagesToCompress = mutableListOf<ChatMessage>()
        var count = 0
        
        // Идем с конца messagesAfterSummary и собираем compressionThreshold сообщений (не summary)
        for (i in messagesAfterSummary.indices.reversed()) {
            if (!messagesAfterSummary[i].isSummary) {
                messagesToCompress.add(0, messagesAfterSummary[i]) // Добавляем в начало для сохранения порядка
                count++
                if (count >= compressionThreshold) {
                    break
                }
            }
        }
        
        // Если набрали достаточно сообщений для сжатия
        if (messagesToCompress.size >= compressionThreshold) {
            _isCompressing.value = true
            
            try {
                // Создаем summary
                val summaryText = deepSeekService.createSummary(messagesToCompress)
                val summaryMessage = ChatMessage(
                    text = "📝 Резюме предыдущего диалога: $summaryText",
                    isFromUser = false,
                    isSummary = true
                )
                
                // Заменяем сжатые сообщения на summary
                val newMessages = mutableListOf<ChatMessage>()
                
                // Добавляем все сообщения до тех, которые нужно сжать
                val firstCompressedIndex = currentMessages.indexOf(messagesToCompress.first())
                if (firstCompressedIndex > 0) {
                    newMessages.addAll(currentMessages.subList(0, firstCompressedIndex))
                }
                
                // Добавляем summary вместо сжатых сообщений
                newMessages.add(summaryMessage)
                
                // Добавляем оставшиеся сообщения после сжатых (включая последние 2 - текущее сообщение пользователя и ответ AI)
                val lastCompressedIndex = currentMessages.indexOf(messagesToCompress.last())
                if (lastCompressedIndex < currentMessages.size - 1) {
                    newMessages.addAll(currentMessages.subList(lastCompressedIndex + 1, currentMessages.size))
                }
                
                // Обновляем список сообщений
                _messages.value = newMessages
                
                Log.d("ChatViewModel", "История сжата: ${messagesToCompress.size} сообщений заменены на summary")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Ошибка при сжатии истории: ${e.message}")
                // В случае ошибки оставляем сообщения как есть
            } finally {
                _isCompressing.value = false
            }
        }
    }
    
    fun clearChat() {
        _messages.value = emptyList()
    }
} 