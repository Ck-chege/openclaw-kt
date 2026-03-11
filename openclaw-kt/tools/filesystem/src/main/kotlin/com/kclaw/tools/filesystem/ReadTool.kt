package com.kclaw.tools.filesystem

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.kclaw.tools.WorkspaceBoundary
import kotlinx.serialization.Serializable
import java.nio.file.Path

object ReadTool : SimpleTool<ReadTool.Args>(
    argsSerializer = Args.serializer(),
    name = "read",
    description = "Read the contents of a file. Returns text with line numbers. Use offset/limit for large files."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Absolute or workspace-relative path to the file")
        val path: String,
        @property:LLMDescription("Line number to start reading from (1-based, default: 1)")
        val offset: Int? = null,
        @property:LLMDescription("Maximum number of lines to read (default: reads until ~50KB)")
        val limit: Int? = null
    )

    var workspacePath: Path = Path.of(".")
    var enforceBoundary: Boolean = true
    var maxBytes: Int = 50_000

    override suspend fun execute(args: Args): String {
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
        if (file.isDirectory) return "Error: Path is a directory, not a file: ${args.path}"

        // Binary detection: check first 8KB for null bytes
        val isBinary = file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            val read = stream.read(buf)
            if (read > 0) buf.take(read).any { it == 0.toByte() } else false
        }
        if (isBinary) return "Error: File appears to be binary: ${args.path}"

        val allLines = file.readLines()
        val startLine = (args.offset ?: 1).coerceAtLeast(1)
        val startIndex = startLine - 1

        if (startIndex >= allLines.size) {
            return "Error: offset $startLine is beyond file length (${allLines.size} lines)"
        }

        // Adaptive paging: read until limit or maxBytes
        val result = StringBuilder()
        var byteCount = 0
        var lineCount = 0
        val maxLines = args.limit ?: Int.MAX_VALUE
        var truncated = false

        for (i in startIndex until allLines.size) {
            if (lineCount >= maxLines) break
            val lineNum = i + 1
            val formatted = "%6d\t%s\n".format(lineNum, allLines[i])
            byteCount += formatted.length
            if (args.limit == null && byteCount > maxBytes && lineCount > 0) {
                truncated = true
                break
            }
            result.append(formatted)
            lineCount++
        }

        if (truncated) {
            val nextOffset = startIndex + lineCount + 1
            result.append("[Read output capped at ${maxBytes / 1000}KB. Use offset=$nextOffset to continue.]")
        }

        return result.toString()
    }
}
