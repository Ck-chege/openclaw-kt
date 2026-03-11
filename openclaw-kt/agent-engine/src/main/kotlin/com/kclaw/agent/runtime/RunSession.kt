package com.kclaw.agent.runtime

import com.kclaw.protocol.dto.AgentEvent
import com.kclaw.protocol.dto.QueueAction
import com.kclaw.protocol.identifiers.SessionKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

data class RunSession(
    val sessionKey: SessionKey,
    val job: Job,
    val events: Flow<AgentEvent>,
    val queueChannel: Channel<QueueAction>,
    val startTimeMs: Long
)

data class RunStatus(
    val sessionKey: SessionKey,
    val isRunning: Boolean,
    val elapsedMs: Long
)
