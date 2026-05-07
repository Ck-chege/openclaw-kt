package com.kclaw.agent.workspace

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Manages workspace first-run seeding and onboarding state machine.
 *
 * State transitions:
 * - NEW → BOOTSTRAP_SEEDED (fresh workspace: seed all 8 templates)
 * - BOOTSTRAP_SEEDED → ONBOARDING_COMPLETE (BOOTSTRAP.md deleted by agent)
 * - ONBOARDING_COMPLETE → no-op
 */
class  WorkspaceBootstrap(
    private val workspacePath: Path,
    private val clock: Clock = Clock.System
) {
    private val stateDir: Path = workspacePath.resolve(".kclaw")
    private val stateFile: Path = stateDir.resolve("workspace-state.json")

    companion object {
        val TEMPLATE_FILES = listOf(
            "BOOTSTRAP.md",
            "SOUL.md",
            "AGENTS.md",
            "IDENTITY.md",
            "USER.md",
            "TOOLS.md",
            "HEARTBEAT.md",
            "MEMORY.md"
        )

        fun loadTemplate(filename: String): String? {
            return WorkspaceBootstrap::class.java
                .getResourceAsStream("/workspace-templates/$filename")
                ?.bufferedReader()
                ?.readText()
        }
    }

    /**
     * Explicitly marks onboarding as complete.
     *
     * Normal completion happens when BOOTSTRAP.md is deleted and [ensureWorkspace]
     * observes that transition. This override is kept for exceptional/manual flows.
     *
     * Returns the updated state, or the current state if already complete.
     */
    fun markOnboardingComplete(): WorkspaceState {
        val state = loadState()
        if (state.phase == OnboardingPhase.ONBOARDING_COMPLETE) return state

        val completeState = state.copy(
            phase = OnboardingPhase.ONBOARDING_COMPLETE,
            onboardingCompletedAt = clock.now()
        )
        saveState(completeState)
        logger.info { "Onboarding marked complete via explicit override" }
        return completeState
    }

    /**
     * Returns the current workspace state without triggering any transitions.
     */
    fun currentState(): WorkspaceState = loadState()

    /**
     * Ensure workspace is set up. Called at application startup.
     * Idempotent — safe to call multiple times.
     */
    fun ensureWorkspace(): WorkspaceState {
        workspacePath.createDirectories()
        val state = loadState()

        return when (state.phase) {
            OnboardingPhase.ONBOARDING_COMPLETE -> {
                logger.debug { "Workspace already onboarded, skipping" }
                state
            }

            OnboardingPhase.BOOTSTRAP_SEEDED -> {
                checkBootstrapCompletion(state)
            }

            OnboardingPhase.NEW -> {
                handleNewWorkspace(state)
            }
        }
    }

    internal fun loadState(): WorkspaceState {
        if (!stateFile.exists()) {
            return WorkspaceState()
        }
        return try {
            json.decodeFromString<WorkspaceState>(stateFile.readText())
        } catch (e: Exception) {
            logger.warn(e) { "Malformed workspace-state.json, resetting to NEW" }
            WorkspaceState()
        }
    }

    internal fun saveState(state: WorkspaceState) {
        stateDir.createDirectories()
        stateFile.writeText(json.encodeToString(WorkspaceState.serializer(), state))
    }

    private fun handleNewWorkspace(state: WorkspaceState): WorkspaceState {
        // Seed all template files
        var seededCount = 0
        for (filename in TEMPLATE_FILES) {
            val target = workspacePath.resolve(filename)
            if (!target.exists()) {
                val content = loadTemplate(filename)
                if (content != null) {
                    target.writeText(content)
                    seededCount++
                } else {
                    logger.warn { "Template not found on classpath: $filename" }
                }
            } else {
                logger.debug { "Workspace file already exists, skipping: $filename" }
            }
        }

        logger.info { "Workspace seeded with template files ($seededCount new files)" }
        val seededState = state.copy(
            phase = OnboardingPhase.BOOTSTRAP_SEEDED,
            bootstrapSeededAt = clock.now()
        )
        saveState(seededState)
        return seededState
    }

    private fun checkBootstrapCompletion(state: WorkspaceState): WorkspaceState {
        val bootstrapFile = workspacePath.resolve("BOOTSTRAP.md")
        if (!bootstrapFile.exists()) {
            logger.info { "BOOTSTRAP.md deleted — onboarding complete" }
            val completeState = state.copy(
                phase = OnboardingPhase.ONBOARDING_COMPLETE,
                onboardingCompletedAt = clock.now()
            )
            saveState(completeState)
            return completeState
        }
        logger.debug { "BOOTSTRAP.md still present, onboarding in progress" }
        return state
    }
}
