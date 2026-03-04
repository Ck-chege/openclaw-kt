package com.openclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger {}

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
    /**
     * Scan the given directories in order, parse SKILL.md files, gate them,
     * and return eligible skills deduplicated by name (first directory wins).
     */
    fun scan(directories: List<Path>): List<SkillMeta> {
        val seen = mutableSetOf<String>()
        val results = mutableListOf<SkillMeta>()

        for (dir in directories) {
            if (!dir.exists() || !dir.isDirectory()) {
                logger.debug { "Skipping non-existent or non-directory: $dir" }
                continue
            }

            val subdirs = try {
                dir.listDirectoryEntries().filter { it.isDirectory() }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to list directory: $dir" }
                continue
            }

            for (subdir in subdirs) {
                val skillFile = subdir.resolve("SKILL.md")
                val meta = SkillParser.parse(skillFile) ?: continue

                // Deduplicate by name — first directory wins
                if (meta.name in seen) {
                    logger.debug { "Skill '${meta.name}' already seen, skipping: $skillFile" }
                    continue
                }

                // Enrich with resource discovery
                val resources = SkillResources.scan(subdir)
                val enriched = meta.copy(resources = resources)

                // Gate check
                when (val gateResult = gate.evaluate(enriched)) {
                    is SkillGateResult.Eligible -> {
                        seen.add(enriched.name)
                        results.add(enriched)
                        logger.debug { "Skill eligible: ${enriched.name} from $skillFile" }
                    }
                    is SkillGateResult.Ineligible -> {
                        logger.debug { "Skill ineligible: ${enriched.name} — ${gateResult.reason}" }
                    }
                }
            }
        }

        logger.info { "Scanned ${directories.size} directories, found ${results.size} eligible skills" }
        return results
    }

    companion object {
        /**
         * Build the default list of skill directories in precedence order:
         * 1. Workspace skills dir (highest priority)
         * 2. Global user skills dir (~/.openclaw/skills/)
         * 3. Bundled skills dir (if provided)
         */
        fun defaultDirectories(
            workspacePath: Path,
            bundledSkillsPath: Path? = null
        ): List<Path> = buildList {
            add(workspacePath.resolve("skills"))
            add(Path.of(System.getProperty("user.home"), ".openclaw", "skills"))
            if (bundledSkillsPath != null) {
                add(bundledSkillsPath)
            }
        }
    }
}
