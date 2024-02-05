/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import java.nio.file.Path

class Context(val settings: Settings, val resolutionCache: Cache = Cache()) {

    constructor(block: SettingsBuilder.() -> Unit = {}) : this(SettingsBuilder(block).settings)

    val nodeCache: Cache = Cache()

    fun copyWithNewNodeCache(parentNode: DependencyNode?): Context = Context(settings, resolutionCache).apply {
        parentNode?.let { nodeCache[parentNodeKey] = it }
    }
}

class SettingsBuilder(init: SettingsBuilder.() -> Unit = {}) {

    var progress: Progress = Progress()
    var scope: ResolutionScope = ResolutionScope.COMPILE
    var platform: String = "jvm"
    var repositories: List<String> = listOf("https://repo1.maven.org/maven2")
    var cache: FileCacheBuilder.() -> Unit = {}
    var conflictResolutionStrategies: List<HighestVersionStrategy> = listOf(HighestVersionStrategy())

    init {
        apply(init)
    }

    val settings: Settings
        get() = Settings(
            progress,
            scope,
            platform,
            repositories,
            FileCacheBuilder(cache).build(),
            conflictResolutionStrategies,
        )
}

class FileCacheBuilder(init: FileCacheBuilder.() -> Unit = {}) {

    var amperCache: Path = Path.of(System.getProperty("user.home"), ".amper")
    var localRepositories: List<LocalRepository> = listOf(GradleLocalRepository(), MavenLocalRepository())
    var fallbackLocalRepository: LocalRepository? = null

    init {
        apply(init)
    }

    fun build(): FileCache = FileCache(
        amperCache, localRepositories, fallbackLocalRepository ?: localRepositories.first()
    )
}

data class Settings(
    val progress: Progress,
    val scope: ResolutionScope,
    val platform: String,
    val repositories: List<String>,
    val fileCache: FileCache,
    val conflictResolutionStrategies: List<ConflictResolutionStrategy>,
)

data class FileCache(
    val amperCache: Path,
    val localRepositories: List<LocalRepository>,
    val fallbackLocalRepository: LocalRepository,
)

data class Message(
    val text: String,
    val extra: String = "",
    val severity: Severity = Severity.INFO,
)

enum class Severity {
    INFO, WARNING, ERROR
}

val parentNodeKey: Key<DependencyNode> = Key<DependencyNode>("parentNode")
