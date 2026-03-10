package com.openclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger {}

data class SkillDirectorySource(
    val path: Path,
    val source: SkillSource
)

/**
 * Scans directories for SKILL.md files and returns eligible skills.
 *
 * Directories are scanned in precedence order — first match for a skill name wins.
 * Each directory is expected to contain subdirectories, each with a SKILL.md file:
 * ```
 * skills/
 *   search/SKILL.md
 *   deploy/SKILL.md
 * ```
 */
class SkillScanner(
    private val gate: SkillGate = SkillGate()
) {
    private fun Path.realPathOrNull(): Path? = try {
        toRealPath()
    } catch (_: Exception) {
        null
    }

    private fun Path.isBoundedBy(root: Path): Boolean {
        val normalizedRoot = root.absolute().normalize()
        val normalizedPath = absolute().normalize()
        return normalizedPath == normalizedRoot || normalizedPath.startsWith(normalizedRoot)
    }

    /**
     * Scan the given directories in order, parse SKILL.md files, gate them,
     * and return eligible skills deduplicated by name (first directory wins).
     */
    fun scan(directories: List<Path>): List<SkillMeta> {
        return scanSources(
            directories.mapIndexed { index, path ->
                val source = when (index) {
                    0 -> SkillSource.WORKSPACE
                    1 -> SkillSource.USER
                    else -> SkillSource.BUNDLED
                }
                SkillDirectorySource(path = path, source = source)
            }
        )
    }

    fun scanSources(
        directories: List<SkillDirectorySource>,
        loadConfig: SkillsLoadConfig = SkillsLoadConfig(),
        gateOverride: SkillGate? = null
    ): List<SkillMeta> {
        return scanDetailedSources(
            directories = directories,
            loadConfig = loadConfig,
            gateOverride = gateOverride
        ).filter { it.gateResult is SkillGateResult.Eligible }
            .map { it.skill }
    }

    fun scanDetailedSources(
        directories: List<SkillDirectorySource>,
        loadConfig: SkillsLoadConfig = SkillsLoadConfig(),
        gateOverride: SkillGate? = null
    ): List<ScannedSkill> {
        val seen = mutableSetOf<String>()
        val results = mutableListOf<ScannedSkill>()
        val activeGate = gateOverride ?: gate

        for (entry in directories) {
            val dir = entry.path
            if (!dir.exists() || !dir.isDirectory()) {
                logger.debug { "Skipping non-existent or non-directory: $dir" }
                continue
            }

            val rootRealPath = dir.realPathOrNull() ?: continue
            val subdirs = try {
                dir.listDirectoryEntries()
                    .sortedBy { it.fileName.toString() }
                    .take(loadConfig.maxCandidatesPerRoot)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to list directory: $dir" }
                continue
            }

            val candidates = buildList {
                if (dir.resolve("SKILL.md").exists()) {
                    add(dir)
                }
                subdirs.forEach { add(it) }
            }

            for (subdir in candidates) {
                if (results.count { it.skill.source == entry.source } >= loadConfig.maxSkillsLoadedPerSource) {
                    break
                }
                val realSubdir = subdir.realPathOrNull() ?: continue
                if (!realSubdir.isBoundedBy(rootRealPath) || !Files.isDirectory(realSubdir)) {
                    logger.warn { "Skipping escaped or invalid skill directory: $subdir" }
                    continue
                }
                val skillFile = realSubdir.resolve("SKILL.md")
                val realSkillFile = skillFile.realPathOrNull() ?: continue
                if (!realSkillFile.isBoundedBy(rootRealPath) || !Files.isRegularFile(realSkillFile)) {
                    logger.warn { "Skipping escaped or invalid skill file: $skillFile" }
                    continue
                }
                val fileSize = try {
                    Files.size(realSkillFile)
                } catch (_: Exception) {
                    continue
                }
                if (fileSize > loadConfig.maxSkillFileBytes) {
                    logger.warn { "Skipping oversized skill file: $realSkillFile ($fileSize bytes)" }
                    continue
                }

                val meta = SkillParser.parse(realSkillFile)?.copy(
                    source = entry.source,
                    baseDir = realSubdir
                ) ?: continue

                // Deduplicate by name — first directory wins
                if (meta.name in seen) {
                    logger.debug { "Skill '${meta.name}' already seen, skipping: $skillFile" }
                    continue
                }

                // Enrich with resource discovery
                val resources = SkillResources.scan(realSubdir)
                val enriched = meta.copy(resources = resources)

                // Gate check
                when (val gateResult = activeGate.evaluate(enriched)) {
                    is SkillGateResult.Eligible -> {
                        seen.add(enriched.name)
                        results.add(ScannedSkill(enriched, gateResult))
                        logger.debug { "Skill eligible: ${enriched.name} from $skillFile" }
                    }
                    is SkillGateResult.Ineligible -> {
                        seen.add(enriched.name)
                        results.add(ScannedSkill(enriched, gateResult))
                        logger.debug { "Skill ineligible: ${enriched.name} — ${gateResult.reason}" }
                    }
                }
            }
        }

        logger.info { "Scanned ${directories.size} directories, found ${results.size} skills" }
        return results
    }

    companion object {
        /**
         * Build the default list of skill directories in precedence order:
         * 1. Workspace skills dir (highest priority)
     * 2. Global user skills dir (~/.openclawk/skills/)
         * 3. Bundled skills dir (if provided)
         */
        fun defaultDirectories(
            workspacePath: Path,
            bundledSkillsPath: Path? = null
        ): List<Path> = buildList {
            add(workspacePath.resolve("skills"))
            add(Path.of(System.getProperty("user.home"), ".openclawk", "skills"))
            if (bundledSkillsPath != null) {
                add(bundledSkillsPath)
            }
        }

        fun defaultSources(
            workspacePath: Path,
            config: SkillsConfig = SkillsConfig(),
            bundledSkillsPath: Path? = null,
            pluginSkillsPaths: List<Path> = emptyList()
        ): List<SkillDirectorySource> = buildList {
            config.load.extraDirs
                .map(::expandHome)
                .map(Path::of)
                .forEach { add(SkillDirectorySource(it, SkillSource.EXTRA)) }
            bundledSkillsPath?.let { add(SkillDirectorySource(it, SkillSource.BUNDLED)) }
            add(SkillDirectorySource(Path.of(System.getProperty("user.home"), ".openclawk", "skills"), SkillSource.USER))
            pluginSkillsPaths.forEach { add(SkillDirectorySource(it, SkillSource.PLUGIN)) }
            add(SkillDirectorySource(workspacePath.resolve("skills"), SkillSource.WORKSPACE))
        }

        private fun expandHome(path: String): String =
            if (path.startsWith("~")) {
                path.replaceFirst("~", System.getProperty("user.home"))
            } else {
                path
            }

    }
}
