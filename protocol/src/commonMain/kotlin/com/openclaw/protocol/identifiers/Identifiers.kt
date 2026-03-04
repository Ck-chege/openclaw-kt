package com.openclaw.protocol.identifiers

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class AgentId(val raw: String) {
    init { require(raw.isNotBlank()) { "AgentId cannot be blank" } }
    override fun toString(): String = raw
}

@JvmInline
@Serializable
value class ModelId(val raw: String) {
    /** Provider prefix, e.g., "anthropic" from "anthropic:claude-sonnet-4-5" */
    val provider: String get() = if (':' in raw) raw.substringBefore(':') else raw

    /** Model name, e.g., "claude-sonnet-4-5" from "anthropic:claude-sonnet-4-5" */
    val model: String get() = if (':' in raw) raw.substringAfter(':') else raw

    init { require(raw.isNotBlank()) { "ModelId cannot be blank" } }
    override fun toString(): String = raw
}

@JvmInline
@Serializable
value class TokenCount(val count: Int) {
    operator fun plus(other: TokenCount) = TokenCount(count + other.count)
    operator fun minus(other: TokenCount) = TokenCount(count - other.count)
    operator fun compareTo(other: TokenCount): Int = count.compareTo(other.count)
    fun exceedsLimit(limit: TokenCount): Boolean = count > limit.count

    init { require(count >= 0) { "TokenCount cannot be negative: $count" } }
    override fun toString(): String = "$count tokens"
}
