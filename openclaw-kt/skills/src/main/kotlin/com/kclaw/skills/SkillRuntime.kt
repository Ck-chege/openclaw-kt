package com.kclaw.skills

import java.util.concurrent.ConcurrentHashMap

object SkillEnvironment {
    private val activeOverrides = ConcurrentHashMap<String, String>()

    fun get(name: String): String? = activeOverrides[name]

    fun snapshot(): Map<String, String> = activeOverrides.toMap()

    fun apply(overrides: Map<String, String>): () -> Unit {
        if (overrides.isEmpty()) return {}
        val previous = mutableMapOf<String, String?>()
        for ((key, value) in overrides) {
            previous[key] = activeOverrides.put(key, value)
        }
        return {
            for ((key, value) in previous) {
                if (value == null) activeOverrides.remove(key) else activeOverrides[key] = value
            }
        }
    }
}

class SkillRuntime {
    fun applyEnvOverrides(snapshot: SkillSnapshot, config: SkillsConfig): () -> Unit {
        val overrides = buildMap {
            for (skill in snapshot.skills) {
                val entry = config.entries[skill.skillKey] ?: continue
                val allowedKeys = buildSet {
                    skill.primaryEnv?.takeIf { it.isNotBlank() }?.let(::add)
                    addAll(skill.requires.env.filter { it.isNotBlank() })
                }
                for ((key, value) in entry.env) {
                    if (key.isBlank() || value.isBlank()) continue
                    if (!allowedKeys.contains(key) && isDangerousEnvKey(key)) continue
                    putIfAbsent(key, value)
                }
                val primaryEnv = skill.primaryEnv
                val apiKey = entry.apiKey?.trim().orEmpty()
                if (!primaryEnv.isNullOrBlank() && apiKey.isNotBlank() && !isDangerousEnvKey(primaryEnv)) {
                    putIfAbsent(primaryEnv, apiKey)
                }
            }
        }
        return SkillEnvironment.apply(overrides)
    }

    companion object {
        private val blockedPrefixes = listOf("LD_", "DYLD_", "JAVA_", "KOTLIN_")
        private val blockedExact = setOf(
            "PATH",
            "HOME",
            "PWD",
            "SHELL",
            "CLASSPATH",
            "OPENSSL_CONF"
        )

        fun isDangerousEnvKey(name: String): Boolean {
            val normalized = name.trim().uppercase()
            return normalized in blockedExact || blockedPrefixes.any { normalized.startsWith(it) }
        }
    }
}
