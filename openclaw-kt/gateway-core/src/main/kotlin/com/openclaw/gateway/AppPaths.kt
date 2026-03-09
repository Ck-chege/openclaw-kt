package com.openclaw.gateway

import java.nio.file.Path

/**
 * Single source of truth for all OpenClaw filesystem paths.
 * Every module resolves paths through here — no hardcoded ~/.openclaw or ~/.openclawk elsewhere.
 */
object AppPaths {

    /**
     * Base directory name under user home.
     * Intentionally ".openclawk" (with trailing 'k') so the Kotlin gateway
     * does not conflict with the JS/Node.js OpenClaw installation (".openclaw")
     * when both are present on the same machine.
     */
    const val BASE_DIR_NAME = ".openclawk"

    /** Root of all OpenClaw data: ~/.openclawk/ */
    val base: Path get() = Path.of(System.getProperty("user.home"), BASE_DIR_NAME)

    /** Config file: ~/.openclawk/openclaw.conf */
    val config: Path get() = base.resolve("openclaw.conf")

    /** Default workspace: ~/.openclawk/workspace/ */
    val workspace: Path get() = base.resolve("workspace")

    /** Session store: ~/.openclawk/sessions/ */
    val sessions: Path get() = base.resolve("sessions")

    /** Koog checkpoint store: ~/.openclawk/checkpoints.sqlite */
    val checkpoints: Path get() = base.resolve("checkpoints.sqlite")

    /** Agent trace output dir: ~/.openclawk/traces/ */
    val traces: Path get() = base.resolve("traces")

    /** Global user skills: ~/.openclawk/skills/ */
    val skills: Path get() = base.resolve("skills")

    /** Workspace state dir (inside a workspace): {workspace}/.openclawk/ */
    fun workspaceStateDir(workspacePath: Path): Path = workspacePath.resolve(BASE_DIR_NAME)
}
