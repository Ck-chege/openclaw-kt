package com.openclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

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
    private val scanner: SkillScanner = SkillScanner(),
    private val maxSkillsCount: Int = 150,
    private val skillsBlockCharLimit: Int = 30_000,
    private val runtime: SkillRuntime = SkillRuntime()
) {
    private data class LoadedSkills(
        val status: List<SkillStatusEntry>,
        val eligibleSkills: List<SkillMeta>
    )

    private data class CachedSnapshot(
        val version: Long,
        val configHash: Int,
        val snapshot: SkillSnapshot
    )

    private val snapshotCache = ConcurrentHashMap<String, CachedSnapshot>()
    private val versions = ConcurrentHashMap<String, Long>()

    /**
     * Build the compact XML representation of available skills.
     * Enforces maxSkillsCount and skillsBlockCharLimit.
     * Returns empty string for an empty list.
     */
    fun buildPromptXml(
        skills: List<SkillMeta>,
        loadConfig: SkillsLoadConfig = SkillsLoadConfig()
    ): String {
        if (skills.isEmpty()) return ""

        val promptVisible = skills.filter { !it.invocation.disableModelInvocation }
        if (promptVisible.isEmpty()) return ""
        val maxCount = minOf(maxSkillsCount, loadConfig.maxSkillsInPrompt)
        val capped = if (promptVisible.size > maxCount) {
            logger.info { "Capping skills from ${promptVisible.size} to $maxCount" }
            promptVisible.take(maxCount)
        } else {
            promptVisible
        }
        val limit = minOf(skillsBlockCharLimit, loadConfig.maxSkillsPromptChars)

        val selectedSkills = mutableListOf<SkillMeta>()
        for (skill in capped) {
            val candidate = buildRawXml(selectedSkills + skill)
            if (candidate.length > limit) {
                break
            }
            selectedSkills += skill
        }
        return buildRawXml(selectedSkills)
    }

    private fun buildSkillLine(skill: SkillMeta): String = buildString {
        append("  <skill name=\"${escapeXmlAttr(skill.name)}\"")
        if (skill.description.isNotEmpty()) {
            append(" description=\"${escapeXmlAttr(skill.description)}\"")
        }
        if (skill.emoji != null) {
            append(" emoji=\"${escapeXmlAttr(skill.emoji!!)}\"")
        }
        append(" path=\"${escapeXmlAttr(skill.path.toString())}\"")
        append(" source=\"${skill.source.name.lowercase()}\"")
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
    }

    private fun buildRawXml(skills: List<SkillMeta>): String {
        val lines = skills.map(::buildSkillLine)
        return buildString {
            appendLine("<available_skills>")
            for (line in lines) {
                appendLine(line)
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

    fun loadEntries(
        workspacePath: Path,
        config: SkillsConfig = SkillsConfig(),
        bundledSkillsPath: Path? = null,
        pluginSkillsPaths: List<Path> = emptyList(),
        isConfigPathTruthy: (String) -> Boolean = { false }
    ): List<SkillMeta> {
        return collectSkills(
            workspacePath = workspacePath,
            config = config,
            bundledSkillsPath = bundledSkillsPath,
            pluginSkillsPaths = pluginSkillsPaths,
            isConfigPathTruthy = isConfigPathTruthy
        ).eligibleSkills
    }

    fun buildStatus(
        workspacePath: Path,
        config: SkillsConfig = SkillsConfig(),
        bundledSkillsPath: Path? = null,
        pluginSkillsPaths: List<Path> = emptyList(),
        isConfigPathTruthy: (String) -> Boolean = { false }
    ): SkillStatusReport {
        return SkillStatusReport(
            workspacePath = workspacePath.toString(),
            skills = collectSkills(
                workspacePath = workspacePath,
                config = config,
                bundledSkillsPath = bundledSkillsPath,
                pluginSkillsPaths = pluginSkillsPaths,
                isConfigPathTruthy = isConfigPathTruthy
            ).status
        )
    }

    fun applyRuntime(snapshot: SkillSnapshot, config: SkillsConfig): () -> Unit =
        runtime.applyEnvOverrides(snapshot, config)

    fun refresh(workspacePath: Path): Long {
        val key = workspacePath.toAbsolutePath().normalize().toString()
        val next = (versions[key] ?: 0L) + 1L
        versions[key] = next
        snapshotCache.remove(key)
        return next
    }

    /**
     * Build a complete skill snapshot by scanning directories, gating, and generating XML.
     */
    fun buildSnapshot(
        workspacePath: Path,
        config: SkillsConfig = SkillsConfig(),
        bundledSkillsPath: Path? = null,
        pluginSkillsPaths: List<Path> = emptyList(),
        isConfigPathTruthy: (String) -> Boolean = { false },
        configStateHash: Int = config.hashCode()
    ): SkillSnapshot {
        val cacheKey = workspacePath.toAbsolutePath().normalize().toString()
        val version = versions[cacheKey] ?: 0L
        val configHash = listOf(
            configStateHash,
            bundledSkillsPath?.toString(),
            pluginSkillsPaths.map(Path::toString)
        ).hashCode()
        snapshotCache[cacheKey]?.let { cached ->
            if (cached.version == version && cached.configHash == configHash) {
                return cached.snapshot
            }
        }
        val loaded = collectSkills(
            workspacePath = workspacePath,
            config = config,
            bundledSkillsPath = bundledSkillsPath,
            pluginSkillsPaths = pluginSkillsPaths,
            isConfigPathTruthy = isConfigPathTruthy
        )
        val skills = loaded.eligibleSkills

        if (skills.isEmpty()) {
            logger.debug { "No eligible skills found for workspace: $workspacePath" }
            return SkillSnapshot.EMPTY.copy(version = version).also {
                snapshotCache[cacheKey] = CachedSnapshot(version, configHash, it)
            }
        }

        val xml = buildPromptXml(skills, config.load)
        logger.info { "Built skill snapshot: ${skills.size} skills, ${xml.length} chars XML" }
        return SkillSnapshot(skills = skills, promptXml = xml, version = version).also {
            snapshotCache[cacheKey] = CachedSnapshot(version, configHash, it)
        }
    }

    /**
     * Load the snapshotted body content for a skill by name from a snapshot.
     * Returns null if the skill is not in the snapshot or the snapshotted body is blank.
     */
    fun loadFull(snapshot: SkillSnapshot, skillName: String): String? {
        val skill = snapshot.skills.find { it.name == skillName } ?: return null
        return skill.body.ifBlank { null }
    }

    private fun collectSkills(
        workspacePath: Path,
        config: SkillsConfig,
        bundledSkillsPath: Path?,
        pluginSkillsPaths: List<Path>,
        isConfigPathTruthy: (String) -> Boolean
    ): LoadedSkills {
        val sources = SkillScanner.defaultSources(workspacePath, config, bundledSkillsPath, pluginSkillsPaths)
        val gate = SkillGate(isConfigPathTruthy = isConfigPathTruthy)
        val scanned = scanner.scanDetailedSources(
            directories = sources,
            loadConfig = config.load,
            gateOverride = gate
        )
        val status = scanned.map { scannedSkill ->
            val skill = scannedSkill.skill
            val entryConfig = config.entries[skill.skillKey]
            val disabled = entryConfig?.enabled == false
            val blockedByAllowlist = skill.source == SkillSource.BUNDLED &&
                config.allowBundled.isNotEmpty() &&
                skill.skillKey !in config.allowBundled &&
                skill.name !in config.allowBundled
            val configChecks = skill.requires.config.map { path ->
                SkillConfigCheck(path = path, satisfied = isConfigPathTruthy(path))
            }
            val missing = when (val gateResult = scannedSkill.gateResult) {
                is SkillGateResult.Eligible -> SkillMissing()
                is SkillGateResult.Ineligible -> gateResult.missing
            }
            SkillStatusEntry(
                name = skill.name,
                skillKey = skill.skillKey,
                description = skill.description,
                source = skill.source,
                filePath = skill.path.toString(),
                baseDir = skill.baseDir.toString(),
                emoji = skill.emoji,
                homepage = skill.homepage,
                always = skill.always,
                disabled = disabled,
                blockedByAllowlist = blockedByAllowlist,
                eligible = scannedSkill.gateResult is SkillGateResult.Eligible && !disabled && !blockedByAllowlist,
                requirements = skill.requires,
                missing = missing,
                configChecks = configChecks,
                resources = skill.resources,
                entry = SkillEntryState(
                    enabled = entryConfig?.enabled,
                    env = entryConfig?.env ?: emptyMap(),
                    config = entryConfig?.config ?: emptyMap(),
                    hasApiKey = !entryConfig?.apiKey.isNullOrBlank()
                )
            )
        }
        val eligibleSkillKeys = status
            .filter { it.eligible }
            .map { it.skillKey }
            .toSet()
        val eligibleSkills = scanned
            .map { it.skill }
            .filter { it.skillKey in eligibleSkillKeys }

        return LoadedSkills(
            status = status,
            eligibleSkills = eligibleSkills
        )
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
