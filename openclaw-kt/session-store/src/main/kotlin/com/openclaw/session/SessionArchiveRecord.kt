package com.openclaw.session

import com.openclaw.protocol.identifiers.SessionKey
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SessionArchiveRecord(
    val sessionKey: SessionKey,
    val archivedFileId: String,
    val archivedPath: String,
    val previousArchiveFileId: String? = null,
    val compactedAt: Instant,
    val messagesCompacted: Int,
    val tokensBefore: Int,
    val tokensAfter: Int
)
