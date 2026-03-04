package com.openclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Checks the runtime environment for binary availability, env vars, and OS.
 * Open for test subclassing with [FakeEnvironmentChecker].
 */
open class EnvironmentChecker {

    /**
     * Check if a binary is available on the system PATH.
     * Uses `which` on Unix, `where` on Windows.
     */
    open fun hasBinary(name: String): Boolean {
        val command = if (isWindows()) listOf("where", name) else listOf("which", name)
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            logger.debug(e) { "Binary check failed for: $name" }
            false
        }
    }

    /**
     * Check if an environment variable is set (non-null).
     */
    open fun hasEnvVar(name: String): Boolean {
        return System.getenv(name) != null
    }

    /**
     * Get the current OS identifier: "linux", "macos", or "windows".
     */
    open fun currentOs(): String {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") || osName.contains("darwin") -> "macos"
            osName.contains("win") -> "windows"
            osName.contains("linux") -> "linux"
            else -> osName
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")
}
