package com.openclaw.protocol.dto

import kotlinx.serialization.Serializable

@Serializable
sealed class ToolPermission {
    @Serializable
    data object Allowed : ToolPermission()

    @Serializable
    data class Denied(val reason: String) : ToolPermission()

    @Serializable
    data class RequiresApproval(val prompt: String) : ToolPermission()
}
