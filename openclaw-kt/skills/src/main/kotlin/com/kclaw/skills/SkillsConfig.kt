package com.kclaw.skills

import kotlinx.serialization.Serializable

@Serializable
data class SkillsConfig(
    val allowBundled: List<String> = emptyList(),
    val load: SkillsLoadConfig = SkillsLoadConfig(),
    val install: SkillsInstallConfig = SkillsInstallConfig(),
    val entries: Map<String, SkillEntryConfig> = emptyMap()
)

@Serializable
data class SkillsLoadConfig(
    val extraDirs: List<String> = emptyList(),
    val watch: Boolean = true,
    val watchDebounceMs: Int = 250,
    val maxCandidatesPerRoot: Int = 300,
    val maxSkillsLoadedPerSource: Int = 200,
    val maxSkillsInPrompt: Int = 150,
    val maxSkillsPromptChars: Int = 30_000,
    val maxSkillFileBytes: Int = 256_000,
    /**
     * How often (in ms) to perform a full filesystem fingerprint check as a safety net,
     * even when the version counter hasn't changed (i.e., the watcher hasn't fired).
     *
     * This catches changes the watcher missed (OS delays, network filesystems, etc.)
     * without paying the fingerprint cost on every single turn.
     *
     * Default: 30 seconds. Set to 0 to fingerprint on every turn (original behavior).
     * Increase to 60_000+ for very large skill sets (500+ skills) on stable filesystems.
     */
    val fingerprintCheckIntervalMs: Long = 30_000L
)

@Serializable
data class SkillsInstallConfig(
    val preferBrew: Boolean = true,
    val nodeManager: String = "npm"
)

@Serializable
data class SkillEntryConfig(
    val enabled: Boolean? = null,
    val env: Map<String, String> = emptyMap(),
    val apiKey: String? = null,
    val config: Map<String, String> = emptyMap()
)
