package com.openclaw.skills

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class SkillsWatcherServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `watcher refreshes snapshot version after skill file changes`() = runBlocking {
        val workspace = tempDir.resolve("workspace").createDirectories()
        val skillDir = workspace.resolve("skills").resolve("search").createDirectories()
        val skillFile = skillDir.resolve("SKILL.md")
        skillFile.writeText(
            """
            |---
            |name: search
            |description: Search docs
            |---
            |Use search.
            """.trimMargin()
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val loader = SkillLoader()
        val watcher = SkillsWatcherService(loader, scope)
        try {
            val initial = loader.buildSnapshot(workspace)
            initial.version shouldBe 0L

            watcher.ensureWatching(
                workspacePath = workspace,
                config = SkillsConfig(load = SkillsLoadConfig(watch = true, watchDebounceMs = 100))
            )

            delay(150)
            skillFile.writeText(
                """
                |---
                |name: search
                |description: Updated
                |---
                |Use updated search.
                """.trimMargin()
            )

            withTimeout(5_000) {
                while (true) {
                    if (loader.buildSnapshot(workspace).version >= 1L) {
                        break
                    }
                    delay(100)
                }
            }
        } finally {
            watcher.stopAll()
            scope.cancel()
        }
    }
}
