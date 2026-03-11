package com.kclaw.gateway

import java.nio.file.Path

/**
 * Single source of truth for all KClaw filesystem paths.
 * Every module resolves paths through here — no hardcoded ~/.kclaw paths elsewhere.
 */
object AppPaths {

    /**
     * Base directory name under user home.
     */
    const val BASE_DIR_NAME = ".kclaw"

    /** Root of all KClaw data: ~/.kclaw/ */
    val base: Path get() = Path.of(System.getProperty("user.home"), BASE_DIR_NAME)

    /** Config file: ~/.kclaw/kclaw.conf */
    val config: Path get() = base.resolve("kclaw.conf")

    /** Default workspace: ~/.kclaw/workspace/ */
    val workspace: Path get() = base.resolve("workspace")

    /** Session store: ~/.kclaw/sessions/ */
    val sessions: Path get() = base.resolve("sessions")

    /** Koog checkpoint store: ~/.kclaw/checkpoints.sqlite */
    val checkpoints: Path get() = base.resolve("checkpoints.sqlite")

    /** Agent trace output dir: ~/.kclaw/traces/ */
    val traces: Path get() = base.resolve("traces")

    /** Global user skills: ~/.kclaw/skills/ */
    val skills: Path get() = base.resolve("skills")

    /** Workspace state dir (inside a workspace): {workspace}/.kclaw/ */
    fun workspaceStateDir(workspacePath: Path): Path = workspacePath.resolve(BASE_DIR_NAME)
}
