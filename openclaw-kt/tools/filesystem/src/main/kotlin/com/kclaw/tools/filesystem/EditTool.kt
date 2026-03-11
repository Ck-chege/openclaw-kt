package com.kclaw.tools.filesystem

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.kclaw.tools.WorkspaceBoundary
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object EditTool : SimpleTool<EditTool.Args>(
    argsSerializer = Args.serializer(),
    name = "edit",
    description = "Make a surgical text replacement in a file. Finds the first occurrence of old_string and replaces it with new_string. The old_string must match exactly (including whitespace and indentation)."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Path to the file to edit")
        val path: String,
        @property:LLMDescription("The exact text to find and replace (must match exactly)")
        val old_string: String,
        @property:LLMDescription("The replacement text")
        val new_string: String
    )

    var workspacePath: Path = Path.of(".")
    var enforceBoundary: Boolean = true

    override suspend fun execute(args: Args): String {
        if (args.old_string.isEmpty()) return "Error: old_string cannot be empty"

        val resolved = if (enforceBoundary) {
            try {
                WorkspaceBoundary.resolve(args.path, workspacePath)
            } catch (e: WorkspaceBoundary.WorkspaceBoundaryViolation) {
                return "Error: ${e.message}"
            }
        } else {
            val p = Path.of(args.path)
            if (p.isAbsolute) p.normalize() else workspacePath.resolve(args.path).normalize()
        }

        val file = resolved.toFile()
        if (!file.exists()) return "Error: File not found: ${args.path}"

        val content = file.readText()
        // Literal string match (not regex)
        val index = content.indexOf(args.old_string)
        if (index == -1) {
            return "Error: old_string not found in ${args.path}"
        }

        val newContent = content.substring(0, index) +
            args.new_string +
            content.substring(index + args.old_string.length)

        // Atomic write
        val tmpFile = resolved.resolveSibling("${resolved.fileName}.tmp")
        try {
            Files.writeString(tmpFile, newContent)
            Files.move(tmpFile, resolved, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.deleteIfExists(tmpFile)
            return "Error writing file: ${e.message}"
        }

        return "Edited ${args.path}: replaced ${args.old_string.length} chars with ${args.new_string.length} chars"
    }
}
