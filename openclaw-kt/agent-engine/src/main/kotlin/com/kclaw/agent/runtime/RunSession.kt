package com.kclaw.agent.runtime

import com.kclaw.protocol.identifiers.SessionKey
import kotlinx.coroutines.Job

data class RunSession(
    val sessionKey: SessionKey,
    val job: Job,
    val startTimeMs: Long
)

data class RunStatus(
    val sessionKey: SessionKey,
    val isRunning: Boolean,
    val elapsedMs: Long
)
