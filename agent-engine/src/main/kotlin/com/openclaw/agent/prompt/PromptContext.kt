package com.openclaw.agent.prompt

import com.openclaw.protocol.identifiers.ModelId
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

data class PromptContext(
    val soul: String = "",
    val agents: String = "",
    val user: String = "",
    val tools: String = "",
    val memory: String = "",
    val identity: String = "",
    val bootstrap: String = "",
    val heartbeat: String = "",
    val skillsXml: String = "",
    val channelName: String? = null,
    val channelCapabilities: String? = null,
    val modelId: ModelId? = null,
    val currentTime: Instant,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val osInfo: String = System.getProperty("os.name") + " " + System.getProperty("os.version")
)
