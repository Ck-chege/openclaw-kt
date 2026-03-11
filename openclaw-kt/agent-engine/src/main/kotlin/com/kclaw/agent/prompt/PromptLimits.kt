package com.kclaw.agent.prompt

/**
 * Configurable limits for prompt content to prevent prompt blowup.
 */
data class PromptLimits(
    /** Max chars per individual workspace file before truncation. */
    val perFileCharLimit: Int = 20_000,
    /** Max total chars for all workspace files combined. */
    val workspaceTotalCharLimit: Int = 150_000,
    /** Max chars for the skills XML block. */
    val skillsBlockCharLimit: Int = 30_000,
    /** Max number of skills included in the prompt. */
    val maxSkillsCount: Int = 150
)
