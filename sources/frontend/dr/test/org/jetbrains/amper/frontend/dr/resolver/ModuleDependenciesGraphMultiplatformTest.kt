/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.IncrementalCacheUsage
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Resolved module dependencies graph for the test project 'compose-multiplatform' is almost identical to what Gradle resolves*.
 * Be careful: changing of the expected result might rather highlight
 * the error introduced to resolution logic than its improvement while DR evolving.
 *
 * Known sources of differences between Amper and Gradle resolution logic:
 *
 * 1. Gradle includes a dependency on 'org.jetbrains.compose.components:components-resources' unconditionally,
 *    while Amper adds this dependency in case the module does have 'compose' resources only.
 * 2. Amper resolves a runtime version of a library on IDE sync.
 *    This might cause a difference with the graph produced by Gradle.
 *    It will be fixed in the nearest future (as soon as Amper IDE plugin started calling
 *    CLI for running application instead of reusing module classpath from the Workspace model)
 */
class ModuleDependenciesGraphMultiplatformTest : BaseModuleDrTest() {
    override val testGoldenFilesRoot: Path = super.testGoldenFilesRoot / "moduleDependenciesGraphMultiplatform"

    @Test
    fun `test sync empty jvm module`() = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("jvm-empty", testDataRoot)

        assertEquals(
            setOf("main", "test"),
            aom.modules[0].fragments.map { it.name }.toSet(),
            ""
        )


        val testFragmentDeps = doTest(
            aom,
            resolutionInput = ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "jvm-empty",
            expected = """
                module:jvm-empty
                ├─── jvm-empty:main:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}
                │         ╰─── org.jetbrains:annotations:13.0
                ├─── jvm-empty:test:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion} (*)
                ╰─── jvm-empty:test:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.defaultKotlinVersion}, implicit (because the test engine is junit-5)
                     ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.defaultKotlinVersion}
                          ├─── org.jetbrains.kotlin:kotlin-test:${UsedVersions.defaultKotlinVersion}
                          │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion} (*)
                          ╰─── org.junit.jupiter:junit-jupiter-api:5.10.1
                               ├─── org.junit:junit-bom:5.10.1
                               ├─── org.opentest4j:opentest4j:1.3.0
                               ├─── org.junit.platform:junit-platform-commons:1.10.1
                               │    ├─── org.junit:junit-bom:5.10.1
                               │    ╰─── org.apiguardian:apiguardian-api:1.1.2
                               ╰─── org.apiguardian:apiguardian-api:1.1.2
                """.trimIndent()
        )

