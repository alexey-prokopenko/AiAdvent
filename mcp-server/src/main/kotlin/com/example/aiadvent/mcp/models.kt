package com.example.aiadvent.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC модели
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int?,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * MCP модели
 */
@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: Map<String, kotlinx.serialization.json.JsonElement>? = null
)

@Serializable
data class McpToolsList(
    val tools: List<McpTool>
)

@Serializable
data class McpToolCallResult(
    val content: List<McpContent>,
    val isError: Boolean = false
)

@Serializable
data class McpContent(
    val type: String,
    val text: String
)

