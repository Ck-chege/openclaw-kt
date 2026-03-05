package com.openclaw.tools

import java.nio.file.Path

/**
 * Validates that file paths stay within the workspace boundary.
 * Prevents path traversal attacks (../ escapes) and symlink escapes.
 *
 * Matches OpenClaw's path-policy.ts boundary enforcement.
 */
object WorkspaceBoundary {

    /**
     * Resolve a path argument relative to the workspace root, then verify
     * the resolved real path is within the workspace boundary.
     *
     * @param path The raw path from tool arguments (absolute or relative)
     * @param workspacePath The workspace root boundary
     * @return The resolved, normalized absolute path
     * @throws WorkspaceBoundaryViolation if path escapes workspace
     */
    fun resolve(path: String, workspacePath: Path): Path {
        val candidate = if (Path.of(path).isAbsolute) {
            Path.of(path).normalize()
        } else {
            workspacePath.resolve(path).normalize()
        }

        if (!isWithin(candidate, workspacePath)) {
            throw WorkspaceBoundaryViolation(path, workspacePath)
        }

        // If the file exists, resolve symlinks to check the real path
        val realCandidate = if (candidate.toFile().exists()) {
            candidate.toRealPath()
        } else {
            // For non-existent files, check the parent directory if it exists
            val parent = candidate.parent
            if (parent != null && parent.toFile().exists()) {
                val realParent = parent.toRealPath()
                if (!isWithin(realParent, workspacePath)) {
                    throw WorkspaceBoundaryViolation(path, workspacePath)
                }
            }
            candidate
        }

        if (realCandidate != candidate && !isWithin(realCandidate, workspacePath)) {
            throw WorkspaceBoundaryViolation(path, workspacePath)
        }

        return candidate
    }

    /**
     * Check whether a resolved path is within the workspace boundary.
     */
    fun isWithin(resolvedPath: Path, workspacePath: Path): Boolean {
        val normalizedResolved = resolvedPath.normalize().toAbsolutePath()
        val normalizedWorkspace = workspacePath.normalize().toAbsolutePath()
        return normalizedResolved.startsWith(normalizedWorkspace)
    }

    /**
     * Exception thrown when a path escapes the workspace boundary.
     */
    class WorkspaceBoundaryViolation(path: String, workspace: Path) :
        IllegalArgumentException("Path '$path' is outside workspace '${workspace.toAbsolutePath()}'")
}
