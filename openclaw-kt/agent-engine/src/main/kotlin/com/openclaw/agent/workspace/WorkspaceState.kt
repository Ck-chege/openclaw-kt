package com.openclaw.agent.workspace

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceState(
    val version: Int = 1,
    val phase: OnboardingPhase = OnboardingPhase.NEW,
    val bootstrapSeededAt: Instant? = null,
    val onboardingCompletedAt: Instant? = null
)

@Serializable
enum class OnboardingPhase {
    NEW,
    BOOTSTRAP_SEEDED,
    ONBOARDING_COMPLETE
}
