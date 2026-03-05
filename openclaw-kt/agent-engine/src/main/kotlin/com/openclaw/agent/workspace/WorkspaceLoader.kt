package com.openclaw.agent.workspace

import com.openclaw.agent.prompt.PromptLimits
import com.openclaw.agent.prompt.TextTruncation
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.fileSize
import kotlin.io.path.readText

private val logger = KotlinLogging.logger {}

class WorkspaceLoader(
    val workspacePath: Path,
    private val limits: PromptLimits = PromptLimits()
) {

    companion object {
        const val MAX_FILE_SIZE_BYTES: Long = 2 * 1024 * 1024
    }

    fun readSoulMd(): String = readFile("SOUL.md")

    fun readAgentsMd(): String = readFile("AGENTS.md")

    fun readUserMd(): String = readFile("USER.md")

    fun readToolsMd(): String = readFile("TOOLS.md")

    fun readMemoryMd(): String = readFile("MEMORY.md")

    fun readIdentityMd(): String = readFile("IDENTITY.md")

    fun readBootstrapMd(): String = readFile("BOOTSTRAP.md")

    fun readHeartbeatMd(): String = readFile("HEARTBEAT.md")

    private fun readFile(filename: String): String {
        val file = workspacePath.resolve(filename)
        if (!file.exists()) {
            logger.debug { "Workspace file not found: $file" }
            return ""
        }
        if (!file.isReadable()) {
            logger.warn { "Workspace file not readable: $file" }
            return ""
        }
        val size = file.fileSize()
        if (size > MAX_FILE_SIZE_BYTES) {
            logger.warn { "Workspace file exceeds ${MAX_FILE_SIZE_BYTES / 1024 / 1024}MB limit: $file ($size bytes)" }
            return ""
        }
        return try {
            val content = file.readText().trim()
            if (content.length > limits.perFileCharLimit) {
                logger.info { "Truncating $filename: ${content.length} chars → ${limits.perFileCharLimit} chars" }
                TextTruncation.truncateWithMarkers(content, limits.perFileCharLimit)
            } else {
                content
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read workspace file: $file" }
            ""
        }
    }
}

fun Path.readSoulMd(): String = WorkspaceLoader(this).readSoulMd()
fun Path.readAgentsMd(): String = WorkspaceLoader(this).readAgentsMd()
fun Path.readUserMd(): String = WorkspaceLoader(this).readUserMd()
fun Path.readToolsMd(): String = WorkspaceLoader(this).readToolsMd()
fun Path.readMemoryMd(): String = WorkspaceLoader(this).readMemoryMd()
fun Path.readIdentityMd(): String = WorkspaceLoader(this).readIdentityMd()
fun Path.readBootstrapMd(): String = WorkspaceLoader(this).readBootstrapMd()
fun Path.readHeartbeatMd(): String = WorkspaceLoader(this).readHeartbeatMd()
