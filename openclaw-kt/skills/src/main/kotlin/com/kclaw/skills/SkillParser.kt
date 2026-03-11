package com.kclaw.skills

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.readText

private val logger = KotlinLogging.logger {}

/**
 * Parses SKILL.md files with YAML frontmatter delimited by `---`.
 *
 * Format:
 * ```
 * ---
 * name: my-skill
 * description: Does something
 * ---
 * Body content (markdown instructions for the agent)
 * ```
 */
object SkillParser {

    private val frontmatterRegex = Regex(
        """^\s*---\s*\n(.*?)\n\s*---\s*\n?(.*)""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false
        )
    )

    /**
     * Parse a SKILL.md file into [SkillMeta].
     * Returns null if the file doesn't exist, is unreadable, has no valid frontmatter,
     * or the frontmatter is malformed.
     */
    fun parse(path: Path): SkillMeta? {
        if (!path.exists() || !path.isReadable()) {
            logger.debug { "Skill file not found or unreadable: $path" }
            return null
        }

        val content = try {
            path.readText()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read skill file: $path" }
            return null
        }

        val match = frontmatterRegex.find(content) ?: run {
            logger.debug { "No frontmatter delimiters found in: $path" }
            return null
        }

        val yamlContent = match.groupValues[1]
        val body = match.groupValues[2].trim()

        val frontmatter = try {
            yaml.decodeFromString(SkillFrontmatter.serializer(), yamlContent)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse YAML frontmatter in: $path" }
            return null
        }

        if (frontmatter.name.isBlank()) {
            logger.warn { "Skill has blank name in: $path" }
            return null
        }

        return SkillMeta(
            frontmatter = frontmatter,
            path = path,
            body = body,
            invocation = SkillInvocationPolicy(
                userInvocable = frontmatter.userInvocable,
                disableModelInvocation = frontmatter.disableModelInvocation,
                commandDispatch = frontmatter.commandDispatch,
                commandTool = frontmatter.commandTool,
                commandArgMode = frontmatter.commandArgMode
            )
        )
    }

    /**
     * Extract just the body content (everything after the frontmatter).
     * Returns the full content if no frontmatter delimiters are found.
     * Returns empty string for frontmatter-only files (no body after closing `---`).
     */
    fun extractBody(path: Path): String {
        if (!path.exists() || !path.isReadable()) return ""

        val content = try {
            path.readText()
        } catch (e: Exception) {
            return ""
        }

        val match = frontmatterRegex.find(content) ?: return content.trim()
        return match.groupValues[2].trim()
    }
}
