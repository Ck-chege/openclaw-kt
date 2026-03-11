package com.openclaw.skills

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * YAML frontmatter parsed from a SKILL.md file via kaml.
 * `strictMode = false` tolerates unknown YAML keys.
 */
@Serializable
data class SkillFrontmatter(
    val name: String,
    val description: String = "",
    val emoji: String? = null,
    val always: Boolean = false,
    @SerialName("user-invocable")
    val userInvocable: Boolean = true,
    @SerialName("disable-model-invocation")
    val disableModelInvocation: Boolean = false,
    @SerialName("command-dispatch")
    val commandDispatch: SkillCommandDispatch? = null,
    @SerialName("command-tool")
    val commandTool: String? = null,
    @SerialName("command-arg-mode")
    val commandArgMode: SkillCommandArgMode = SkillCommandArgMode.RAW,
    val requires: SkillRequirements = SkillRequirements(),
    val openclaw: OpenClawSkillConfig = OpenClawSkillConfig()
)

@Serializable
data class SkillRequirements(
    val bins: List<String> = emptyList(),
    @SerialName("any_bins")
    val anyBins: List<String> = emptyList(),
    val env: List<String> = emptyList(),
    val os: List<String> = emptyList(),
    val config: List<String> = emptyList()
)

@Serializable
data class OpenClawSkillConfig(
    val version: String = "1.0",
    val tags: List<String> = emptyList(),
    val homepage: String? = null,
    val skillKey: String? = null,
    val primaryEnv: String? = null
)

/**
 * Describes bundled resource files discovered in a skill directory.
 * Skills can include scripts (executable), references (docs), and assets (templates/configs).
 */
@Serializable
data class SkillResources(
    val scripts: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val assets: List<String> = emptyList()
) {
    val hasScripts: Boolean get() = scripts.isNotEmpty()
    val hasReferences: Boolean get() = references.isNotEmpty()
    val hasAssets: Boolean get() = assets.isNotEmpty()
    val isEmpty: Boolean get() = !hasScripts && !hasReferences && !hasAssets

    companion object {
        val EMPTY = SkillResources()

        /** Scan a skill directory for resource subdirectories. */
        fun scan(skillDir: Path): SkillResources {
            fun listFiles(subdir: String): List<String> {
                val dir = skillDir.resolve(subdir)
                if (!dir.exists() || !dir.isDirectory()) return emptyList()
                return dir.listDirectoryEntries()
                    .filter { it.isRegularFile() }
                    .map { "$subdir/${it.fileName}" }
                    .sorted()
            }
            return SkillResources(
                scripts = listFiles("scripts"),
                references = listFiles("references"),
                assets = listFiles("assets")
            )
        }
    }
}

@Serializable
enum class SkillSource {
    WORKSPACE,
    USER,
    BUNDLED,
    EXTRA,
    PLUGIN
}

@Serializable
enum class SkillCommandDispatch {
    @SerialName("tool")
    TOOL
}

@Serializable
enum class SkillCommandArgMode {
    @SerialName("raw")
    RAW
}

@Serializable
data class SkillInvocationPolicy(
    val userInvocable: Boolean = true,
    val disableModelInvocation: Boolean = false,
    val commandDispatch: SkillCommandDispatch? = null,
    val commandTool: String? = null,
    val commandArgMode: SkillCommandArgMode = SkillCommandArgMode.RAW
)

/**
 * Resolved skill metadata with filesystem path attached.
 */
data class SkillMeta(
    val frontmatter: SkillFrontmatter,
    val path: Path,
    val body: String,
    val resources: SkillResources = SkillResources.EMPTY,
    val source: SkillSource = SkillSource.WORKSPACE,
    val baseDir: Path = path.parent,
    val invocation: SkillInvocationPolicy = SkillInvocationPolicy()
) {
    val name: String get() = frontmatter.name
    val skillKey: String get() = frontmatter.openclaw.skillKey?.ifBlank { null } ?: name
    val description: String get() = frontmatter.description
    val emoji: String? get() = frontmatter.emoji
    val homepage: String? get() = frontmatter.openclaw.homepage
    val primaryEnv: String? get() = frontmatter.openclaw.primaryEnv
    val always: Boolean get() = frontmatter.always
    val requires: SkillRequirements get() = frontmatter.requires
    val hasScripts: Boolean get() = resources.hasScripts
    val hasReferences: Boolean get() = resources.hasReferences
}

/**
 * Result of gating a skill against the current environment.
 */
sealed class SkillGateResult {
    data object Eligible : SkillGateResult()
    data class Ineligible(
        val missing: SkillMissing,
        val reason: String
    ) : SkillGateResult()
}
