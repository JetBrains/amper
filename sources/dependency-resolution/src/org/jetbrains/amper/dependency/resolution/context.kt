/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
class Context(
    val settings: Settings,
    val resolutionCache: Cache = Cache(),
    /**
     * Contains a map of all already created [MavenDependencyNode]s by their original maven dependency.
     *
     * For keys, we are only really interested in the original dependency coordinates, but we can rely on referentia
     * equality here because `MavenDependency` instances are reused just like nodes.
     *
     * Note: if conflict resolution occurs, the key is untouched, and we still consider nodes based on their "desired"
     * version. This way, whenever another node is created for the exact same version, it will also benefit from
     * the conflict resolution.
     */
    private val nodesByMavenDependency: MutableMap<MavenDependency, MavenDependencyNode> = ConcurrentHashMap(),
) : Closeable {

    constructor(block: SettingsBuilder.() -> Unit = {}) : this(SettingsBuilder(block).settings)

    val nodeCache: Cache = Cache()

    fun copyWithNewNodeCache(parentNodes: List<DependencyNode>, repositories: List<Repository>? = null): Context = Context(settings.withRepositories(repositories), resolutionCache, nodesByMavenDependency).apply {
        nodeParents.addAll(parentNodes)
    }

    private fun Settings.withRepositories(repositories: List<Repository>?): Settings =
        if (repositories != null && repositories.toSet() != this@withRepositories.repositories.toSet()) {
            this@withRepositories.copy(repositories = repositories)
        } else this@withRepositories

    /**
     * Creates a new [MavenDependencyNode] corresponding to the given Maven [dependency] (in its desired version), or
     * reuses an existing node that was requesting the exact same dependency.
     *
     * In case of reuse, the returned node is guaranteed to have the same requested version as [dependency]. This means
     * that, if conflict resolution has already occurred for the returned node, its internal [MavenDependency.version]
     * might be different from that of the given [dependency], but its "desired" [MavenDependencyNode.version] is
     * guaranteed to match.
     */
    fun getOrCreateNode(dependency: MavenDependency, parentNode: DependencyNode?): MavenDependencyNode =
        nodesByMavenDependency
            .computeIfAbsent(dependency) { MavenDependencyNode(templateContext = this, dependency) }
            .apply { parentNode?.let { context.nodeParents.add(parentNode) } }

    override fun close() {
        resolutionCache.close()
    }
}

/**
 * Helps to build [Settings].
 *
 * todo (AB) : Design choice for DR: https://jetbrains.team/p/amper/repositories/amper-design/files/06-Dependencies.md#design-choice-4
 * todo (AB) : Algorithm below follows option B, while document suggests to choose option A
 * todo (AB) : Common fragments could be compiled against different library version for different leaf fragments =>
 * todo (AB) : There is no API surface in common fragment.
 *
 * @see [FileCacheBuilder]
 * @see [HighestVersionStrategy]
 */
class SettingsBuilder(init: SettingsBuilder.() -> Unit = {}) {

    var progress: Progress = Progress()
    var scope: ResolutionScope = ResolutionScope.COMPILE
    var platforms: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM)
    var repositories: List<Repository> = listOf(Repository("https://repo1.maven.org/maven2"))
    var cache: FileCacheBuilder.() -> Unit = {}
    var conflictResolutionStrategies: List<HighestVersionStrategy> = listOf(HighestVersionStrategy())

    init {
        apply(init)
    }

    val settings: Settings
        get() = Settings(
            progress,
            scope,
            platforms,
            repositories,
            FileCacheBuilder(cache).build(),
            conflictResolutionStrategies
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
    val platforms: Set<ResolutionPlatform>,
    val repositories: List<Repository>,
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
    val exception: Throwable? = null,
)

enum class Severity {
    INFO, WARNING, ERROR
}

/**
 * The parents of the node holding this context.
 */
// TODO this should probably be an internal property of the dependency node instead of stored in the nodeCache
val Context.nodeParents: MutableList<DependencyNode>
    get() = nodeCache.computeIfAbsent(Key<MutableList<DependencyNode>>("parentNodes")) {
        CopyOnWriteArrayList()
    }


data class Repository(
    val url: String,
    val userName: String? = null,
    val password: String? = null,
) {
    override fun toString(): String {
        return url
    }
}

fun List<String>.toRepositories() = map { Repository(it) }