package com.openclaw.protocol.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class StopReason {
    @Serializable
    data class ToolUse(val calls: List<ToolCallRequest>) : StopReason()

    @Serializable
    data object EndTurn : StopReason()

    @Serializable
    data class Timeout(val elapsedMs: Long) : StopReason()

    @Serializable
    data class Error(val message: String, val code: String? = null) : StopReason()

    @Serializable
    data class Compaction(val tokensBefore: Int, val tokensAfter: Int) : StopReason()

    @Serializable
    data object MaxIterations : StopReason()
}

@Serializable
data class ToolCallRequest(
    val id: String,
    val name: String,
    val args: JsonObject
)
