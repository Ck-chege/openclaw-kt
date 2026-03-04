package com.openclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Gates skills against the current environment.
 *
 * Check order: OS → bins (ALL required) → anyBins (ANY required) → env vars (ALL required).
 * Short-circuits on first failure.
 */
class SkillGate(
    private val env: EnvironmentChecker = EnvironmentChecker()
) {
    /**
     * Evaluate whether a skill is eligible to run in the current environment.
     */
    fun evaluate(skill: SkillMeta): SkillGateResult {
        val requires = skill.requires

        // 1. OS check
        if (requires.os.isNotEmpty()) {
            val currentOs = env.currentOs()
            if (currentOs !in requires.os) {
                val reason = "OS '$currentOs' not in required: ${requires.os}"
                logger.debug { "Skill '${skill.name}' ineligible: $reason" }
                return SkillGateResult.Ineligible(reason)
            }
        }

        // 2. Required binaries (ALL must be present)
        if (requires.bins.isNotEmpty()) {
            val missing = requires.bins.filter { !env.hasBinary(it) }
            if (missing.isNotEmpty()) {
                val reason = "Missing required binaries: $missing"
                logger.debug { "Skill '${skill.name}' ineligible: $reason" }
                return SkillGateResult.Ineligible(reason)
            }
        }

        // 3. Any binaries (at least ONE must be present)
        if (requires.anyBins.isNotEmpty()) {
            val anyPresent = requires.anyBins.any { env.hasBinary(it) }
            if (!anyPresent) {
                val reason = "None of anyBins present: ${requires.anyBins}"
                logger.debug { "Skill '${skill.name}' ineligible: $reason" }
                return SkillGateResult.Ineligible(reason)
            }
        }

        // 4. Environment variables (ALL must be set)
        if (requires.env.isNotEmpty()) {
            val missing = requires.env.filter { !env.hasEnvVar(it) }
            if (missing.isNotEmpty()) {
                val reason = "Missing required env vars: $missing"
                logger.debug { "Skill '${skill.name}' ineligible: $reason" }
                return SkillGateResult.Ineligible(reason)
            }
        }

        return SkillGateResult.Eligible
    }
}
