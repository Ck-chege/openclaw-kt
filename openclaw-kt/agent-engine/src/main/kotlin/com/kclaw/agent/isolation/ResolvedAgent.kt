package com.kclaw.agent.isolation

import com.kclaw.agent.workspace.WorkspaceLoader
import com.kclaw.gateway.config.AgentToolOverrides
import com.kclaw.protocol.identifiers.AgentId
import com.kclaw.protocol.identifiers.ModelId

data class ResolvedAgent(
    val agentId: AgentId,
    val modelId: ModelId,
    val workspace: WorkspaceLoader,
    val toolProfile: String,
    val toolOverrides: AgentToolOverrides? = null
)
