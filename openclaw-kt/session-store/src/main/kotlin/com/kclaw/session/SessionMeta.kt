package com.kclaw.session

import com.kclaw.protocol.identifiers.SessionKey
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SessionMeta(
    val key: SessionKey,
    val agentId: String,
    val channel: String? = null,
    val modelId: String? = null,
    val createdAt: Instant,
    val lastMessageAt: Instant? = null,
    val messageCount: Int = 0,
    val compactionCount: Int = 0,
    val totalTokens: Long = 0,
    val fileId: String
)
