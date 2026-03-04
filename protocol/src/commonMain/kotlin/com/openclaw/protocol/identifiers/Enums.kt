package com.openclaw.protocol.identifiers

import kotlinx.serialization.Serializable

@Serializable
enum class SessionScope {
    MAIN,
    PER_PEER,
    PER_CHANNEL_PEER,
    SUBAGENT,
    CRON
}

@Serializable
enum class ChatType {
    DM,
    GROUP,
    CHANNEL,
    THREAD
}

@Serializable
enum class OperatorLevel {
    ADMIN,
    WRITE,
    READ,
    APPROVALS
}

@Serializable
enum class DmPolicy {
    PAIRING,
    ALLOWLIST,
    OPEN
}

@Serializable
enum class QueueMode {
    REPLACE,
    STEER,
    IGNORE
}
