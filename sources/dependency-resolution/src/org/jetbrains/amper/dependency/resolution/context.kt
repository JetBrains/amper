/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.internal.ApiUsageLogger
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

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
class Context internal constructor(
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
    private val constraintsByMavenDependency: MutableMap<MavenDependencyConstraint, MavenDependencyConstraintNode> = ConcurrentHashMap(),
) : Closeable {

    constructor(block: SettingsBuilder.() -> Unit = {}) : this(SettingsBuilder(block).settings)

    val nodeCache: Cache = Cache()

    fun copyWithNewNodeCache(parentNodes: List<DependencyNode>, repositories: List<Repository>? = null): Context =
        Context(settings.withRepositories(repositories), resolutionCache, nodesByMavenDependency, constraintsByMavenDependency)
            .apply {
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

    fun getOrCreateConstraintNode(dependencyConstraint: MavenDependencyConstraint, parentNode: DependencyNode?): MavenDependencyConstraintNode =
        constraintsByMavenDependency
            .computeIfAbsent(dependencyConstraint) { MavenDependencyConstraintNode(templateContext = this, dependencyConstraint) }
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
    var repositories: List<Repository> = listOf("https://repo1.maven.org/maven2").toRepositories()
    var cache: FileCacheBuilder.() -> Unit = {}
    var spanBuilder: SpanBuilderSource = { NoopSpanBuilder.create() }
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
            spanBuilder,
            conflictResolutionStrategies
        )
}

/**
 * Helps to build [FileCache].
 *
 * @see [SettingsBuilder]
 * @see [GradleLocalRepository]
 * @see [MavenLocalRepository]
 */
class FileCacheBuilder(init: FileCacheBuilder.() -> Unit = {}) {
    var amperCache: Path = Path(System.getProperty("user.home"), ".amper")
        internal set
    var readOnlyExternalRepositories: List<LocalRepository> = defaultReadOnlyExternalRepositories()
    var localRepository: LocalRepository? = null

    init {
        apply(init)
    }

    internal fun build(): FileCache {
        return FileCache(
            amperCache,
            readOnlyExternalRepositories,
            localRepository ?: defaultLocalRepository(amperCache)
        )
    }
}

fun getDefaultFileCacheBuilder(cacheRoot: Path): FileCacheBuilder.() -> Unit = {
    amperCache = cacheRoot
    localRepository = defaultLocalRepository(cacheRoot)
    readOnlyExternalRepositories = defaultReadOnlyExternalRepositories()
}

// todo (AB) : Should it be emptyList by default?
private fun FileCacheBuilder.defaultReadOnlyExternalRepositories() = listOf(GradleLocalRepository(), MavenLocalRepository())
private fun FileCacheBuilder.defaultLocalRepository(cacheRoot: Path) = MavenLocalRepository(cacheRoot.resolve(".m2.cache"))

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
    var spanBuilder: SpanBuilderSource,
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
    val readOnlyExternalRepositories: List<LocalRepository>,
    val localRepository: LocalRepository,
)

/**
 * The parents of the node holding this context.
 */
// TODO this should probably be an internal property of the dependency node instead of stored in the nodeCache
val Context.nodeParents: MutableList<DependencyNode>
    get() = nodeCache.computeIfAbsent(Key<MutableList<DependencyNode>>("parentNodes")) {
        CopyOnWriteArrayList()
    }

sealed interface Repository

data class MavenRepository(
    val url: String,
    val userName: String? = null,
    val password: String? = null,
) : Repository {
    init {
        assert(url != MavenLocal.URL) { "Use dedicated object ${MavenLocal::class.simpleName} for ${MavenLocal.URL} instead" }
    }
    override fun toString()= url
}

object MavenLocal : Repository{
    internal const val URL = "mavenLocal"

    override fun toString() = URL
}

fun List<String>.toRepositories() = map {
    when  {
        it == MavenLocal.URL -> MavenLocal
        else -> MavenRepository(it)
    }
}

typealias SpanBuilderSource = (String) -> SpanBuilder

fun Context.spanBuilder(scope: String) = settings.spanBuilder(scope)

class NoopSpanBuilder : SpanBuilder {
    private constructor()

    private var spanContext: SpanContext? = null

    override fun startSpan(): Span? {
        if (this.spanContext == null) {
            this.spanContext = Span.current().getSpanContext()
        }
        return Span.wrap(this.spanContext!!)
    }

    override fun setParent(context: io.opentelemetry.context.Context): NoopSpanBuilder {
        if (context == null) {
            ApiUsageLogger.log("context is null")
            return this
        } else {
            this.spanContext = Span.fromContext(context).getSpanContext()
            return this
        }
    }

    override fun setNoParent(): NoopSpanBuilder {
        this.spanContext = SpanContext.getInvalid()
        return this
    }

    override fun addLink(spanContext: SpanContext): NoopSpanBuilder = this
    override fun addLink(spanContext: SpanContext, attributes: Attributes): NoopSpanBuilder = this
    override fun setAttribute(key: String, value: String): NoopSpanBuilder = this
    override fun setAttribute(key: String, value: Long): NoopSpanBuilder = this
    override fun setAttribute(key: String, value: Double): NoopSpanBuilder  = this
    override fun setAttribute(key: String, value: Boolean): NoopSpanBuilder = this
    override fun <T> setAttribute(key: AttributeKey<T?>, value: T?): NoopSpanBuilder = this
    override fun setAllAttributes(attributes: Attributes): NoopSpanBuilder  = this
    override fun setSpanKind(spanKind: SpanKind): NoopSpanBuilder  = this
    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): NoopSpanBuilder = this

    companion object {
        fun create(): NoopSpanBuilder {
            return NoopSpanBuilder()
        }
    }
}