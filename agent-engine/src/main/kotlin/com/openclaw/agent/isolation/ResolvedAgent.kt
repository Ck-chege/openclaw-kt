package com.openclaw.agent.isolation

import com.openclaw.agent.workspace.WorkspaceLoader
import com.openclaw.gateway.config.AgentToolOverrides
import com.openclaw.protocol.identifiers.AgentId
import com.openclaw.protocol.identifiers.ModelId

data class ResolvedAgent(
    val agentId: AgentId,
    val modelId: ModelId,
    val workspace: WorkspaceLoader,
    val toolProfile: String,
    val toolOverrides: AgentToolOverrides? = null
)
