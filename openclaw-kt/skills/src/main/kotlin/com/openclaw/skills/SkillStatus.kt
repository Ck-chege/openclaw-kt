package com.openclaw.skills

import kotlinx.serialization.Serializable

@Serializable
data class SkillMissing(
    val bins: List<String> = emptyList(),
    val anyBins: List<String> = emptyList(),
    val env: List<String> = emptyList(),
    val config: List<String> = emptyList(),
    val os: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = bins.isEmpty() && anyBins.isEmpty() && env.isEmpty() && config.isEmpty() && os.isEmpty()
}

@Serializable
data class SkillConfigCheck(
    val path: String,
    val satisfied: Boolean
)

@Serializable
data class SkillEntryState(
    val enabled: Boolean? = null,
    val env: Map<String, String> = emptyMap(),
    val config: Map<String, String> = emptyMap(),
    val hasApiKey: Boolean = false
)

@Serializable
data class SkillStatusEntry(
    val name: String,
    val skillKey: String,
    val description: String,
    val source: SkillSource,
    val filePath: String,
    val baseDir: String,
    val emoji: String? = null,
    val homepage: String? = null,
    val always: Boolean = false,
    val disabled: Boolean = false,
    val blockedByAllowlist: Boolean = false,
    val eligible: Boolean = false,
    val requirements: SkillRequirements = SkillRequirements(),
    val missing: SkillMissing = SkillMissing(),
    val configChecks: List<SkillConfigCheck> = emptyList(),
    val resources: SkillResources = SkillResources.EMPTY,
    val entry: SkillEntryState = SkillEntryState()
)

@Serializable
data class SkillStatusReport(
    val workspacePath: String,
    val skills: List<SkillStatusEntry>
)

data class ScannedSkill(
    val skill: SkillMeta,
    val gateResult: SkillGateResult
)
