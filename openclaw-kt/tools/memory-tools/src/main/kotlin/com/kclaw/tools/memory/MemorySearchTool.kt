package com.kclaw.tools.memory

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.kclaw.memory.MemoryFacade
import kotlinx.serialization.Serializable

/**
 * Tool for searching the memory system using hybrid (vector + BM25) search.
 */
object MemorySearchTool : SimpleTool<MemorySearchTool.Args>(
    argsSerializer = Args.serializer(),
    name = "memory_search",
    description = "Search your memory/knowledge base using semantic and keyword search. " +
        "Returns relevant chunks from indexed workspace files (markdown, text). " +
        "Use this to recall prior decisions, documented context, or workspace knowledge."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The search query — can be a question, keywords, or natural language description")
        val query: String,
        @property:LLMDescription("Maximum number of results to return (1-20, default: 5)")
        val limit: Int = 5
    )

    /** Set by Main.kt during initialization. Null if memory is disabled. */
    var memoryManager: MemoryFacade? = null

    override suspend fun execute(args: Args): String {
        val manager = memoryManager
            ?: return "Memory system is not enabled. Configure memory in kclaw.json to use this tool."

        if (args.query.isBlank()) {
            return "Error: Search query cannot be empty."
        }

        val effectiveLimit = args.limit.coerceIn(1, 20)

        return try {
            val results = manager.search(args.query, effectiveLimit)
            if (results.isEmpty()) {
                "No results found for: ${args.query}"
            } else {
                buildString {
                    appendLine("Found ${results.size} result(s) for: ${args.query}")
                    appendLine()
                    for ((i, result) in results.withIndex()) {
                        appendLine("${i + 1}. [score: ${"%.3f".format(result.score)}]")
                        appendLine(result.snippet)
                        appendLine()
                    }
                }.trimEnd()
            }
        } catch (e: Exception) {
            "Error searching memory: ${e.message ?: e::class.simpleName}"
        }
    }
}
