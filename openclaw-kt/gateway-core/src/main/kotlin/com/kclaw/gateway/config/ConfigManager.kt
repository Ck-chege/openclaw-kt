package com.kclaw.gateway.config

import com.kclaw.gateway.AppPaths
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.exists
import kotlin.io.path.readText

private val logger = KotlinLogging.logger {}

/** JSON decoder configured for user-facing config files (comments + trailing commas). */
internal val configJson = Json {
    ignoreUnknownKeys = true
    allowComments = true
    allowTrailingComma = true
    isLenient = true
    encodeDefaults = false
    coerceInputValues = true
}

/** Returns dotted paths that changed between two configs. */
fun diffConfigPaths(prev: GatewayConfig, next: GatewayConfig): List<String> {
    val paths = mutableListOf<String>()
    if (prev.gateway.port != next.gateway.port)   paths += "gateway.port"
    if (prev.gateway.bind != next.gateway.bind)   paths += "gateway.bind"
    if (prev.gateway.auth != next.gateway.auth)   paths += "gateway.auth"
    if (prev.gateway.reload != next.gateway.reload) paths += "gateway.reload"
    if (prev.agents != next.agents)               paths += "agents"
    if (prev.skills != next.skills)               paths += "skills"
    if (prev.secrets != next.secrets)             paths += "secrets"
    if (prev.tools != next.tools)                 paths += "tools"
    if (prev.memory != next.memory)               paths += "memory"
    if (prev.cron != next.cron)                   paths += "cron"
    return paths
}

data class GatewayReloadPlan(
    val changedPaths: List<String>,
    val requiresRestart: Boolean,
    val restartReasons: List<String>
)

/** Paths that require a full gateway restart when changed. */
private val RESTART_REQUIRED_PATHS = setOf("gateway.port", "gateway.bind", "gateway.auth")

fun buildReloadPlan(changedPaths: List<String>): GatewayReloadPlan {
    val restartReasons = changedPaths.filter { it in RESTART_REQUIRED_PATHS }
    return GatewayReloadPlan(
        changedPaths = changedPaths,
        requiresRestart = restartReasons.isNotEmpty(),
        restartReasons = restartReasons
    )
}

/**
 * Manages configuration loading, hot-reload via file watching, and reactive updates.
 *
 * Consumers observe [config] to receive configuration changes reactively.
 * Reads JSON from [configPath] (with comments and trailing commas allowed),
 * deserializes to [GatewayConfig] data classes via kotlinx-serialization.
 * Missing file → defaults. Invalid file → fail-fast on startup.
 */


class ConfigManager(private val configPath: Path) {

    private val _config = MutableStateFlow(GatewayConfig())
    val config: StateFlow<GatewayConfig> = _config.asStateFlow()

    private var watcherJob: Job? = null

    /**
     * Parse the JSON config file at [configPath].
     * Returns defaults if the file does not exist or is blank.
     * Throws [ConfigError.FileParseError] on syntax errors.
     * Throws [ConfigError.InvalidValue] on type mismatches.
     */
    private fun parseConfigFile(): GatewayConfig {
        if (!configPath.exists()) {
            logger.info { "Config file not found at $configPath — using defaults" }
            val config = GatewayConfig()
            configPath.toFile().writeText(config.toJsonString())
            logger.info { "Config file written to $configPath" }
            return config
        }

        val text = configPath.readText().trim()
        if (text.isEmpty()) {
            logger.info { "Config file at $configPath is empty — using defaults" }
            return GatewayConfig()
        }

        return try {
            configJson.decodeFromString<GatewayConfig>(text)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw ConfigError.FileParseError(configPath, e)
        } catch (e: IllegalArgumentException) {
            throw ConfigError.FileParseError(configPath, e)
        }
    }

    /**
     * Load configuration from the JSON file at [configPath].
     * Updates the [config] StateFlow. Throws on error (fail-fast at startup).
     */
    fun load(): GatewayConfig {
        logger.info { "Loading configuration from $configPath" }
        val loaded = parseConfigFile()
        _config.value = loaded
        return loaded
    }

    /**
     * Re-read the configuration file and update the [config] StateFlow.
     * On parse errors, logs a warning and retains the previous configuration.
     */
    fun reload(onRestartRequired: ((GatewayReloadPlan) -> Unit)? = null) {
        val previous = _config.value
        try {
            val reloaded = parseConfigFile()
            val diff = diffConfigPaths(previous, reloaded)
            if (diff.isEmpty()) {
                logger.debug { "Config reload: no changes detected" }
                return
            }
            val plan = buildReloadPlan(diff)
            logger.info { "Config changed: ${diff.joinToString()}" }
            if (plan.requiresRestart) {
                logger.warn { "Config change requires restart: ${plan.restartReasons.joinToString()}" }
                onRestartRequired?.invoke(plan)
                if (onRestartRequired == null) {
                    // Still apply the config — caller handles restart externally
                    _config.value = reloaded
                }
            } else {
                _config.value = reloaded
                logger.info { "Config hot-reloaded successfully" }
            }
        } catch (e: ConfigError) {
            logger.warn(e) { "Failed to reload config — keeping previous configuration" }
        }
    }

    /**
     * Start watching the config file's parent directory for modifications.
     * Uses Java [java.nio.file.WatchService] with 300ms debounce.
     * Respects [ReloadMode] — does not watch if mode is OFF or RESTART.
     */
    fun startFileWatcher(scope: CoroutineScope) {
        val mode = _config.value.gateway.reload.mode
        if (mode == ReloadMode.OFF || mode == ReloadMode.RESTART) {
            logger.info { "File watcher disabled (reload.mode=$mode)" }
            return
        }

        // Cancel any existing watcher before starting a new one
        stopFileWatcher()

        val dir = configPath.parent
        if (dir == null || !dir.exists()) {
            logger.warn { "Cannot watch config directory — parent of $configPath does not exist" }
            return
        }

        watcherJob = scope.launch(Dispatchers.IO) {
            logger.info { "Starting config file watcher on $dir" }
            val watchService = FileSystems.getDefault().newWatchService()
            dir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE
            )

            try {
                while (isActive) {
                    val key = watchService.take() // blocks until event
                    var relevant = false
                    for (event in key.pollEvents()) {
                        val changed = event.context() as? Path
                        if (changed != null && dir.resolve(changed) == configPath) {
                            relevant = true
                        }
                    }
                    key.reset()

                    if (relevant) {
                        // Debounce: wait 300ms for editors that write multiple times
                        delay(300)
                        // Drain any events accumulated during debounce
                        watchService.poll()?.let { extraKey ->
                            extraKey.pollEvents()
                            extraKey.reset()
                        }
                        logger.info { "Config file change detected — reloading" }
                        reload()
                    }
                }
            } catch (_: CancellationException) {
                // Normal shutdown
            } finally {
                watchService.close()
                logger.info { "Config file watcher stopped" }
            }
        }
    }

    /**
     * Stop the file watcher coroutine.
     */
    fun stopFileWatcher() {
        watcherJob?.cancel()
        watcherJob = null
    }
}