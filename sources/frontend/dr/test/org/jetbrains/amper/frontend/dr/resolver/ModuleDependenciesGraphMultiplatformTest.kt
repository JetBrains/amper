/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
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
 * 1. Gradle includes dependency on 'org.jetbrains.compose.components:components-resources' unconditionally,
 *    while Amper adds this dependency in case module does have 'compose' resources only.
 * 2. Amper resolves runtime version of a library on IDE sync.
 *    This might cause a difference with the graph produced by Gradle.
 *    It will be fixed in the nearest future (as soon as Amper IDE plugin started calling
 *    CLI for running application instead of reusing module classpath from the Workspace model)
 */
class ModuleDependenciesGraphMultiplatformTest : BaseModuleDrTest() {
    override val testGoldenFilesRoot: Path = super.testGoldenFilesRoot / "moduleDependenciesGraphMultiplatform"

    @Test
    fun `test sync empty jvm module`() {
        val aom = getTestProjectModel("jvm-empty", testDataRoot)

        assertEquals(
            setOf("common", "commonTest", "jvm", "jvmTest"),
            aom.modules[0].fragments.map { it.name }.toSet(),
            ""
        )


        val jvmTestFragmentDeps = runBlocking {
            doTest(
                aom,
                resolutionInput = ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "jvm-empty",
                expected = """
                module:jvm-empty
                ├─── jvm-empty:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                │         ╰─── org.jetbrains:annotations:13.0
                ├─── jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                ├─── jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}
                │         ├─── org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
                │         │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                │         ╰─── org.junit.jupiter:junit-jupiter-api:5.10.1
                │              ├─── org.junit:junit-bom:5.10.1
                │              ├─── org.opentest4j:opentest4j:1.3.0
                │              ├─── org.junit.platform:junit-platform-commons:1.10.1
                │              │    ├─── org.junit:junit-bom:5.10.1
                │              │    ╰─── org.apiguardian:apiguardian-api:1.1.2
                │              ╰─── org.apiguardian:apiguardian-api:1.1.2
                ├─── jvm-empty:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                ├─── jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                ╰─── jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                     ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion} (*)
                """.trimIndent()
            )
        }

        runBlocking {
            downloadAndAssertFiles(
                listOf(
                    "annotations-13.0.jar",
                    "apiguardian-api-1.1.2.jar",
                    "junit-jupiter-api-5.10.1.jar",
                    "junit-platform-commons-1.10.1.jar",
                    "kotlin-stdlib-${UsedVersions.kotlinVersion}.jar",
                    "kotlin-test-${UsedVersions.kotlinVersion}.jar",
                    "kotlin-test-junit5-${UsedVersions.kotlinVersion}.jar",
                    "opentest4j-1.3.0.jar",
                ),
                jvmTestFragmentDeps
            )
        }
    }

