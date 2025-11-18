package com.example.aiadvent_1.mcp

// JSON-RPC модели
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int? = null, // null для notifications (уведомлений)
    val method: String,
    val params: Map<String, Any>? = null
)

data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int?,
    val result: Any? = null,
    val error: JsonRpcError? = null
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

// MCP модели для инструментов
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: Map<String, Any>? = null
)

data class McpToolsList(
    val tools: List<McpTool>
)
