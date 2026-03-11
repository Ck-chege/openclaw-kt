package com.kclaw.agent.model

import com.kclaw.protocol.identifiers.ModelId

sealed class ModelRegistryError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class UnknownModel(val modelId: ModelId) :
        ModelRegistryError("Unknown model: ${modelId.raw}")

    class UnknownProvider(val provider: String) :
        ModelRegistryError("Unknown provider: $provider")

    class MissingApiKey(val provider: String, val envVar: String) :
        ModelRegistryError("Missing API key for provider '$provider': set environment variable $envVar")
}
