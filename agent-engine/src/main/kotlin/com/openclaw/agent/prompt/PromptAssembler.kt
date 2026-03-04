package com.openclaw.agent.prompt

import kotlinx.datetime.toLocalDateTime

object PromptAssembler {
    fun assembleSystemPrompt(ctx: PromptContext): String = systemPrompt {
        if (ctx.bootstrap.isNotBlank()) {
            section("BOOTSTRAP") { ctx.bootstrap }
        }

        section("SOUL") { ctx.soul }
        section("IDENTITY") { ctx.identity }
        section("AGENTS") { ctx.agents }
        section("USER") { ctx.user }
        section("TOOLS") { ctx.tools }

        if (ctx.skillsXml.isNotBlank()) {
            raw(ctx.skillsXml)
        }

        if (ctx.channelName != null) {
            section("CHANNEL") {
                buildString {
                    appendLine("Channel: ${ctx.channelName}")
                    if (ctx.channelCapabilities != null) {
                        append("Capabilities: ${ctx.channelCapabilities}")
                    }
                }.trim()
            }
        }

        section("MEMORY") { ctx.memory }

        section("RUNTIME") {
            val localTime = ctx.currentTime.toLocalDateTime(ctx.timeZone)
            buildString {
                appendLine("Date: ${localTime.date}")
                appendLine("Time: ${localTime.time}")
                appendLine("Timezone: ${ctx.timeZone.id}")
                if (ctx.modelId != null) {
                    appendLine("Model: ${ctx.modelId.raw}")
                }
                if (ctx.channelName != null) {
                    appendLine("Channel: ${ctx.channelName}")
                }
                append("OS: ${ctx.osInfo}")
            }
        }
    }
}
