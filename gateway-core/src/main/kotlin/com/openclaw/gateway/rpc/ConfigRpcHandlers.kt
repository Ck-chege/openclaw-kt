package com.openclaw.gateway.rpc

import com.openclaw.gateway.config.ConfigManager
import com.openclaw.gateway.config.GatewayConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

private val logger = KotlinLogging.logger {}

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Data class for model info passed from the model registry via lambda.
 */
data class ModelInfo(
    val id: String,
    val provider: String,
    val displayName: String,
)

/**
 * Register built-in RPC handlers for configuration, models, and channel status.
 *
 * @param modelLister returns all available models — injected to avoid coupling gateway-core to agent-engine.
 */
fun registerConfigHandlers(
    router: RpcRouter,
    configManager: ConfigManager,
    modelLister: () -> List<ModelInfo> = { emptyList() },
) {

    // config.get -- Return the current gateway configuration
    router.register("config.get") { _, _ ->
        logger.debug { "Handling config.get" }
        val currentConfig = configManager.config.value
        json.encodeToJsonElement<GatewayConfig>(currentConfig).jsonObject
    }

    // models.list -- Return available models from the registry
    router.register("models.list") { _, _ ->
        logger.debug { "Handling models.list" }
        val models = modelLister()
        buildJsonObject {
            put("models", JsonArray(models.map { model ->
                buildJsonObject {
                    put("id", JsonPrimitive(model.id))
                    put("provider", JsonPrimitive(model.provider))
                    put("name", JsonPrimitive(model.displayName))
                }
            }))
        }
    }

    // channels.status -- Return connected channel status (stub)
    router.register("channels.status") { _, _ ->
        logger.debug { "Handling channels.status" }
        buildJsonObject {
            put("channels", JsonArray(emptyList()))
            put("connected", JsonPrimitive(0))
        }
    }
}
