package com.kclaw.gateway.rpc

import com.kclaw.gateway.config.ConfigManager
import com.kclaw.gateway.config.ConfigWriter
import com.kclaw.gateway.config.SecretsConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

/**
 * Data class representing setup status for the client.
 */
data class SetupStatusInfo(
    val setupRequired: Boolean,
    val onboardingPhase: String,
    val hasAnthropicKey: Boolean,
    val hasOpenaiKey: Boolean,
    val hasAuthToken: Boolean,
    val defaultModel: String
)

/**
 * Register RPC handlers for first-run onboarding setup.
 *
 * Methods:
 * - `setup.status` — returns current setup status and what's configured
 * - `setup.configure` — writes config values (token, API keys, model)
 * - `setup.complete` — marks onboarding complete (if requirements met)
 *
 * @param configManager provides current config and reload capability
 * @param configWriter writes updated config to disk
 * @param setupStatusProvider returns the current onboarding phase
 * @param markOnboardingComplete transitions workspace state to ONBOARDING_COMPLETE
 * @param apiKeyChecker checks if API keys are available (config or env vars)
 */
fun registerSetupHandlers(
    router: RpcRouter,
    configManager: ConfigManager,
    configWriter: ConfigWriter,
    setupStatusProvider: () -> String,
    markOnboardingComplete: () -> Unit,
    apiKeyChecker: (String) -> Boolean = { envVar ->
        System.getenv(envVar) != null
    }
) {

    // setup.status -- Return current setup state and what's configured
    router.register("setup.status") { _, _ ->
        logger.debug { "Handling setup.status" }
        val config = configManager.config.value
        val phase = setupStatusProvider()
        val hasAnthropicKey = !config.secrets.anthropicApiKey.isNullOrBlank() || apiKeyChecker("ANTHROPIC_API_KEY")
        val hasOpenaiKey = !config.secrets.openaiApiKey.isNullOrBlank() || apiKeyChecker("OPENAI_API_KEY")

        buildJsonObject {
            put("setupRequired", JsonPrimitive(phase != "ONBOARDING_COMPLETE"))
            put("onboardingPhase", JsonPrimitive(phase))
            put("hasAnthropicKey", JsonPrimitive(hasAnthropicKey))
            put("hasOpenaiKey", JsonPrimitive(hasOpenaiKey))
            put("hasAuthToken", JsonPrimitive(!config.gateway.auth.token.isNullOrBlank()))
            put("defaultModel", JsonPrimitive(config.agents.defaults.model))
        }
    }

    // setup.configure -- Write config values and reload
    router.register("setup.configure") { params, _ ->
        logger.debug { "Handling setup.configure" }
        val token = params["token"]?.jsonPrimitive?.contentOrNull
        val anthropicKey = params["anthropicKey"]?.jsonPrimitive?.contentOrNull
        val openaiKey = params["openaiKey"]?.jsonPrimitive?.contentOrNull
        val model = params["model"]?.jsonPrimitive?.contentOrNull

        if (token == null && anthropicKey == null && openaiKey == null && model == null) {
            throw IllegalArgumentException("At least one of: token, anthropicKey, openaiKey, model must be provided")
        }

        val currentConfig = configManager.config.value
        val updatedConfig = currentConfig.copy(
            gateway = if (token != null) {
                currentConfig.gateway.copy(
                    auth = currentConfig.gateway.auth.copy(token = token)
                )
            } else {
                currentConfig.gateway
            },
            agents = if (model != null) {
                currentConfig.agents.copy(
                    defaults = currentConfig.agents.defaults.copy(model = model)
                )
            } else {
                currentConfig.agents
            },
            secrets = currentConfig.secrets.copy(
                anthropicApiKey = anthropicKey ?: currentConfig.secrets.anthropicApiKey,
                openaiApiKey = openaiKey ?: currentConfig.secrets.openaiApiKey
            )
        )

        configWriter.write(updatedConfig)
        configManager.reload()
        logger.info { "Setup config updated and reloaded" }

        buildJsonObject {
            put("configured", JsonPrimitive(true))
            token?.let { put("tokenSet", JsonPrimitive(true)) }
            anthropicKey?.let { put("anthropicKeySet", JsonPrimitive(true)) }
            openaiKey?.let { put("openaiKeySet", JsonPrimitive(true)) }
            model?.let { put("modelSet", JsonPrimitive(model)) }
        }
    }

    // setup.complete -- Mark onboarding as complete (if requirements met)
    router.register("setup.complete") { _, _ ->
        logger.debug { "Handling setup.complete" }
        val config = configManager.config.value
        val hasAnthropicKey = !config.secrets.anthropicApiKey.isNullOrBlank() || apiKeyChecker("ANTHROPIC_API_KEY")
        val hasOpenaiKey = !config.secrets.openaiApiKey.isNullOrBlank() || apiKeyChecker("OPENAI_API_KEY")

        if (!hasAnthropicKey && !hasOpenaiKey) {
            throw IllegalArgumentException("At least one API key (Anthropic or OpenAI) must be configured before completing setup")
        }

        markOnboardingComplete()
        logger.info { "Onboarding marked complete" }

        buildJsonObject {
            put("completed", JsonPrimitive(true))
            put("onboardingPhase", JsonPrimitive("ONBOARDING_COMPLETE"))
        }
    }
}
