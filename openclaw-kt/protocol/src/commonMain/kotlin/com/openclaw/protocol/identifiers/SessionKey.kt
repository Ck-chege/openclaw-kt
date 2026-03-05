package com.openclaw.protocol.identifiers

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Hierarchical session key encoding routing context.
 * Format: agent:channel:scope:peer
 * Examples:
 *   "main:main:*:main" — single shared session
 *   "main:telegram:*:dm:6614828781" — per-peer Telegram DM
 *   "main:discord:channel:14775920" — Discord channel session
 *   "trading:main:subagent:abc123" — subagent session
 *   "main:main:cron:9467cf95" — cron job session
 */
@JvmInline
@Serializable
value class SessionKey(val raw: String) {
    val agentId: String get() = raw.substringBefore(':')
    val segments: List<String> get() = raw.split(':')
    val segmentCount: Int get() = segments.size
    val hasMinimumSegments: Boolean get() = segmentCount >= MIN_SEGMENTS
    val channel: String? get() = segments.getOrNull(1)
    val scope: String? get() = segments.getOrNull(2)
    val peerId: String? get() {
        val segs = segments
        return if (segs.size >= 5 && segs[3] == "dm") segs[4] else segs.getOrNull(3)
    }
    val isSubagent: Boolean get() = raw.contains(":subagent:")
    val isCron: Boolean get() = raw.contains(":cron:")

    init {
        require(hasMinimumSegments) { "Invalid session key format: $raw (expected at least $MIN_SEGMENTS colon-delimited segments)" }
    }

    companion object {
        const val MIN_SEGMENTS = 4
        fun main(agentId: String = "main") = SessionKey("$agentId:main:*:main")
        fun dm(agentId: String, channel: String, peerId: String) = SessionKey("$agentId:$channel:*:dm:$peerId")
        fun cron(agentId: String, jobId: String) = SessionKey("$agentId:main:cron:$jobId")
        fun subagent(parentKey: SessionKey, subagentId: String) = SessionKey("${parentKey.agentId}:${parentKey.channel}:subagent:$subagentId")
    }
}
