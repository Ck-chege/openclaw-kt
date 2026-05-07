package com.kclaw.gateway.config

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ConfigErrorTest {

    @Test
    fun `FileParseError carries path and cause`() {
        val path = Path.of("/home/user/.kclaw/kclaw.json")
        val cause = RuntimeException("Unexpected character at line 3")

        val error = ConfigError.FileParseError(path, cause)

        error.path shouldBe path
        error.cause shouldBe cause
        error.message shouldContain path.toString()
        error.message shouldContain "Unexpected character at line 3"
    }

    @Test
    fun `InvalidValue carries config path and expected description`() {
        val cause = IllegalArgumentException("not an int")

        val error = ConfigError.InvalidValue("gateway.port", "integer", cause)

        error.configPath shouldBe "gateway.port"
        error.expected shouldBe "integer"
        error.cause shouldBe cause
        error.message shouldContain "gateway.port"
        error.message shouldContain "integer"
    }

    @Test
    fun `InvalidValue works without cause`() {
        val error = ConfigError.InvalidValue("reload.mode", "one of HYBRID, HOT, RESTART, OFF")

        error.cause shouldBe null
        error.configPath shouldBe "reload.mode"
        error.message shouldContain "reload.mode"
    }

    @Test
    fun `both subtypes are instances of ConfigError`() {
        val fileError: ConfigError = ConfigError.FileParseError(
            Path.of("/tmp/test.conf"),
            RuntimeException("bad")
        )
        val valueError: ConfigError = ConfigError.InvalidValue("key", "string")

        fileError.shouldBeInstanceOf<ConfigError>()
        valueError.shouldBeInstanceOf<ConfigError>()
    }

    @Test
    fun `sealed class enables exhaustive when matching`() {
        val errors: List<ConfigError> = listOf(
            ConfigError.FileParseError(Path.of("/tmp/a.conf"), RuntimeException("parse")),
            ConfigError.InvalidValue("x", "y")
        )

        val messages = errors.map { error ->
            when (error) {
                is ConfigError.FileParseError -> "file: ${error.path}"
                is ConfigError.InvalidValue -> "value: ${error.configPath}"
            }
        }

        messages[0] shouldBe "file: /tmp/a.conf"
        messages[1] shouldBe "value: x"
    }
}
