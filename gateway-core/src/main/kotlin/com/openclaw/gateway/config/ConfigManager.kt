package com.openclaw.gateway.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.exists

private val logger = KotlinLogging.logger {}

/**
 * Manages configuration loading, hot-reload via file watching, and reactive updates.
 *
 * Consumers observe [config] to receive configuration changes reactively.
 * Reads HOCON from [configPath], resolves `${ENV_VAR}` substitutions, and maps
 * to [GatewayConfig] data classes. Missing file → defaults. Invalid file → fail-fast on startup.
 */
class ConfigManager(private val configPath: Path) {
    private val _config = MutableStateFlow(GatewayConfig())
    val config: StateFlow<GatewayConfig> = _config.asStateFlow()

    private var watcherJob: Job? = null

    /**
     * Parse the HOCON config file at [configPath].
     * Returns defaults if the file does not exist.
     * Throws [ConfigError.FileParseError] on syntax/substitution errors.
     * Throws [ConfigError.InvalidValue] on type mismatches.
     */
    private fun parseConfigFile(): GatewayConfig {
        if (!configPath.exists()) {
            logger.info { "Config file not found at $configPath — using defaults" }
            return GatewayConfig()
        }

        return try {
            val parsed = ConfigFactory.parseFile(configPath.toFile())
            val resolved = parsed.resolve()
            resolved.toGatewayConfig()
        } catch (e: ConfigError) {
            throw e // already a structured error
        } catch (e: ConfigException) {
            throw ConfigError.FileParseError(configPath, e)
        }
    }

    /**
     * Load configuration from the HOCON file at [configPath].
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
    fun reload() {
        logger.info { "Reloading configuration from $configPath" }
        try {
            val reloaded = parseConfigFile()
            _config.value = reloaded
            logger.info { "Configuration reloaded successfully" }
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
