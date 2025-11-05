package com.example.aiadvent_1

import com.google.gson.annotations.SerializedName

/**
 * Стандартный формат ответа от агента
 */
data class AgentResponse(
    @SerializedName("status")
    val status: String, // "success" или "error"
    
    @SerializedName("message")
    val message: String, // Основное текстовое сообщение
    
    @SerializedName("data")
    val data: Map<String, Any>? = null, // Дополнительные данные в виде объекта
    
    @SerializedName("error")
    val error: String? = null // Описание ошибки, если status = "error"
)