    @Test
    fun `test shared@ios dependencies graph`(testInfo: TestInfo) {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedIosFragmentDeps = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
                fragment = "ios",
            )
        }
        assertFiles(testInfo, sharedIosFragmentDeps)
    }

    @Test
    fun `test shared@iosX64 dependencies graph`(testInfo: TestInfo) {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
                fragment = "iosX64",
            )
        }

        assertFiles(testInfo, iosAppIosX64FragmentDeps)
    }

    @Test
    fun `test shared@iosX64Test dependencies graph`(testInfo: TestInfo) {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
                fragment = "iosX64Test",
            )
        }

        assertFiles(testInfo, iosAppIosX64FragmentDeps)
    }

    /**
     * Since test fragment from one module can't reference test fragment of another module,
     * exported test dependency 'tinylog-api-kotlin' of shared module is not added to the fragment ios-app@iosX64Test.
     */
    @Test
    fun `test ios-app@iosX64Test dependencies graph`(testInfo: TestInfo) {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "ios-app",
                fragment = "iosX64Test",
            )
        }

        assertFiles(testInfo, iosAppIosX64FragmentDeps)
    }

    @Test
    fun `test ios-app@ios dependencies graph`(testInfo: TestInfo) {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val iosAppIosFragmentDeps = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "ios-app",
                fragment = "ios",
            )
        }
        assertFiles(testInfo, iosAppIosFragmentDeps)
    }

    @Test
    fun `test ios-app@iosX64 dependencies graph`(testInfo: TestInfo) {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "ios-app",
                fragment = "iosX64",
            )
        }

        assertFiles(testInfo, iosAppIosX64FragmentDeps)
    }

    // todo (AB) : 'anrdoid-app.android' differs from what Gradle produce (versions).
    // todo (AB) : It seems it is caused by resolving RUNTIME version of library instead of COMPILE one being resolved by IdeSync.
    @Test
    fun `test android-app@android dependencies graph`(testInfo: TestInfo) {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val androidAppAndroidFragmentDeps = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "android-app",
                fragment = "android",
            )
        }
        // todo (AB) : Some versions are incorrect (?) - check difference with Gradle
        assertFiles(testInfo, androidAppAndroidFragmentDeps)
    }

    @Test
    fun `test shared@android dependencies graph`(testInfo: TestInfo) {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedAndroidFragmentDeps = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
                fragment = "android",
            )
        }
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
    fun `test publication of shared KMP module for single platform`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedModuleDeps = runBlocking {
            doTest(
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
                ),
                module = "shared"
            ) as ModuleDependencyNodeWithModule
        }

        sharedModuleDeps.assertMapping(
            mapOf(
                "org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}" to "org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}",
                "org.jetbrains.compose.runtime:runtime:1.7.3" to "org.jetbrains.compose.runtime:runtime-desktop:1.7.3",
                "org.jetbrains.compose.foundation:foundation:1.7.3" to "org.jetbrains.compose.foundation:foundation-desktop:1.7.3",
                "org.jetbrains.compose.material3:material3:1.7.3" to "org.jetbrains.compose.material3:material3-desktop:1.7.3",
            )
        )
    }

    private fun ModuleDependencyNodeWithModule.assertMapping(
        expectedMapping: Map<String, String>
    ) {
        val expectedCoordinatesMapping =
            expectedMapping.map { it.key.toMavenCoordinates() to it.value.toMavenCoordinates() }.toMap()
        this
            .children
            .filterIsInstance<DirectFragmentDependencyNodeHolder>()
            .filter { it.dependencyNode is MavenDependencyNode }
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

    /**
     * Version of a direct dependency is resolved from BOM if it was left unspecified.
     */
    @Test
    fun `resolving version of a direct dependency from BOM`() {
        val aom = getTestProjectModel("jvm-bom-support", testDataRoot)

        val jvmAppDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.ClassPathType(
                        ResolutionScope.COMPILE,
                        setOf(ResolutionPlatform.JVM),
                        false,
                        false)
                    ,
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                verifyMessages = false,
                module = "app",
                expected = """
                    app:COMPILE:JVM
                    ├─── app:common:com.fasterxml.jackson.core:jackson-annotations:unspecified
                    │    ╰─── com.fasterxml.jackson.core:jackson-annotations:unspecified -> 2.18.3
                    │         ╰─── com.fasterxml.jackson:jackson-bom:2.18.3
                    │              ╰─── com.fasterxml.jackson.core:jackson-annotations:2.18.3 (c)
                    ├─── app:common:com.fasterxml.jackson:jackson-bom:2.18.3
                    │    ╰─── com.fasterxml.jackson:jackson-bom:2.18.3 (*)
                    ├─── app:common:org.jetbrains.kotlin:kotlin-stdlib:2.1.20, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:2.1.20
                    │         ╰─── org.jetbrains:annotations:13.0
                    ╰─── app:jvm:org.jetbrains.kotlin:kotlin-stdlib:2.1.20, implicit
                         ╰─── org.jetbrains.kotlin:kotlin-stdlib:2.1.20 (*)
                """.trimIndent(),
            )
        }
        assertFiles(
            listOf(
                "annotations-13.0.jar",
                "jackson-annotations-2.18.3.jar",
                "kotlin-stdlib-2.1.20.jar",
            ),
            jvmAppDeps
        )
    }

    private fun String.toMavenCoordinates() =
        split(":").let { MavenCoordinates(it[0], it[1], it[2]) }
}
