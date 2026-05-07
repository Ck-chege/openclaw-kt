package com.kclaw.gateway.config

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

/** JSON encoder that pretty-prints and includes all defaults so the full config is visible. */
private val writerJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Writes a [GatewayConfig] as JSON to the given file path.
 *
 * Writes all sections with their defaults so the full config structure is always
 * visible in the file. Null values (e.g. unconfigured secrets) are omitted.
 * The output is a valid JSON file that can be read back by [ConfigManager].
 */
class ConfigWriter(private val configJsonFilePath: Path) {

    /**
     * Write the given config to the config file, overwriting any existing content.
     * Includes all sections with defaults for discoverability.
     */
    fun write(config: GatewayConfig){
        configJsonFilePath.writeText(config.toJsonString())
        // Restrict file permissions when secrets are present (owner read/write only)
        if (config.secrets != GatewayConfig().secrets) {
            try {
                configJsonFilePath.setPosixFilePermissions(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                )
            } catch (_: UnsupportedOperationException) {
                // Non-POSIX filesystem (Windows) — skip permission restriction
            }
        }
        logger.info { "Config written to $configJsonFilePath" }
    }

    /**
     * Update specific fields in the current config without touching other values.
     * Reads the existing config, applies the [mutator], and writes back.
     */
    fun update(currentConfig: GatewayConfig, mutator: (GatewayConfig) -> GatewayConfig) {
        val updated = mutator(currentConfig)
        write(updated)
    }


}

fun GatewayConfig.toJsonString() = writerJson.encodeToString(GatewayConfig.serializer(), this)