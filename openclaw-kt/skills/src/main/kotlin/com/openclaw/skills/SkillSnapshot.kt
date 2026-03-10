package com.openclaw.skills

/**
 * Immutable snapshot of available skills for a session.
 * Created once at session start and reused across turns.
 */
data class SkillSnapshot(
    val skills: List<SkillMeta>,
    val promptXml: String,
    val version: Long = 0L
) {
    companion object {
        val EMPTY = SkillSnapshot(skills = emptyList(), promptXml = "")
    }
}
