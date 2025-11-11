package com.example.aiadvent_1

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageRequest>,
    val max_tokens: Int? = null,
    val stop: List<String>? = null
)

data class ChatMessageRequest(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val choices: List<Choice>,
    val usage: Usage? = null
)

data class Usage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)

data class Choice(
    val message: ChatMessageResponse
)

data class ChatMessageResponse(
    val role: String? = null,
    val content: String
)

