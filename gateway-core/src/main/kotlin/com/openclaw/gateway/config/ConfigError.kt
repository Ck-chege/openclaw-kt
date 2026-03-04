package com.openclaw.gateway.config

import java.nio.file.Path

/**
 * Sealed exception hierarchy for configuration errors.
 * Provides structured error reporting with context about what went wrong.
 */
sealed class ConfigError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * The HOCON file could not be parsed — bad syntax, unresolved substitutions, or I/O error.
     */
    class FileParseError(
        val path: Path,
        cause: Throwable
    ) : ConfigError("Failed to parse config file '$path': ${cause.message}", cause)

    /**
     * A config value exists but has the wrong type or is outside valid range.
     */
    class InvalidValue(
        val configPath: String,
        val expected: String,
        cause: Throwable? = null
    ) : ConfigError("Invalid config value at '$configPath': expected $expected", cause)
}
