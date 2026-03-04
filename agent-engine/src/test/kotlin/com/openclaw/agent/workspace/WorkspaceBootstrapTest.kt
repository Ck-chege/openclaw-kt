package com.openclaw.agent.workspace

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

class WorkspaceBootstrapTest {

    @TempDir
    lateinit var tempDir: Path

    /** Fixed clock for deterministic timestamps in tests. */
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1700000000000)
    }

    private fun bootstrap(path: Path = tempDir) = WorkspaceBootstrap(path, fixedClock)

    @Nested
    inner class FreshWorkspace {

        @Test
        fun `seeds all 8 template files in empty workspace`() {
            val state = bootstrap().ensureWorkspace()
            state.phase shouldBe OnboardingPhase.BOOTSTRAP_SEEDED

            val files = tempDir.listDirectoryEntries("*.md").map { it.fileName.toString() }
            files shouldContainAll WorkspaceBootstrap.TEMPLATE_FILES
        }

        @Test
        fun `creates openclaw state directory`() {
            bootstrap().ensureWorkspace()
            tempDir.resolve(".openclawk").exists() shouldBe true
        }

        @Test
        fun `creates workspace-state json`() {
            bootstrap().ensureWorkspace()
            tempDir.resolve(".openclawk/workspace-state.json").exists() shouldBe true
        }

        @Test
        fun `state file contains BOOTSTRAP_SEEDED phase`() {
            bootstrap().ensureWorkspace()
            val content = tempDir.resolve(".openclawk/workspace-state.json").readText()
            content shouldContain "BOOTSTRAP_SEEDED"
        }

        @Test
        fun `sets bootstrapSeededAt timestamp`() {
            val state = bootstrap().ensureWorkspace()
            state.bootstrapSeededAt.shouldNotBeNull()
            state.bootstrapSeededAt shouldBe fixedClock.now()
        }

        @Test
        fun `onboardingCompletedAt is null after seeding`() {
            val state = bootstrap().ensureWorkspace()
            state.onboardingCompletedAt shouldBe null
        }

        @Test
        fun `seeded SOUL md has non-trivial content`() {
            bootstrap().ensureWorkspace()
            val content = tempDir.resolve("SOUL.md").readText()
            content.shouldNotBeBlank()
            content shouldContain "Soul"
        }

        @Test
        fun `seeded BOOTSTRAP md contains bootstrap instructions`() {
            bootstrap().ensureWorkspace()
            val content = tempDir.resolve("BOOTSTRAP.md").readText()
            content shouldContain "Bootstrap"
            content shouldContain "woke up"
        }

        @Test
        fun `creates workspace directory if it does not exist`() {
            val nonExistent = tempDir.resolve("new-workspace")
            bootstrap(nonExistent).ensureWorkspace()
            nonExistent.exists() shouldBe true
        }
    }

    @Nested
    inner class WriteIfMissing {

        @Test
        fun `does not overwrite existing SOUL md`() {
            tempDir.resolve("SOUL.md").writeText("Custom soul content")
            bootstrap().ensureWorkspace()
            tempDir.resolve("SOUL.md").readText() shouldBe "Custom soul content"
        }

        @Test
        fun `does not overwrite existing USER md`() {
            tempDir.resolve("USER.md").writeText("My user info")
            bootstrap().ensureWorkspace()
            tempDir.resolve("USER.md").readText() shouldBe "My user info"
        }

        @Test
        fun `seeds only missing files when some already exist`() {
            tempDir.resolve("SOUL.md").writeText("short")  // short = not legacy
            tempDir.resolve("USER.md").writeText("user")
            bootstrap().ensureWorkspace()

            // Existing files preserved
            tempDir.resolve("SOUL.md").readText() shouldBe "short"
            tempDir.resolve("USER.md").readText() shouldBe "user"

            // Missing files seeded
            tempDir.resolve("BOOTSTRAP.md").exists() shouldBe true
            tempDir.resolve("IDENTITY.md").exists() shouldBe true
            tempDir.resolve("AGENTS.md").exists() shouldBe true
        }
    }

    @Nested
    inner class LegacyDetection {

        @Test
        fun `SOUL md over 50 chars transitions directly to COMPLETE`() {
            val longSoul = "You are a personal assistant named Claude. You help with coding tasks and general inquiries."
            tempDir.resolve("SOUL.md").writeText(longSoul)

            val state = bootstrap().ensureWorkspace()
            state.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
        }

        @Test
        fun `legacy detection sets onboardingCompletedAt`() {
            tempDir.resolve("SOUL.md").writeText("A".repeat(51))
            val state = bootstrap().ensureWorkspace()
            state.onboardingCompletedAt.shouldNotBeNull()
        }

        @Test
        fun `legacy detection does not seed BOOTSTRAP md`() {
            tempDir.resolve("SOUL.md").writeText("A".repeat(51))
            bootstrap().ensureWorkspace()
            tempDir.resolve("BOOTSTRAP.md").exists() shouldBe false
        }

        @Test
        fun `SOUL md at exactly 50 chars is NOT legacy`() {
            tempDir.resolve("SOUL.md").writeText("A".repeat(50))
            val state = bootstrap().ensureWorkspace()
            state.phase shouldBe OnboardingPhase.BOOTSTRAP_SEEDED
        }

        @Test
        fun `SOUL md at 51 chars IS legacy`() {
            tempDir.resolve("SOUL.md").writeText("A".repeat(51))
            val state = bootstrap().ensureWorkspace()
            state.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
        }

        @Test
        fun `whitespace-only SOUL md is not legacy`() {
            tempDir.resolve("SOUL.md").writeText("   \n\t  \n   " + " ".repeat(100))
            val state = bootstrap().ensureWorkspace()
            // After trimming, it's empty — not legacy
            state.phase shouldBe OnboardingPhase.BOOTSTRAP_SEEDED
        }
    }

    @Nested
    inner class BootstrapCompletion {

        @Test
        fun `BOOTSTRAP_SEEDED with BOOTSTRAP md deleted transitions to COMPLETE`() {
            // First run: seed
            val bs = bootstrap()
            bs.ensureWorkspace()

            // Simulate agent deleting BOOTSTRAP.md
            tempDir.resolve("BOOTSTRAP.md").deleteIfExists()

            // Second run: check completion
            val state = bs.ensureWorkspace()
            state.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
        }

        @Test
        fun `BOOTSTRAP_SEEDED with BOOTSTRAP md still present stays SEEDED`() {
            val bs = bootstrap()
            bs.ensureWorkspace()

            // BOOTSTRAP.md still exists
            val state = bs.ensureWorkspace()
            state.phase shouldBe OnboardingPhase.BOOTSTRAP_SEEDED
        }

        @Test
        fun `completion sets onboardingCompletedAt`() {
            val bs = bootstrap()
            bs.ensureWorkspace()
            tempDir.resolve("BOOTSTRAP.md").deleteIfExists()

            val state = bs.ensureWorkspace()
            state.onboardingCompletedAt.shouldNotBeNull()
        }
    }

    @Nested
    inner class OnboardingComplete {

        @Test
        fun `ONBOARDING_COMPLETE is a no-op`() {
            val bs = bootstrap()
            // Force to complete
            tempDir.resolve("SOUL.md").writeText("A".repeat(100))
            bs.ensureWorkspace()

            // Second call should be no-op
            val state = bs.ensureWorkspace()
            state.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
        }

        @Test
        fun `ONBOARDING_COMPLETE does not modify existing files`() {
            val bs = bootstrap()
            tempDir.resolve("SOUL.md").writeText("A".repeat(100))
            bs.ensureWorkspace()

            // Modify a file
            tempDir.resolve("SOUL.md").writeText("Modified content")

            // Should not re-seed
            bs.ensureWorkspace()
            tempDir.resolve("SOUL.md").readText() shouldBe "Modified content"
        }
    }

    @Nested
    inner class StatePersistence {

        @Test
        fun `state survives save and reload`() {
            val bs = bootstrap()
            val state = WorkspaceState(
                version = 1,
                phase = OnboardingPhase.BOOTSTRAP_SEEDED,
                bootstrapSeededAt = fixedClock.now()
            )
            bs.saveState(state)
            val loaded = bs.loadState()
            loaded shouldBe state
        }

        @Test
        fun `malformed JSON falls back to NEW`() {
            tempDir.resolve(".openclawk").createDirectories()
            tempDir.resolve(".openclawk/workspace-state.json").writeText("not valid json{{{")

            val loaded = bootstrap().loadState()
            loaded shouldBe WorkspaceState()
        }

        @Test
        fun `missing state file returns default NEW state`() {
            val loaded = bootstrap().loadState()
            loaded.phase shouldBe OnboardingPhase.NEW
            loaded.version shouldBe 1
        }

        @Test
        fun `empty state file falls back to NEW`() {
            tempDir.resolve(".openclawk").createDirectories()
            tempDir.resolve(".openclawk/workspace-state.json").writeText("")

            val loaded = bootstrap().loadState()
            loaded shouldBe WorkspaceState()
        }
    }

    @Nested
    inner class TemplateLoading {

        @Test
        fun `all 8 templates exist on classpath`() {
            for (filename in WorkspaceBootstrap.TEMPLATE_FILES) {
                val content = WorkspaceBootstrap.loadTemplate(filename)
                content.shouldNotBeNull()
                content.shouldNotBeBlank()
            }
        }

        @Test
        fun `nonexistent template returns null`() {
            val content = WorkspaceBootstrap.loadTemplate("NONEXISTENT.md")
            content shouldBe null
        }
    }

    @Nested
    inner class MarkOnboardingComplete {

        @Test
        fun `marks NEW workspace as complete`() {
            val bs = bootstrap()
            bs.ensureWorkspace()
            val state = bs.markOnboardingComplete()
            state.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
            state.onboardingCompletedAt.shouldNotBeNull()
        }

        @Test
        fun `marks BOOTSTRAP_SEEDED as complete`() {
            val bs = bootstrap()
            bs.ensureWorkspace() // -> BOOTSTRAP_SEEDED
            val state = bs.markOnboardingComplete()
            state.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
        }

        @Test
        fun `already complete is a no-op`() {
            val bs = bootstrap()
            tempDir.resolve("SOUL.md").writeText("A".repeat(100))
            bs.ensureWorkspace() // -> ONBOARDING_COMPLETE
            val state = bs.markOnboardingComplete()
            state.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
        }

        @Test
        fun `persists the complete state`() {
            val bs = bootstrap()
            bs.ensureWorkspace()
            bs.markOnboardingComplete()

            val reloaded = bootstrap().loadState()
            reloaded.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
        }
    }

    @Nested
    inner class CurrentState {

        @Test
        fun `returns current state without transitions`() {
            val bs = bootstrap()
            bs.ensureWorkspace() // -> BOOTSTRAP_SEEDED
            val state = bs.currentState()
            state.phase shouldBe OnboardingPhase.BOOTSTRAP_SEEDED
        }

        @Test
        fun `returns NEW for fresh workspace`() {
            val state = bootstrap().currentState()
            state.phase shouldBe OnboardingPhase.NEW
        }
    }

    @Nested
    inner class Idempotency {

        @Test
        fun `calling ensureWorkspace twice on fresh workspace is idempotent`() {
            val bs = bootstrap()
            val first = bs.ensureWorkspace()
            val second = bs.ensureWorkspace()

            first.phase shouldBe OnboardingPhase.BOOTSTRAP_SEEDED
            second.phase shouldBe OnboardingPhase.BOOTSTRAP_SEEDED
        }

        @Test
        fun `calling ensureWorkspace after completion is idempotent`() {
            val bs = bootstrap()
            tempDir.resolve("SOUL.md").writeText("A".repeat(100))
            val first = bs.ensureWorkspace()
            val second = bs.ensureWorkspace()
            val third = bs.ensureWorkspace()

            first.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
            second.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
            third.phase shouldBe OnboardingPhase.ONBOARDING_COMPLETE
        }
    }
}
