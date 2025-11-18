package com.example.aiadvent_1

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageRequest>,
    val max_tokens: Int? = null,
    val stop: List<String>? = null,
    val tools: List<Tool>? = null,
    val tool_choice: String? = null // "auto", "none", or specific tool
)

data class ChatMessageRequest(
    val role: String,
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null
)

data class Tool(
    val type: String = "function",
    val function: FunctionDefinition
)

data class FunctionDefinition(
    val name: String,
    val description: String? = null,
    val parameters: Map<String, Any>? = null
)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

data class FunctionCall(
    val name: String,
    val arguments: String // JSON string
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
    val message: ChatMessageResponse,
    val finish_reason: String? = null
)

data class ChatMessageResponse(
    val role: String? = null,
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null
)

