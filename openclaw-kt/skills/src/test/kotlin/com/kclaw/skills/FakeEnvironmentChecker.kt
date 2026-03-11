package com.kclaw.skills

/**
 * Test double for [EnvironmentChecker] — no real process spawning.
 */
class FakeEnvironmentChecker(
    private val binaries: Set<String> = emptySet(),
    private val envVars: Set<String> = emptySet(),
    private val os: String = "linux"
) : EnvironmentChecker() {

    override fun hasBinary(name: String): Boolean = name in binaries
    override fun hasEnvVar(name: String): Boolean = name in envVars
    override fun currentOs(): String = os
}
