/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.dependency.resolution.diagnostics.UnableToResolveDependency
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.io.TempDir
import java.net.http.HttpClient
import java.nio.file.Path
import java.util.*
import kotlin.io.path.div
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class BuildGraphIncrementalCacheTest : BaseDRTest() {
    override val testDataPath: Path = super.testDataPath / "buildGraphIncrementalCache"

    @TempDir
    lateinit var tmpDir: Path

    fun uniqueNestedTempDir(): Path = tmpDir.resolve(UUID.randomUUID().toString().substring(0, 10))

    /**
     * This test checks that quasi-SNAPSHOT dependency (published without maven-metadata.xml) could be
     * - correctly resolved;
     * - stored to the incremental cache;
     * - restored from there.
     */
    @Test
    fun `com_jetbrains_intellij_platform jps-build-dependency-graph 253_25908_13-EAP-SNAPSHOT`(testInfo: TestInfo) = runSlowDrTest {

        val coordinates = "com.jetbrains.intellij.platform:jps-build-dependency-graph:253.25908.13-EAP-SNAPSHOT".toMavenCoordinates()
        val repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_INTELLIJ_DEPS, REDIRECTOR_INTELLIJ_SNAPSHOTS)

        // Incremental cache root is calculated once and reused between all resolution runs.
        val incrementalCachePath = uniqueNestedTempDir()

        // 1. The first resolution takes place and populates the cache
        resolveAndCheck(coordinates, testInfo, incrementalCachePath,
            repositories = repositories,
            expectedRootNodeType = RootDependencyNodeWithContext::class
        )

        // 2. Re-resolve the graph, from the incremental cache this time
        resolveAndCheck(coordinates, testInfo, incrementalCachePath,
            repositories = repositories,
            expectedRootNodeType = SerializableRootDependencyNode::class,
        )
    }

    /**
     * This test checks that if a recoverable network error occurred while resolving the graph,
     * next time that graph won't be reused (won't be taken from cache),
     * instead it will be recalculated.
     */
    @Test
    fun `check that recoverable network error prevents reusing of resolved graph`(testInfo: TestInfo) = runSlowDrTest(timeout = 10.minutes) {

        val coordinates = "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2".toMavenCoordinates()
        val repository = MavenRepository("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/")

        // Incremental cache root is calculated once and reused between all resolution runs.
        val tmpDir = uniqueNestedTempDir()
        val incrementalCachePath = tmpDir.resolve(".inc")
        val localStorage = tmpDir.resolve(".amper")

        // 1. The first resolution takes place. It fails on downloading artifact, producing ERROR diagnostic
        val resolvedGraph = resolveAndCheck(
            coordinates, testInfo, incrementalCachePath, localStorage = localStorage,
            httpClient = TestHttpClient.create(
                // This causes the dependency to be in a completely unresolved state after resolution finished,
                // neither own artifacts nor transitive dependencies are resolved.
                urlThatShouldNotBeDownloaded = listOf(
                    coordinates.toUrl(repository, "pom"),
                    coordinates.toUrl(repository, "module"),
                ),
                failOnErrorUrl = false
            ),
            repositories = listOf(repository),
            assertCorrectResolution = false,
            expectedRootNodeType = RootDependencyNodeWithContext::class
        )

        assertTheOnlyNonInfoMessage<UnableToResolveDependency>(resolvedGraph, Severity.ERROR)

        // 2. Re-resolve the graph. Graph is not taken from the cache, the problematic node is re-downloaded.
        resolveAndCheck(
            coordinates, testInfo, incrementalCachePath, localStorage = localStorage,
            repositories = listOf(repository),
            expectedRootNodeType = RootDependencyNodeWithContext::class
        )
    }

    /**
     * Resolve a library with the given [coordinates].
     * The first run populates the incremental cache, all later runs should reuse it
     * (but only in case if no potentially recoverable error were reported during the resolution though).
     */
    suspend fun resolveAndCheck(
        coordinates: MavenCoordinates,
        testInfo: TestInfo,
        incrementalCacheDir: Path,
        localStorage: Path = Dirs.userCacheRoot,
        expectedRootNodeType: KClass<out DependencyNode>? = null,
        assertCorrectResolution: Boolean = true,
        repositories: List<Repository> = listOf(REDIRECTOR_MAVEN_CENTRAL),
        httpClient: HttpClient? = null,
    ): DependencyNode {
        val resolver = Resolver()

        val context = Context {
            this.repositories = repositories
            this.cache = getDefaultFileCacheBuilder(localStorage).let {
                {
                    it()
                    readOnlyExternalRepositories = emptyList()
                }
            }
            this.incrementalCache = IncrementalCache(
                incrementalCacheDir,
                "1"
            )
        }

        if (httpClient != null) context.resolutionCache[httpClientKey] = httpClient

        val root = RootDependencyNodeWithContext(
            templateContext = context,
            rootCacheEntryKey = RootCacheEntryKey.FromChildren,
            children = listOf(context.toMavenDependencyNode(coordinates,false))
        )

        val resolvedGraph = resolver.resolveDependencies(root).root

        expectedRootNodeType?.let {
            kotlin.test.assertEquals(
                expectedRootNodeType, resolvedGraph::class,
                "Unexpected type of root node is return by resolution"
            )
        }

        if (assertCorrectResolution)  {
            resolvedGraph.verifyMessages()
            assertFiles(testInfo, resolvedGraph)
        }
        return resolvedGraph
    }

    private fun MavenCoordinates.toUrl(repository: MavenRepository, extension: String = "jar"): String {
        return StringBuilder(repository.url.trimEnd('/'))
            .appendToUrl(groupId.replace(".", "/"))
            .appendToUrl(artifactId)
            .appendToUrl(version.orUnspecified())
            .appendToUrl("$artifactId-$version.$extension")
            .toString()
    }

    private fun StringBuilder.appendToUrl(suffix: String): StringBuilder {
        ensureEndsWith("/")
        append(suffix.trimStart('/'))
        return this
    }

    private fun StringBuilder.ensureEndsWith(suffix: String = "/"): StringBuilder {
        if (!endsWith(suffix)) append(suffix)
        return this
    }
}
