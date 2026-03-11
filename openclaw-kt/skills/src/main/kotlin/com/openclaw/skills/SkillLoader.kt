package com.openclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

private const val SkillCommandMaxLength = 32
private const val SkillCommandFallback = "skill"

/**
 * Instruction appended after `<available_skills>` XML to tell the agent
 * how to use non-always skills. The wording must be directive enough that
 * the model actually calls `use_skill` before responding.
 */
private val USE_SKILL_INSTRUCTION = """
IMPORTANT — Skill activation:
Before composing your reply, scan the available_skills list above. If ANY skill's name or description could apply to the user's message, you MUST call the use_skill tool with that skill's name to load its full instructions, then follow those instructions exactly. Do not skip this step. Do not respond without checking skills first.
""".trimIndent().trim()

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
        val snapshot: SkillSnapshot,
        val fingerprint: SkillRootsFingerprint
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
            val candidate = buildSkillsXmlBlock(selectedSkills + skill)
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

    /**
     * Build just the `<available_skills>` XML block (no instruction, no always-on bodies).
     * Used for size-limit checks during skill selection.
     */
    private fun buildSkillsXmlBlock(skills: List<SkillMeta>): String {
        val lines = skills.map(::buildSkillLine)
        return buildString {
            appendLine("<available_skills>")
            for (line in lines) {
                appendLine(line)
            }
            append("</available_skills>")
        }
    }

    /**
     * Build the full prompt output: XML block + use_skill instruction + always-on bodies.
     * The instruction and always-on bodies are fixed overhead not subject to the char limit.
     */
    private fun buildRawXml(skills: List<SkillMeta>): String {
        val hasNonAlways = skills.any { !it.always }
        return buildString {
            append(buildSkillsXmlBlock(skills))

            // Instruct the agent to use the use_skill tool for non-always skills
            if (hasNonAlways) {
                appendLine()
                appendLine()
                append(USE_SKILL_INSTRUCTION)
            }

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
     *
     * Uses a three-layer cache invalidation strategy:
     * 1. **Version** — bumped by [refresh] (called by watcher or RPC)
     * 2. **Config hash** — detects config changes (bundled paths, plugin paths, settings)
     * 3. **Filesystem mtime** — detects on-disk changes regardless of watcher timing
     *
     * The mtime check is the correctness guarantee: it compares root directory
     * modification times against the last scan timestamp. This catches changes
     * that the watcher hasn't reported yet (e.g., macOS WatchService polling delay).
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
        val sources = SkillScanner.defaultSources(workspacePath, config, bundledSkillsPath, pluginSkillsPaths)
        val rootDirs = sources.map { it.path }
        val currentFingerprint = SkillRootsFingerprint.capture(rootDirs)

        snapshotCache[cacheKey]?.let { cached ->
            if (cached.version == version && cached.configHash == configHash) {
                if (cached.fingerprint == currentFingerprint) {
                    logger.debug { "Snapshot cache hit for $cacheKey (fingerprint unchanged)" }
                    return cached.snapshot
                }
                logger.info { "Snapshot cache stale for $cacheKey (filesystem fingerprint changed)" }
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
                snapshotCache[cacheKey] = CachedSnapshot(version, configHash, it, currentFingerprint)
            }
        }

        val xml = buildPromptXml(skills, config.load)
        logger.info { "Built skill snapshot: ${skills.size} skills, ${xml.length} chars XML" }
        return SkillSnapshot(skills = skills, promptXml = xml, version = version).also {
            snapshotCache[cacheKey] = CachedSnapshot(version, configHash, it, currentFingerprint)
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

    /**
     * Load a skill body from the most recently cached snapshot for a workspace.
     * Returns null if no snapshot is cached or the skill is not found.
     *
     * Used by [UseSkillTool] to load skill instructions at agent runtime
     * without requiring the caller to hold a snapshot reference.
     */
    fun loadFullFromCache(workspacePath: Path, skillName: String): String? {
        val cacheKey = workspacePath.toAbsolutePath().normalize().toString()
        val cached = snapshotCache[cacheKey] ?: return null
        return loadFull(cached.snapshot, skillName)
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
        val commandNames = resolveCommandNames(scanned.map { it.skill })
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
                ),
                invocation = SkillInvocationStatus(
                    userInvocable = skill.invocation.userInvocable,
                    disableModelInvocation = skill.invocation.disableModelInvocation,
                    commandName = commandNames[skill.skillKey],
                    commandDispatch = skill.invocation.commandDispatch,
                    commandTool = skill.invocation.commandTool,
                    commandArgMode = skill.invocation.commandArgMode
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
            .map { skill -> skill.copy(body = resolveBodyPaths(skill)) }

        return LoadedSkills(
            status = status,
            eligibleSkills = eligibleSkills
        )
    }

    companion object {
        /**
         * Expand relative resource paths in a skill's body to absolute paths
         * using the skill's [SkillMeta.baseDir]. Only paths that match known
         * resource entries (scripts/, references/, assets/) are expanded, so
         * arbitrary text is not mangled.
         *
         * This ensures skill scripts are found regardless of the agent's
         * working directory.
         */
        fun resolveBodyPaths(skill: SkillMeta): String {
            if (skill.body.isBlank() || skill.resources.isEmpty) return skill.body
            val baseDir = skill.baseDir.toAbsolutePath().normalize()
            var resolved = skill.body
            val allResourcePaths = skill.resources.scripts +
                skill.resources.references +
                skill.resources.assets
            for (relativePath in allResourcePaths) {
                val absolutePath = baseDir.resolve(relativePath).toString()
                resolved = resolved.replace(relativePath, absolutePath)
            }
            return resolved
        }

        private fun resolveCommandNames(skills: List<SkillMeta>): Map<String, String> {
            val used = mutableSetOf<String>()
            return buildMap {
                skills.forEach { skill ->
                    if (!skill.invocation.userInvocable) return@forEach
                    val base = sanitizeCommandName(skill.name)
                    val unique = resolveUniqueCommandName(base, used)
                    used += unique.lowercase()
                    put(skill.skillKey, unique)
                }
            }
        }

        private fun sanitizeCommandName(raw: String): String {
            val normalized = raw
                .lowercase()
                .replace(Regex("[^a-z0-9_]+"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .take(SkillCommandMaxLength)
            return normalized.ifBlank { SkillCommandFallback }
        }

        private fun resolveUniqueCommandName(base: String, used: Set<String>): String {
            if (base.lowercase() !in used) return base
            for (index in 2..999) {
                val suffix = "_$index"
                val prefix = base.take((SkillCommandMaxLength - suffix.length).coerceAtLeast(1))
                val candidate = "$prefix$suffix"
                if (candidate.lowercase() !in used) return candidate
            }
            return "${base.take((SkillCommandMaxLength - 2).coerceAtLeast(1))}_x"
        }

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
