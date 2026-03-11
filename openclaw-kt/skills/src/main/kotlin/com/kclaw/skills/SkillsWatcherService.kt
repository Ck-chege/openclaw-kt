package com.kclaw.skills

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val watcherLogger = KotlinLogging.logger {}

class SkillsWatcherService(
    private val loader: SkillLoader,
    private val scope: CoroutineScope,
    private val watchServiceFactory: () -> WatchService = { FileSystems.getDefault().newWatchService() }
) {
    private data class WorkspaceWatchRegistration(
        val roots: Set<String>,
        val debounceMs: Long,
        val job: Job
    )

    private val registrations = ConcurrentHashMap<String, WorkspaceWatchRegistration>()

    fun ensureWatching(
        workspacePath: Path,
        config: SkillsConfig = SkillsConfig(),
        bundledSkillsPath: Path? = null,
        pluginSkillsPaths: List<Path> = emptyList()
    ) {
        val workspaceKey = workspacePath.toAbsolutePath().normalize().toString()
        if (!config.load.watch) {
            stopWatching(workspacePath)
            return
        }

        val sources = SkillScanner.defaultSources(
            workspacePath = workspacePath,
            config = config,
            bundledSkillsPath = bundledSkillsPath,
            pluginSkillsPaths = pluginSkillsPaths
        )
        val roots = sources.map { it.path.toAbsolutePath().normalize().toString() }.toSet()
        val debounceMs = config.load.watchDebounceMs.toLong()
        val existing = registrations[workspaceKey]
        if (existing != null && existing.roots == roots && existing.debounceMs == debounceMs && existing.job.isActive) {
            return
        }

        stopWatching(workspacePath)
        val job = scope.launch(Dispatchers.IO) {
            watchWorkspace(
                workspacePath = workspacePath,
                roots = sources.map { it.path },
                debounceMs = debounceMs
            )
        }
        registrations[workspaceKey] = WorkspaceWatchRegistration(roots, debounceMs, job)
    }

    fun stopWatching(workspacePath: Path) {
        val workspaceKey = workspacePath.toAbsolutePath().normalize().toString()
        registrations.remove(workspaceKey)?.job?.cancel()
    }

    fun stopAll() {
        registrations.values.forEach { it.job.cancel() }
        registrations.clear()
    }

    private suspend fun watchWorkspace(
        workspacePath: Path,
        roots: List<Path>,
        debounceMs: Long
    ) {
        val workspaceKey = workspacePath.toAbsolutePath().normalize().toString()
        val watchService = watchServiceFactory()
        val normalizedRoots = roots.map { it.toAbsolutePath().normalize() }
        val keyToDir = mutableMapOf<WatchKey, Path>()
        val watchedDirs = mutableSetOf<Path>()

        fun registerDir(dir: Path) {
            val normalized = dir.toAbsolutePath().normalize()
            if (!normalized.exists() || !normalized.isDirectory() || normalized in watchedDirs) return
            try {
                val key = normalized.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )
                watchedDirs.add(normalized)
                keyToDir[key] = normalized
            } catch (e: Exception) {
                watcherLogger.debug(e) { "Failed to register skills watch dir: $normalized" }
            }
        }

        fun refreshRegistrations() {
            normalizedRoots.forEach { normalizedRoot ->
                normalizedRoot.parent?.let(::registerDir)
                registerDir(normalizedRoot)
                if (normalizedRoot.exists() && normalizedRoot.isDirectory()) {
                    normalizedRoot.listDirectoryEntries().forEach(::registerDir)
                }
            }
        }

        suspend fun onRelevantChange() {
            delay(debounceMs)
            while (true) {
                val extraKey = watchService.poll() ?: break
                extraKey.pollEvents()
                if (!extraKey.reset()) {
                    keyToDir.remove(extraKey)?.let(watchedDirs::remove)
                }
            }
            refreshRegistrations()
            val version = loader.refresh(workspacePath)
            watcherLogger.info { "Skills change detected for $workspaceKey, version=$version" }
        }

        refreshRegistrations()
        watcherLogger.info { "Watching skills roots for $workspaceKey (${roots.size} roots)" }
        try {
            while (currentCoroutineContext().isActive) {
                val key = watchService.take()
                val watchedDir = keyToDir[key]
                var relevant = false
                if (watchedDir != null) {
                    for (event in key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            relevant = true
                            continue
                        }
                        val changed = (event.context() as? Path) ?: continue
                        val changedPath = watchedDir.resolve(changed).toAbsolutePath().normalize()
                        val changedName = changed.fileName?.toString()
                        val rootLevelChange = watchedDir in normalizedRoots
                        val rootCreatedOrDeleted = changedPath in normalizedRoots
                        if (changedName == "SKILL.md" || rootLevelChange || rootCreatedOrDeleted) {
                            relevant = true
                        }
                    }
                }
                if (!key.reset()) {
                    keyToDir.remove(key)?.let(watchedDirs::remove)
                }
                if (relevant) {
                    onRelevantChange()
                }
            }
        } catch (_: CancellationException) {
            // normal shutdown
        } finally {
            watchService.close()
            watcherLogger.info { "Stopped watching skills roots for $workspaceKey" }
        }
    }
}
