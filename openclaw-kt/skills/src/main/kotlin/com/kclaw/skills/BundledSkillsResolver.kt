package com.kclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger {}

/**
 * Resolves the bundled skills directory shipped with the KClaw distribution.
 *
 * Resolution order (mirrors TS `resolveBundledSkillsDir`):
 * 1. `KCLAW_BUNDLED_SKILLS_DIR` env var — explicit override
 * 2. `OPENCLAW_BUNDLED_SKILLS_DIR` env var — legacy override
 * 2. `<executableDir>/skills/` — sibling to the running JAR/binary (production fat-jar layout)
 * 3. Classpath root scan — walks class-path roots looking for a `skills/` dir that
 *    contains at least one `SKILL.md` subdirectory (dev/test layout)
 */
object BundledSkillsResolver {

    fun resolve(
        envOverride: String? = System.getenv("KCLAW_BUNDLED_SKILLS_DIR")
            ?: System.getenv("OPENCLAW_BUNDLED_SKILLS_DIR"),
        executablePath: Path? = resolveExecutablePath(),
        classpathRoots: List<Path> = resolveClasspathRoots()
    ): Path? {
        // 1. Env var override
        if (!envOverride.isNullOrBlank()) {
            val path = Path.of(envOverride.trim())
            if (path.isDirectory()) {
                logger.debug { "Bundled skills dir from env override: $path" }
                return path
            }
            logger.warn { "Bundled skills env override set but not a directory: $path" }
        }

        // 2. Sibling to the executable (fat-jar / native binary layout)
        if (executablePath != null) {
            val sibling = executablePath.parent?.resolve("skills")
            if (sibling != null && looksLikeSkillsDir(sibling)) {
                logger.debug { "Bundled skills dir from executable sibling: $sibling" }
                return sibling
            }
        }

        // 3. Classpath roots — for npm/dev/IDE launches
        for (root in classpathRoots) {
            val candidate = root.resolve("skills")
            if (looksLikeSkillsDir(candidate)) {
                logger.debug { "Bundled skills dir from classpath: $candidate" }
                return candidate
            }
        }

        logger.debug { "Bundled skills dir could not be resolved; built-in skills will not be available" }
        return null
    }

    /**
     * A directory "looks like" a skills dir if it contains at least one sub-directory
     * with a `SKILL.md` file, or directly contains a `SKILL.md` file.
     */
    private fun looksLikeSkillsDir(dir: Path): Boolean {
        if (!dir.exists() || !dir.isDirectory()) return false
        return try {
            dir.listDirectoryEntries().any { entry ->
                entry.resolve("SKILL.md").isRegularFile() || entry.isRegularFile() && entry.fileName.toString() == "SKILL.md"
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveExecutablePath(): Path? = try {
        val uri = ProcessHandle.current().info().command().orElse(null) ?: return null
        Path.of(uri)
    } catch (_: Exception) {
        null
    }

    private fun resolveClasspathRoots(): List<Path> = try {
        val cp = System.getProperty("java.class.path") ?: return emptyList()
        cp.split(System.getProperty("path.separator", ":"))
            .map { Path.of(it).toAbsolutePath().normalize() }
            .flatMap { entry -> ancestorPaths(entry, depth = 4) }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }

    /** Return [path] and up to [depth] ancestor directories. */
    private fun ancestorPaths(path: Path, depth: Int): List<Path> {
        val result = mutableListOf<Path>()
        var current: Path? = path
        var remaining = depth
        while (remaining-- > 0) {
            val c = current ?: break
            result.add(c)
            current = c.parent
        }
        return result
    }
}
