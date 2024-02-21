/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import java.io.Closeable
import java.nio.file.Path

/**
 * A context holder that's passed across the nodes.
 * It's supposed to be unique for each node as it holds its [nodeCache].
 * As the same time, [settings] and [resolutionCache] are expected to be the same withing the resolution session.
 * Thus, they can be copied via [copyWithNewNodeCache].
 *
 * The context has to be closed at the end of the resolution session to free caches resources, e.g., a Ktor client.
 *
 * The suggested way to populate the context with settings is using [SettingsBuilder].
 *
 * ```kotlin
 * Context {
 *     scope = ResolutionScope.COMPILE
 *     platform = "jvm"
 * }
 * ```
 *
 * @see [SettingsBuilder]
 * @see [Cache]
 */
class Context(val settings: Settings, val resolutionCache: Cache = Cache()) : Closeable {

    constructor(block: SettingsBuilder.() -> Unit = {}) : this(SettingsBuilder(block).settings)

    val nodeCache: Cache = Cache()

    fun copyWithNewNodeCache(parentNode: DependencyNode?): Context = Context(settings, resolutionCache).apply {
        parentNode?.let { nodeCache[parentNodeKey] = it }
    }

    override fun close() {
        resolutionCache.close()
    }
}

/**
 * Helps to build [Settings].
 *
 * @see [FileCacheBuilder]
 * @see [HighestVersionStrategy]
 */
class SettingsBuilder(init: SettingsBuilder.() -> Unit = {}) {

    var progress: Progress = Progress()
    var scope: ResolutionScope = ResolutionScope.COMPILE
    var platform: String = "jvm"
    var nativeTarget: String? = null
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
            nativeTarget,
            repositories,
            FileCacheBuilder(cache).build(),
            conflictResolutionStrategies,
        )
}

/**
 * Helps to build [FileCache].
 * When [fallbackLocalRepository] is not specified, the first location from [localRepositories] is used as a fallback.
 *
 * @see [SettingsBuilder]
 * @see [GradleLocalRepository]
 * @see [MavenLocalRepository]
 */
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

/**
 * Intended to define a resolution session.
 *
 * Expected to be created using [SettingsBuilder] that provides defaults.
 *
 * @see [SettingsBuilder]
 */
data class Settings(
    val progress: Progress,
    val scope: ResolutionScope,
    val platform: String,
    val nativeTarget: String?,
    val repositories: List<String>,
    val fileCache: FileCache,
    val conflictResolutionStrategies: List<ConflictResolutionStrategy>,
)

/**
 * Defines locations within the resolution session.
 *
 * Expected to be created using [FileCacheBuilder] that provides defaults.
 *
 * @see [FileCacheBuilder]
 * @see [LocalRepository]
 */
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
