package com.kclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

private const val SkillCommandMaxLength = 32
private const val SkillCommandFallback = "skill"

/**
 * Builds skill snapshots and prompt XML for injection into the agent system prompt.
 *
 * XML format (TS-compatible, child elements):
 * ```xml
 * <available_skills>
 *   <skill>
 *     <name>weather</name>
 *     <description>Get weather forecasts</description>
 *     <location>/workspace/skills/weather/SKILL.md</location>
 *   </skill>
 * </available_skills>
 * ```
 *
 * Always-on skills have their body appended after the XML block.
 * The `## Skills (mandatory)` header and usage instructions are injected
 * by PromptAssembler, not baked into the XML.
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
        val fingerprint: SkillRootsFingerprint,
        /** Epoch ms when we last ran a filesystem fingerprint check against this snapshot. */
        val lastFingerprintCheckMs: Long = 0L
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
        appendLine("  <skill>")
        appendLine("    <name>${escapeXml(skill.name)}</name>")
        if (skill.description.isNotEmpty()) {
            appendLine("    <description>${escapeXml(skill.description)}</description>")
        }
        append("    <location>${escapeXml(skill.path.toString())}</location>")
        appendLine()
        append("  </skill>")
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
        return buildString {
            append(buildSkillsXmlBlock(skills))

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
     * Uses a **hybrid three-layer cache strategy** optimised for both correctness and scale:
     *
     * **Layer 1 — Version counter (O(1), zero disk I/O):**
     * The watcher calls [refresh] on every filesystem change, bumping a version counter
     * and evicting the cache. If the counter changed, we rebuild immediately.
     *
     * **Layer 2 — Config hash (O(1)):**
     * Detects config changes (bundled paths, plugin paths, feature flags) that the watcher
     * won't observe.
     *
     * **Layer 3 — Periodic fingerprint check (O(skills), disk I/O — runs rarely):**
     * A safety net that catches changes the watcher missed (OS delays, NFS mounts,
     * rapid writes within the debounce window). Runs at most once per
     * [SkillsLoadConfig.fingerprintCheckIntervalMs] (default 30 s).
     *
     * **For large skill sets (500+ skills):** most turns hit Layer 1 (O(1) integer compare,
     * zero disk I/O). Layer 3 fires at most every 30 s regardless of traffic volume.
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
        val now = System.currentTimeMillis()
        val fingerprintIntervalMs = config.load.fingerprintCheckIntervalMs

        // ── Layer 1 & 2: version + config fast path ──────────────────────────
        snapshotCache[cacheKey]?.let { cached ->
            if (cached.version == version && cached.configHash == configHash) {

                // ── Layer 3: periodic fingerprint safety net ──────────────────
                val msSinceCheck = now - cached.lastFingerprintCheckMs
                val needsFingerprintCheck = fingerprintIntervalMs == 0L ||
                        msSinceCheck >= fingerprintIntervalMs

                if (!needsFingerprintCheck) {
                    // Fast path: version matches + recently fingerprinted → trust cache
                    logger.debug { "Snapshot cache hit for $cacheKey (version=$version, age=${msSinceCheck}ms)" }
                    return cached.snapshot
                }

                // Fingerprint interval elapsed — run the safety-net check
                val sources = SkillScanner.defaultSources(workspacePath, config, bundledSkillsPath, pluginSkillsPaths)
                val currentFingerprint = SkillRootsFingerprint.capture(sources.map { it.path })
                if (cached.fingerprint == currentFingerprint) {
                    // Filesystem unchanged — refresh the timestamp and return cached snapshot
                    logger.debug { "Snapshot fingerprint verified for $cacheKey (unchanged after ${msSinceCheck}ms)" }
                    snapshotCache[cacheKey] = cached.copy(lastFingerprintCheckMs = now)
                    return cached.snapshot
                }

                // Fingerprint changed — watcher missed an event; rebuild below
                logger.info { "Snapshot stale for $cacheKey: fingerprint changed (watcher lag detected after ${msSinceCheck}ms)" }
            }
        }

        // ── Rebuild: version changed, config changed, or fingerprint mismatch ─
        val sources = SkillScanner.defaultSources(workspacePath, config, bundledSkillsPath, pluginSkillsPaths)
        val rootDirs = sources.map { it.path }
        val freshFingerprint = SkillRootsFingerprint.capture(rootDirs)

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
                snapshotCache[cacheKey] = CachedSnapshot(version, configHash, it, freshFingerprint, now)
            }
        }

        val xml = buildPromptXml(skills, config.load)
        logger.info { "Built skill snapshot: ${skills.size} skills, ${xml.length} chars XML" }
        return SkillSnapshot(skills = skills, promptXml = xml, version = version).also {
            snapshotCache[cacheKey] = CachedSnapshot(version, configHash, it, freshFingerprint, now)
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

        /**
         * Escape special characters for XML element text content.
         */
        fun escapeXml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
