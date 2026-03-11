package com.kclaw.skills

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * Lightweight filesystem fingerprint for skill root directories.
 *
 * Captures just enough state to detect changes without walking full subtrees.
 * For each skill root, records each skill subdirectory's:
 * - Name (detects new/deleted/renamed skills)
 * - Entry count (detects added/removed files within a skill)
 * - Aggregate mtime (detects edits to any file at any depth)
 *
 * The aggregate mtime is the **maximum** mtime across all files and directories
 * within a skill directory. This avoids stat-ing every file on every check —
 * we only need one number per skill that changes whenever anything inside changes.
 *
 * Cost: O(roots × skills-per-root) directory listings + one Files.walk per skill.
 * For 200 skills this is ~200 walks. Each walk is bounded by [MAX_ENTRIES_PER_SKILL]
 * to prevent runaway I/O on pathological skill directories.
 *
 * Comparison is a simple data class equality check — O(1) if sizes differ,
 * O(n) string/long comparison otherwise.
 */
data class SkillRootsFingerprint(
    val entries: List<RootFingerprint>
) {
    data class SkillDirEntry(
        val name: String,
        val fileCount: Int,
        val maxMtimeMs: Long
    )

    data class RootFingerprint(
        val rootExists: Boolean,
        val rootMtimeMs: Long,
        val skills: List<SkillDirEntry>
    )

    companion object {
        /**
         * Maximum files to stat per skill directory during fingerprinting.
         * A typical skill has 5-20 files. Cap prevents pathological directories
         * from blocking the staleness check.
         */
        private const val MAX_ENTRIES_PER_SKILL = 200

        fun capture(roots: List<Path>): SkillRootsFingerprint {
            return SkillRootsFingerprint(
                entries = roots.map { root -> captureRoot(root) }
            )
        }

        private fun captureRoot(root: Path): RootFingerprint {
            val normalized = root.toAbsolutePath().normalize()
            if (!normalized.exists() || !normalized.isDirectory()) {
                return RootFingerprint(rootExists = false, rootMtimeMs = 0L, skills = emptyList())
            }

            val rootMtime = try {
                Files.getLastModifiedTime(normalized).toMillis()
            } catch (_: Exception) {
                0L
            }

            val children = try {
                normalized.listDirectoryEntries()
                    .filter { it.isDirectory() }
                    .sortedBy { it.fileName.toString() }
            } catch (_: Exception) {
                return RootFingerprint(rootExists = true, rootMtimeMs = rootMtime, skills = emptyList())
            }

            val skillEntries = children.map { skillDir ->
                captureSkillDir(skillDir)
            }

            return RootFingerprint(
                rootExists = true,
                rootMtimeMs = rootMtime,
                skills = skillEntries
            )
        }

        private fun captureSkillDir(skillDir: Path): SkillDirEntry {
            var fileCount = 0
            var maxMtime = 0L

            try {
                Files.walk(skillDir).use { stream ->
                    val iterator = stream.iterator()
                    while (iterator.hasNext() && fileCount < MAX_ENTRIES_PER_SKILL) {
                        val entry = iterator.next()
                        fileCount++
                        val mtime = try {
                            Files.getLastModifiedTime(entry).toMillis()
                        } catch (_: Exception) {
                            continue
                        }
                        if (mtime > maxMtime) maxMtime = mtime
                    }
                }
            } catch (_: Exception) {
                // Inaccessible directory — treat as empty
            }

            return SkillDirEntry(
                name = skillDir.fileName.toString(),
                fileCount = fileCount,
                maxMtimeMs = maxMtime
            )
        }
    }
}