package com.openclaw.agent.model

import com.openclaw.protocol.identifiers.ModelId
import kotlinx.serialization.Serializable

@Serializable
data class ModelDefinition(
    val id: ModelId,
    val provider: String,
    val modelName: String,
    val displayName: String,
    val maxContextTokens: Int = 200_000
)
