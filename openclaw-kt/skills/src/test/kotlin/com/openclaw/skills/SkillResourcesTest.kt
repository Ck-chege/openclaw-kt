package com.openclaw.skills

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class SkillResourcesTest {

    @TempDir
    lateinit var tempDir: Path

    @Nested
    inner class Scanning {

        @Test
        fun `scan empty directory returns EMPTY`() {
            val dir = tempDir.resolve("empty-skill").createDirectories()
            val resources = SkillResources.scan(dir)
            resources shouldBe SkillResources.EMPTY
            resources.isEmpty shouldBe true
        }

        @Test
        fun `scan directory with scripts returns script relative paths`() {
            val dir = tempDir.resolve("with-scripts").createDirectories()
            val scriptsDir = dir.resolve("scripts").createDirectories()
            scriptsDir.resolve("init.sh").writeText("#!/bin/bash\necho hello")
            scriptsDir.resolve("deploy.py").writeText("print('deploying')")

            val resources = SkillResources.scan(dir)
            resources.scripts shouldContainExactly listOf("scripts/deploy.py", "scripts/init.sh") // sorted
            resources.hasScripts shouldBe true
            resources.references.shouldBeEmpty()
            resources.assets.shouldBeEmpty()
        }

        @Test
        fun `scan directory with all resource types returns all relative paths`() {
            val dir = tempDir.resolve("full-skill").createDirectories()
            dir.resolve("scripts").createDirectories().resolve("run.sh").writeText("#!/bin/bash")
            dir.resolve("references").createDirectories().resolve("guide.md").writeText("# Guide")
            dir.resolve("assets").createDirectories().resolve("template.yml").writeText("key: value")

            val resources = SkillResources.scan(dir)
            resources.scripts shouldContainExactly listOf("scripts/run.sh")
            resources.references shouldContainExactly listOf("references/guide.md")
            resources.assets shouldContainExactly listOf("assets/template.yml")
            resources.isEmpty shouldBe false
        }

        @Test
        fun `scan ignores subdirectories inside resource dirs`() {
            val dir = tempDir.resolve("nested-skill").createDirectories()
            val scriptsDir = dir.resolve("scripts").createDirectories()
            scriptsDir.resolve("run.sh").writeText("#!/bin/bash")
            scriptsDir.resolve("subdir").createDirectories().resolve("nested.sh").writeText("nested")

            val resources = SkillResources.scan(dir)
            resources.scripts shouldContainExactly listOf("scripts/run.sh") // subdir excluded
        }
    }

    @Nested
    inner class Flags {

        @Test
        fun `EMPTY has all false flags`() {
            val empty = SkillResources.EMPTY
            empty.hasScripts shouldBe false
            empty.hasReferences shouldBe false
            empty.hasAssets shouldBe false
            empty.isEmpty shouldBe true
        }

        @Test
        fun `flags reflect content`() {
            val resources = SkillResources(scripts = listOf("scripts/run.sh"))
            resources.hasScripts shouldBe true
            resources.hasReferences shouldBe false
            resources.hasAssets shouldBe false
            resources.isEmpty shouldBe false
        }
    }
}
