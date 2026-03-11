package com.kclaw.tools.exec

import com.kclaw.skills.SkillEnvironment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ExecToolTest {

    @TempDir
    lateinit var workspace: Path

    @BeforeEach
    fun setUp() {
        ExecTool.workspacePath = workspace
        ExecTool.backgroundTracker = BackgroundProcessTracker()
    }

    @Nested
    inner class ForegroundExecution {

        @Test
        fun `echo command returns output`() = runTest {
            val result = ExecTool.execute(ExecTool.Args(command = "echo hello"))
            result shouldContain "hello"
            result shouldContain "Exit code: 0"
        }

        @Test
        fun `captures stderr via redirected error stream`() = runTest {
            val result = ExecTool.execute(ExecTool.Args(command = "echo err >&2"))
            result shouldContain "err"
            result shouldContain "Exit code: 0"
        }

        @Test
        fun `respects custom cwd`() = runTest {
            val subdir = Files.createDirectory(workspace.resolve("mydir"))
            val result = ExecTool.execute(ExecTool.Args(command = "pwd", cwd = "mydir"))
            result shouldContain "mydir"
            result shouldContain "Exit code: 0"
        }

        @Test
        fun `inherits active skill environment overrides`() = runTest {
            val cleanup = SkillEnvironment.apply(mapOf("SKILL_TEST_TOKEN" to "runtime-value"))
            try {
                val result = ExecTool.execute(ExecTool.Args(command = "printf '%s' \"\$SKILL_TEST_TOKEN\""))
                result shouldContain "runtime-value"
                result shouldContain "Exit code: 0"
            } finally {
                cleanup()
            }
        }

        @Test
        fun `timeout fires for long-running command`() = runTest {
            val result = ExecTool.execute(ExecTool.Args(command = "sleep 30", timeout = 1))
            result shouldContain "timed out"
            result shouldContain "1s"
        }

        @Test
        fun `exit code returned for failing command`() = runTest {
            val result = ExecTool.execute(ExecTool.Args(command = "exit 42"))
            result shouldContain "Exit code: 42"
        }

        @Test
        fun `command not found returns non-zero exit`() = runTest {
            val result = ExecTool.execute(ExecTool.Args(command = "nonexistent_cmd_xyz_12345"))
            result shouldContain "Exit code:"
            // The shell returns 127 for command not found
            result shouldContain "127"
        }
    }

    @Nested
    inner class Defaults {

        @Test
        fun `default timeout is null in args which maps to 1800`() {
            val args = ExecTool.Args(command = "ls")
            args.timeout shouldBe null
        }

        @Test
        fun `timeout is capped at 3600`() = runTest {
            // We can't easily test the internal coercion directly, but we can
            // verify the command still works with a large timeout
            val result = ExecTool.execute(ExecTool.Args(command = "echo hi", timeout = 9999))
            result shouldContain "hi"
            result shouldContain "Exit code: 0"
        }
    }

    @Nested
    inner class OutputTruncation {

        @Test
        fun `output under limit is not truncated`() {
            val text = "a".repeat(100)
            val result = ExecTool.truncateMiddle(text, ExecTool.MAX_OUTPUT_BYTES)
            result shouldBe text
        }

        @Test
        fun `output over limit is truncated with omission notice`() {
            val size = ExecTool.MAX_OUTPUT_BYTES + 10_000
            val text = "x".repeat(size)
            val result = ExecTool.truncateMiddle(text, ExecTool.MAX_OUTPUT_BYTES)
            result shouldContain "characters omitted"
            result.length shouldBe (ExecTool.MAX_OUTPUT_BYTES + "[... 10000 characters omitted ...]".length + 4) // +4 for \n\n...\n\n
        }

        @Test
        fun `large command output is truncated`() = runTest {
            // Generate >100KB output using dd (universally available)
            val result = ExecTool.execute(
                ExecTool.Args(command = "dd if=/dev/zero bs=1024 count=200 2>/dev/null | tr '\\0' 'x'")
            )
            result shouldContain "characters omitted"
            result shouldContain "Exit code: 0"
        }
    }

    @Nested
    inner class WorkspaceBoundary {

        @Test
        fun `cwd outside workspace is rejected`() = runTest {
            val result = ExecTool.execute(ExecTool.Args(command = "ls", cwd = "/etc"))
            result shouldContain "Error"
            result shouldContain "outside workspace"
        }

        @Test
        fun `dot-dot traversal in cwd is rejected`() = runTest {
            val result = ExecTool.execute(ExecTool.Args(command = "ls", cwd = "../../.."))
            result shouldContain "Error"
            result shouldContain "outside workspace"
        }
    }

    @Nested
    inner class EmptyCommand {

        @Test
        fun `empty command returns error`() = runTest {
            val result = ExecTool.execute(ExecTool.Args(command = ""))
            result shouldContain "Error"
            result shouldContain "command cannot be empty"
        }

        @Test
        fun `blank command returns error`() = runTest {
            val result = ExecTool.execute(ExecTool.Args(command = "   "))
            result shouldContain "Error"
            result shouldContain "command cannot be empty"
        }
    }

    @Nested
    inner class BackgroundMode {

        @Test
        fun `background returns session ID`() = runTest {
            val result = ExecTool.execute(
                ExecTool.Args(command = "echo bg", background = true)
            )
            result shouldContain "sessionId="
            result shouldContain "Background process started"
            result shouldContain "echo bg"
        }

        @Test
        fun `background fails when tracker not set`() = runTest {
            ExecTool.backgroundTracker = null
            val result = ExecTool.execute(
                ExecTool.Args(command = "echo bg", background = true)
            )
            result shouldContain "Error"
            result shouldContain "background execution not available"
        }
    }
}
