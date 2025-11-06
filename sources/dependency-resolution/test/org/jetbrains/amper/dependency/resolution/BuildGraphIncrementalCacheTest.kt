/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import kotlin.io.path.div
import kotlin.test.Test
import kotlin.test.assertTrue

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
    fun `com_jetbrains_intellij_platform jps-build-dependency-graph 253_25908_13-EAP-SNAPSHOT`(testInfo: TestInfo) = runTest {

        // Incremental cache root is calculated once and reused between all resolution runs.
        val incrementalCachePath = uniqueNestedTempDir()

        /**
         * Resolve library "com.jetbrains.intellij.platform:jps-build-dependency-graph:253.25908.13-EAP-SNAPSHOT".
         * The first run populates the incremental cache, all later runs should reuse it.
         */
        suspend fun resolveAndCheck(): DependencyNode {
            val resolver = Resolver()

            val context = Context {
                this.repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_INTELLIJ_DEPS, REDIRECTOR_INTELLIJ_SNAPSHOTS)
                this.cache = getDefaultFileCacheBuilder(Dirs.userCacheRoot)
                this.incrementalCache = IncrementalCache(
                    incrementalCachePath,
                    "1"
                )
            }

            val root = RootDependencyNodeWithContext(
                templateContext = context,
                rootCacheEntryKey = RootCacheEntryKey.FromChildren,
                children = listOf(
                    context.toMavenDependencyNode(
                        "com.jetbrains.intellij.platform:jps-build-dependency-graph:253.25908.13-EAP-SNAPSHOT".toMavenCoordinates(),
                        false
                    )
                )
            )

            val resolvedGraph = resolver.resolveDependencies(root).root
            resolvedGraph.verifyMessages()
            assertFiles(testInfo, resolvedGraph)
            return resolvedGraph
        }

        // 1. The first resolution takes place and populates the cache
        resolveAndCheck()

        // 2. Re-resolve the graph, from the incremental cache this time
        val rootReresolved = resolveAndCheck()
        assertTrue(
            rootReresolved is SerializableRootDependencyNode,
            "Unexpected type of root node, it should have been resolved from cache, but the type is" +
                    " ${rootReresolved::class.simpleName}")
    }
}
