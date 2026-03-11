package com.kclaw.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * Allows the agent to create new skills at runtime, including bundled
 * scripts, references, and assets.
 *
 * Writes to `{workspace}/skills/{name}/` — the same directory
 * the [SkillScanner] checks first, so the skill becomes available on the next run.
 *
 * Directory structure:
 * ```
 * skills/{name}/
 *   SKILL.md              # frontmatter + instructions
 *   scripts/              # executable scripts (run via exec tool)
 *   references/           # reference docs
 *   assets/               # templates, config files
 * ```
 */
class CreateSkillTool(
    private val workspacePath: () -> Path
) : SimpleTool<CreateSkillTool.Args>(
    argsSerializer = Args.serializer(),
    name = "create_skill",
    description = "Create a new skill with optional bundled scripts, references, and assets. Scripts can be executed later via the exec tool. The skill becomes available on the next agent run."
) {
    @Serializable
    data class SkillFile(
        @property:LLMDescription("File name (e.g. 'init.sh', 'template.py', 'guide.md')")
        val name: String,
        @property:LLMDescription("File content")
        val content: String
    )

    @Serializable
    data class Args(
        @property:LLMDescription("Unique skill name in kebab-case (e.g. 'web-search', 'code-review')")
        val name: String,
        @property:LLMDescription("Short description of what the skill does")
        val description: String,
        @property:LLMDescription("Full skill body — markdown instructions the agent follows when using this skill")
        val body: String,
        @property:LLMDescription("Optional emoji for the skill (e.g. a magnifying glass for search)")
        val emoji: String? = null,
        @property:LLMDescription("If true, skill body is always injected into the prompt without needing to be requested")
        val always: Boolean = false,
        @property:LLMDescription("Required binaries that must be on PATH for this skill to be eligible (all must be present)")
        val requiredBins: List<String> = emptyList(),
        @property:LLMDescription("Required environment variables that must be set for this skill to be eligible (all must be set)")
        val requiredEnv: List<String> = emptyList(),
        @property:LLMDescription("Executable scripts to bundle with the skill (placed in scripts/ directory). The agent can run these later via the exec tool.")
        val scripts: List<SkillFile> = emptyList(),
        @property:LLMDescription("Reference documents for the skill (placed in references/ directory)")
        val references: List<SkillFile> = emptyList(),
        @property:LLMDescription("Static assets like templates or config files (placed in assets/ directory)")
        val assets: List<SkillFile> = emptyList()
    )

    companion object {
        private val NAME_PATTERN = Regex("^[a-z0-9][a-z0-9-]*$")
        private val IS_UNIX = !System.getProperty("os.name").lowercase().contains("win")
    }

    override suspend fun execute(args: Args): String {
        // 1. Validate name
        val name = args.name.trim()
        if (name.isBlank()) {
            return "Error: Skill name cannot be blank"
        }
        if (!NAME_PATTERN.matches(name)) {
            return "Error: Skill name must be kebab-case (lowercase letters, digits, hyphens; must start with letter or digit). Got: '$name'"
        }

        // 2. Validate all file names
        val allFiles = args.scripts + args.references + args.assets
        for (file in allFiles) {
            val error = validateFileName(file.name)
            if (error != null) return "Error: $error"
        }

        // 3. Resolve target path
        val skillDir = workspacePath().resolve("skills").resolve(name)
        val skillFile = skillDir.resolve("SKILL.md")

        if (Files.exists(skillFile)) {
            return "Error: Skill '$name' already exists at $skillFile"
        }

        // 4. Build SKILL.md content
        val content = buildSkillMd(args)

        // 5. Write everything
        return try {
            Files.createDirectories(skillDir)
            Files.writeString(skillFile, content)

            // Write resource directories
            val scriptCount = writeFiles(skillDir.resolve("scripts"), args.scripts, executable = true)
            val refCount = writeFiles(skillDir.resolve("references"), args.references)
            val assetCount = writeFiles(skillDir.resolve("assets"), args.assets)

            buildString {
                append("Created skill '$name' at $skillFile")
                if (scriptCount > 0) append(", $scriptCount script(s)")
                if (refCount > 0) append(", $refCount reference(s)")
                if (assetCount > 0) append(", $assetCount asset(s)")
            }
        } catch (e: Exception) {
            "Error writing skill file: ${e.message}"
        }
    }

    private fun writeFiles(dir: Path, files: List<SkillFile>, executable: Boolean = false): Int {
        if (files.isEmpty()) return 0
        Files.createDirectories(dir)
        for (file in files) {
            val target = dir.resolve(file.name)
            Files.writeString(target, file.content)
            if (executable && IS_UNIX) {
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"))
            }
        }
        return files.size
    }

    private fun validateFileName(name: String): String? {
        if (name.isBlank()) return "File name cannot be blank"
        if (name.contains("..")) return "File name cannot contain '..'"
        if (name.contains("/") || name.contains("\\")) return "File name cannot contain path separators"
        return null
    }

    private fun buildSkillMd(args: Args): String = buildString {
        appendLine("---")
        appendLine("name: ${args.name.trim()}")
        if (args.description.isNotBlank()) {
            appendLine("description: ${yamlEscape(args.description)}")
        }
        if (args.emoji != null) {
            appendLine("emoji: \"${args.emoji}\"")
        }
        if (args.always) {
            appendLine("always: true")
        }
        if (args.requiredBins.isNotEmpty() || args.requiredEnv.isNotEmpty()) {
            appendLine("requires:")
            if (args.requiredBins.isNotEmpty()) {
                appendLine("  bins: [${args.requiredBins.joinToString(", ")}]")
            }
            if (args.requiredEnv.isNotEmpty()) {
                appendLine("  env: [${args.requiredEnv.joinToString(", ")}]")
            }
        }
        appendLine("---")
        append(args.body)
    }

    private fun yamlEscape(value: String): String {
        return if (value.contains(':') || value.contains('#') || value.contains('"') ||
            value.startsWith(' ') || value.endsWith(' ')
        ) {
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } else {
            value
        }
    }
}