        assertFiles(
            listOf(
                "annotations-13.0.jar",
                "apiguardian-api-1.1.2.jar",
                "junit-jupiter-api-5.10.1.jar",
                "junit-platform-commons-1.10.1.jar",
                "kotlin-stdlib-${UsedVersions.defaultKotlinVersion}.jar",
                "kotlin-test-${UsedVersions.defaultKotlinVersion}.jar",
                "kotlin-test-junit5-${UsedVersions.defaultKotlinVersion}.jar",
                "opentest4j-1.3.0.jar",
            ),
            testFragmentDeps
        )
    }

    @Test
    fun `test shared@ios dependencies graph`(testInfo: TestInfo) = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedIosFragmentDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "shared",
            fragment = "ios",
        )
        assertFiles(testInfo, sharedIosFragmentDeps)
    }

    @Test
    fun `test shared@iosX64 dependencies graph`(testInfo: TestInfo) = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "shared",
            fragment = "iosX64",
        )

        assertFiles(testInfo, iosAppIosX64FragmentDeps)
    }

    @Test
    fun `test shared@iosX64Test dependencies graph`(testInfo: TestInfo) = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "shared",
            fragment = "iosX64Test",
        )

        assertFiles(testInfo, iosAppIosX64FragmentDeps)
    }

    /**
     * Since test fragment from one module can't reference the test fragment of another module,
     * exported test dependency 'tinylog-api-kotlin' of the shared module is not added to the fragment ios-app@iosX64Test.
     */
    @Test
    fun `test ios-app@iosX64Test dependencies graph`(testInfo: TestInfo) = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "ios-app",
            fragment = "iosX64Test",
        )

        assertFiles(testInfo, iosAppIosX64FragmentDeps)
    }

    @Test
    fun `test ios-app@ios dependencies graph`(testInfo: TestInfo) = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val iosAppIosFragmentDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "ios-app",
            fragment = "ios",
        )
        assertFiles(testInfo, iosAppIosFragmentDeps)
    }

    @Test
    fun `test ios-app@iosX64 dependencies graph`(testInfo: TestInfo) = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "ios-app",
            fragment = "iosX64",
        )
        assertFiles(testInfo, iosAppIosX64FragmentDeps)
    }

    // todo (AB) : 'android-app.android' differs from what Gradle produce (versions).
    // todo (AB) : It seems it is caused by resolving RUNTIME version of library instead of COMPILE one being resolved by IdeSync.
    @Test
    fun `test android-app@android dependencies graph`(testInfo: TestInfo) = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val androidAppAndroidFragmentDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom),
                ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "android-app",
            fragment = "main",
        )
        // todo (AB) : Some versions are incorrect (?) - check difference with Gradle
        assertFiles(testInfo, androidAppAndroidFragmentDeps)
    }

    @Test
    fun `test shared@android dependencies graph`(testInfo: TestInfo) = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedAndroidFragmentDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "shared",
            fragment = "android",
        )
        // todo (AB) : Some versions are incorrect (?) - check difference with Gradle
        assertFiles(testInfo, sharedAndroidFragmentDeps)
    }

    /**
     * Publishing of a KMP library involves publishing various variants of this library for different platforms.
     * DR provides API [MavenDependencyNode.getMavenCoordinatesForPublishing]
     * for getting coordinates of the platform-specific variants of KMP libraries.
     * Those coordinates are used for the module publishing instead of references on KMP libraries.
     *
     * This test verifies that DR API provides correct coordinates of the platform-specific
     * variants for KMP libraries.
     */
    @Test
    fun `test publication of shared KMP module for single platform`() = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedModuleDeps = doTest(
            aom,
            ResolutionInput(
                DependenciesFlowType.ClassPathType(
                    ResolutionScope.COMPILE,
                    setOf(ResolutionPlatform.JVM),
                    isTest = false,
                    includeNonExportedNative = false
                ),
                ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                incrementalCacheUsage = IncrementalCacheUsage.SKIP
            ),
            module = "shared"
        ) as ModuleDependencyNode

        sharedModuleDeps.assertMapping(
            mapOf(
                "org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}" to "org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}",
                "org.jetbrains.compose.runtime:runtime:${UsedVersions.composeVersion}" to "org.jetbrains.compose.runtime:runtime-desktop:${UsedVersions.composeVersion}",
                "org.jetbrains.compose.foundation:foundation:${UsedVersions.composeVersion}" to "org.jetbrains.compose.foundation:foundation-desktop:${UsedVersions.composeVersion}",
                "org.jetbrains.compose.material3:material3:${UsedVersions.composeVersion}" to "org.jetbrains.compose.material3:material3-desktop:${UsedVersions.composeVersion}",
            )
        )
    }

    /**
     * For platform-specific artifacts that were introduced into the resolution by KMP libraries
     * (as one of their `available-at`), DR provides API [MavenDependencyNode.getParentKmpLibraryCoordinates]
     * for getting coordinates of the original KMP libraries.
     * These coordinates are used in the IDE to deduplicate dependencies when searching for symbols that appear
     * both in the KMP library and in the platform-specific artifact.
     *
     * This test verifies that DR API provides correct coordinates of the KMP libraries
     * for platform-specific variants.
     */
    @Test
    fun `test finding KMP library for platform-specific variant`() = runSlowModuleDependenciesTest {
        val aom = getTestProjectModel("kmp-library", testDataRoot)

        val moduleDeps = doTest(
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom),
                ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "kmp-library",
        ) as ModuleDependencyNode

        moduleDeps.assertParentKmpLibraries(
            mapOf(
                "com.squareup.okio:okio-jvm:3.9.1" to "com.squareup.okio:okio:3.9.1",
                "com.squareup.okio:okio-iosarm64:3.9.1" to "com.squareup.okio:okio:3.9.1",
            ),
        )
    }

    private fun ModuleDependencyNode.assertMapping(
        expectedMapping: Map<String, String>
    ) {
        val expectedCoordinatesMapping =
            expectedMapping.map { it.key.toMavenCoordinates() to it.value.toMavenCoordinates() }.toMap()
        this
            .children
            .filterIsInstance<DirectFragmentDependencyNodeHolderWithContext>()
            .forEach { directMavenDependency ->
                val node = directMavenDependency.dependencyNode as MavenDependencyNode

                val originalCoordinates = node.getOriginalMavenCoordinates()
                val expectedCoordinatesForPublishing = expectedCoordinatesMapping[originalCoordinates]
                val actualCoordinatesForPublishing = node.getMavenCoordinatesForPublishing()

                assertNotNull(
                    expectedCoordinatesForPublishing,
                    "Library with coordinates [$originalCoordinates] is absent among direct module dependencies."
                ) {}
                assertEquals(
                    expectedCoordinatesForPublishing, actualCoordinatesForPublishing,
                    "Unexpected coordinates for publishing were resolved for the library [$originalCoordinates]"
                )
            }
    }

    private fun ModuleDependencyNode.assertParentKmpLibraries(
        expectedMapping: Map<String, String>
    ) {
        val expectedCoordinatesMapping =
            expectedMapping.map { it.key.toMavenCoordinates() to it.value.toMavenCoordinates() }.toMap()
        // Find all MavenDependencyNode instances in the dependency graph
        val allMavenNodes = mutableListOf<MavenDependencyNode>()

        fun collectMavenNodes(node: DependencyNode) {
            if (node is MavenDependencyNode) {
                allMavenNodes.add(node)
            }
            node.children.forEach { collectMavenNodes(it) }
        }

        collectMavenNodes(this)

        val nodeParents = allMavenNodes.mapNotNull { dep ->
            val parentCoordinates = dep.getParentKmpLibraryCoordinates() ?: return@mapNotNull null
            dep.getOriginalMavenCoordinates() to parentCoordinates
        }.toMap()
        assertEquals(expectedCoordinatesMapping, nodeParents, "Incorrect parent KMP libraries were resolved")
    }
}