package com.openclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Builds skill snapshots and prompt XML for injection into the agent system prompt.
 *
 * XML format (compact, ~24 tokens per skill):
 * ```xml
 * <available_skills>
 *   <skill name="search" description="Web search" path="/workspace/skills/search/SKILL.md" />
 * </available_skills>
 * ```
 *
 * Always-on skills have their body appended after the XML block.
 */
class SkillLoader(
    private val scanner: SkillScanner = SkillScanner()
) {
    /**
     * Build the compact XML representation of available skills.
     * Returns empty string for an empty list.
     */
    fun buildPromptXml(skills: List<SkillMeta>): String {
        if (skills.isEmpty()) return ""

        return buildString {
            appendLine("<available_skills>")
            for (skill in skills) {
                append("  <skill name=\"${escapeXmlAttr(skill.name)}\"")
                if (skill.description.isNotEmpty()) {
                    append(" description=\"${escapeXmlAttr(skill.description)}\"")
                }
                if (skill.emoji != null) {
                    append(" emoji=\"${escapeXmlAttr(skill.emoji!!)}\"")
                }
                append(" path=\"${escapeXmlAttr(skill.path.toString())}\"")
                if (skill.resources.hasScripts) {
                    append(" has_scripts=\"true\"")
                    append(" scripts=\"${escapeXmlAttr(skill.resources.scripts.joinToString(","))}\"")
                }
                if (skill.resources.hasReferences) {
                    append(" has_references=\"true\"")
                    append(" references=\"${escapeXmlAttr(skill.resources.references.joinToString(","))}\"")
                }
                if (skill.resources.hasAssets) {
                    append(" has_assets=\"true\"")
                    append(" assets=\"${escapeXmlAttr(skill.resources.assets.joinToString(","))}\"")
                }
                append(" />")
                appendLine()
            }
            append("</available_skills>")

            // Append always-on skill bodies
            val alwaysOn = skills.filter { it.always && it.body.isNotBlank() }
            if (alwaysOn.isNotEmpty()) {
                appendLine()
                appendLine()
                for (skill in alwaysOn) {
                    appendLine("<!-- always-on: ${escapeXmlAttr(skill.name)} -->")
                    appendLine(skill.body)
                }
            }
        }.trimEnd()
    }

    /**
     * Build a complete skill snapshot by scanning directories, gating, and generating XML.
     */
    fun buildSnapshot(workspacePath: Path, bundledSkillsPath: Path? = null): SkillSnapshot {
        val directories = SkillScanner.defaultDirectories(workspacePath, bundledSkillsPath)
        val skills = scanner.scan(directories)

        if (skills.isEmpty()) {
            logger.debug { "No eligible skills found for workspace: $workspacePath" }
            return SkillSnapshot.EMPTY
        }

        val xml = buildPromptXml(skills)
        logger.info { "Built skill snapshot: ${skills.size} skills, ${xml.length} chars XML" }
        return SkillSnapshot(skills = skills, promptXml = xml)
    }

    /**
     * Load the full body content for a skill by name from a snapshot.
     * Returns null if the skill is not in the snapshot or body is unreadable.
     */
    fun loadFull(snapshot: SkillSnapshot, skillName: String): String? {
        val skill = snapshot.skills.find { it.name == skillName } ?: return null
        return try {
            SkillParser.extractBody(skill.path).ifEmpty { null }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load full content for skill: $skillName" }
            null
        }
    }

    companion object {
        /**
         * Escape special characters for XML attribute values.
         */
        fun escapeXmlAttr(value: String): String = value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
    }
}
