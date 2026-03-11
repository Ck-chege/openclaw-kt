package com.openclaw.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.openclaw.skills.SkillLoader
import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * Allows the agent to load and apply a skill's instructions at runtime.
 *
 * When the system prompt lists available skills via `<available_skills>` XML,
 * the agent can call this tool to retrieve a skill's full body (instructions)
 * and follow them for the current interaction.
 *
 * This bridges the gap between skill *discovery* (prompt XML listing) and
 * skill *execution* (actually reading and following the instructions).
 */
class UseSkillTool(
    private val skillLoader: SkillLoader,
    private val workspacePath: () -> Path
) : SimpleTool<UseSkillTool.Args>(
    argsSerializer = Args.serializer(),
    name = "use_skill",
    description = "Load a skill's instructions by name. Call this when a listed skill is relevant to the user's request. Returns the skill's full instructions for you to follow."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The name of the skill to load (from the available_skills listing)")
        val name: String
    )

    override suspend fun execute(args: Args): String {
        val name = args.name.trim()
        if (name.isBlank()) {
            return "Error: Skill name cannot be blank"
        }

        val body = skillLoader.loadFullFromCache(workspacePath(), name)
            ?: return "Error: Skill '$name' not found or has no instructions"

        return body
    }
}