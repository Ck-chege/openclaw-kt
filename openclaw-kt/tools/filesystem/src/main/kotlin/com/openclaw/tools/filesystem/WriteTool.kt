package com.openclaw.tools.filesystem

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.openclaw.tools.WorkspaceBoundary
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object WriteTool : SimpleTool<WriteTool.Args>(
    argsSerializer = Args.serializer(),
    name = "write",
    description = "Write content to a file. Creates the file if it doesn't exist, overwrites if it does. Only writes within the workspace."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Absolute or workspace-relative path for the file")
        val path: String,
        @property:LLMDescription("The content to write to the file")
        val content: String
    )

    var workspacePath: Path = Path.of(".")

    override suspend fun execute(args: Args): String {
        val resolved = try {
            WorkspaceBoundary.resolve(args.path, workspacePath)
        } catch (e: WorkspaceBoundary.WorkspaceBoundaryViolation) {
            return "Error: ${e.message}"
        }

        // Create parent dirs
        resolved.parent?.let { Files.createDirectories(it) }

        // Atomic write: write to .tmp then move
        val tmpFile = resolved.resolveSibling("${resolved.fileName}.tmp")
        try {
            Files.writeString(tmpFile, args.content)
            Files.move(tmpFile, resolved, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.deleteIfExists(tmpFile)
            return "Error writing file: ${e.message}"
        }

        val size = Files.size(resolved)
        return "Wrote ${size} bytes to ${args.path}"
    }
}
