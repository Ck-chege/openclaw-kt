package com.openclaw.tools.memory

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Tool for reading a specific file from the workspace by path.
 * Validates the path exists and prevents directory traversal.
 */
object MemoryGetTool : SimpleTool<MemoryGetTool.Args>(
    argsSerializer = Args.serializer(),
    name = "memory_get",
    description = "Read the contents of a specific file from your workspace. " +
        "Use this after memory_search finds a relevant file that you want to read in full. " +
        "Path should be relative to the workspace root."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Relative path to the file within the workspace (e.g., 'docs/architecture.md')")
        val path: String,
        @property:LLMDescription("Maximum characters to return (default: 10000)")
        val maxChars: Int = 10_000
    )

    /** Set by Main.kt during initialization. */
    var workspacePath: Path = Path.of(".")

    override suspend fun execute(args: Args): String {
        if (args.path.isBlank()) {
            return "Error: File path cannot be empty."
        }

        // Prevent path traversal
        val normalizedPath = args.path.replace("\\", "/")
        if (normalizedPath.contains("..") || normalizedPath.startsWith("/")) {
            return "Error: Path traversal is not allowed. Use relative paths within the workspace."
        }

        val targetPath = workspacePath.resolve(normalizedPath).normalize()

        // Verify the resolved path is still within workspace
        if (!targetPath.startsWith(workspacePath)) {
            return "Error: Path traversal is not allowed. Use relative paths within the workspace."
        }

        if (!targetPath.exists()) {
            return "Error: File not found: ${args.path}"
        }

        if (targetPath.isDirectory()) {
            return "Error: '${args.path}' is a directory, not a file."
        }

        return try {
            val content = targetPath.readText()
            val effectiveMax = args.maxChars.coerceIn(100, 100_000)
            if (content.length > effectiveMax) {
                content.take(effectiveMax) + "\n\n... (truncated at $effectiveMax characters, total: ${content.length})"
            } else {
                content
            }
        } catch (e: Exception) {
            "Error reading file '${args.path}': ${e.message ?: e::class.simpleName}"
        }
    }
}
