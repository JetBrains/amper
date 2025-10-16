/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.dependency.resolution.diagnostics.detailedMessage
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolverTest: BaseDRTest() {

    @Test
    fun `junit-jupiter-params resolved in two contexts (COMPILE, RUNTIME)`() = runTest {
        val jupiterParamsCoordinates = "org.junit.jupiter:junit-jupiter-params:5.7.2"

        val nodeInCompileContext = jupiterParamsCoordinates.toMavenNode(context(ResolutionScope.COMPILE))
        val nodeInRuntimeContext = jupiterParamsCoordinates.toMavenNode(context(ResolutionScope.RUNTIME))

        val root = RootDependencyNodeInput(
            children = listOf(nodeInCompileContext, nodeInRuntimeContext),
//            context()
        )

        doTest(
            root,
            expected = """
            root
            ├─── org.junit.jupiter:junit-jupiter-params:5.7.2
            │    ├─── org.junit:junit-bom:5.7.2
            │    ├─── org.apiguardian:apiguardian-api:1.1.0
            │    ╰─── org.junit.jupiter:junit-jupiter-api:5.7.2
            │         ├─── org.junit:junit-bom:5.7.2
            │         ├─── org.apiguardian:apiguardian-api:1.1.0
            │         ├─── org.opentest4j:opentest4j:1.2.0
            │         ╰─── org.junit.platform:junit-platform-commons:1.7.2
            │              ├─── org.junit:junit-bom:5.7.2
            │              ╰─── org.apiguardian:apiguardian-api:1.1.0
            ╰─── org.junit.jupiter:junit-jupiter-params:5.7.2
                 ├─── org.junit:junit-bom:5.7.2
                 ├─── org.apiguardian:apiguardian-api:1.1.0
                 ╰─── org.junit.jupiter:junit-jupiter-api:5.7.2
                      ├─── org.junit:junit-bom:5.7.2
                      ├─── org.apiguardian:apiguardian-api:1.1.0
                      ├─── org.opentest4j:opentest4j:1.2.0
                      ╰─── org.junit.platform:junit-platform-commons:1.7.2
                           ├─── org.junit:junit-bom:5.7.2
                           ╰─── org.apiguardian:apiguardian-api:1.1.0
            """.trimIndent(),
            verifyMessages = false
        )

        downloadAndAssertFiles(
            listOf(
                "apiguardian-api-1.1.0.jar",
                "junit-jupiter-api-5.7.2.jar",
                "junit-jupiter-params-5.7.2-all.jar",
                "junit-jupiter-params-5.7.2.jar",
                "junit-platform-commons-1.7.2.jar",
                "opentest4j-1.2.0.jar",
            ),
            root
        )
    }

    @Test
    fun `kmp library sources downloaded`(testInfo: TestInfo) = runTest {
        val kmpLibrary = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0"

        val root = doTest(
            testInfo,
            dependency = listOf(kmpLibrary),
            platform = setOf(ResolutionPlatform.IOS_ARM64, ResolutionPlatform.IOS_SIMULATOR_ARM64),
            expected = """
                root
                ╰─── org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
                     ├─── org.jetbrains.kotlinx:atomicfu:0.23.1
                     │    ├─── org.jetbrains.kotlin:kotlin-stdlib:1.9.21
                     │    ╰─── org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21
                     │         ╰─── org.jetbrains.kotlin:kotlin-stdlib:1.9.21
                     ╰─── org.jetbrains.kotlin:kotlin-stdlib:1.9.21
            """.trimIndent(),
            verifyMessages = false,
        )

        downloadAndAssertFiles(
            listOf(
                "atomicfu-commonMain-0.23.1-sources.jar",
                "atomicfu-commonMain-0.23.1.klib",
                "atomicfu-nativeMain-0.23.1-sources.jar",
                "atomicfu-nativeMain-0.23.1.klib",
                "atomicfu-nativeMain-cinterop-0.23.1.klib",
                "kotlin-stdlib-commonMain-1.9.21-sources.jar",
                "kotlin-stdlib-commonMain-1.9.21.klib",
                "kotlinx-coroutines-core-commonMain-1.8.0-sources.jar",
                "kotlinx-coroutines-core-commonMain-1.8.0.klib",
                "kotlinx-coroutines-core-concurrentMain-1.8.0-sources.jar",
                "kotlinx-coroutines-core-concurrentMain-1.8.0.klib",
                "kotlinx-coroutines-core-nativeDarwinMain-1.8.0-sources.jar",
                "kotlinx-coroutines-core-nativeDarwinMain-1.8.0.klib",
                "kotlinx-coroutines-core-nativeMain-1.8.0-sources.jar",
                "kotlinx-coroutines-core-nativeMain-1.8.0.klib",
            ),
            withSources = true,
            root = root
        )
    }

    @Test
    fun `sources downloaded even if variant is not defined in Gradle metadata`(testInfo: TestInfo) = runTest {
        val library = "com.fasterxml.jackson.core:jackson-core:2.17.2"

        val root = doTest(
            testInfo,
            dependency = listOf(library),
            platform = setOf(ResolutionPlatform.JVM),
            expected = """
                root
                ╰─── com.fasterxml.jackson.core:jackson-core:2.17.2
                     ╰─── com.fasterxml.jackson:jackson-bom:2.17.2
            """.trimIndent(),
            verifyMessages = false,
        )

        downloadAndAssertFiles(
            listOf(
                "jackson-core-2.17.2-sources.jar",
                "jackson-core-2.17.2.jar",
            ),
            withSources = true,
            root = root
        )
    }

    @Test
    fun `invalid version is correctly reported`(testInfo: TestInfo) = runTest {
        val library = "com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared"

        val root = doTest(
            testInfo,
            dependency = listOf(library),
            platform = setOf(ResolutionPlatform.JVM),
            expected = """
                root
                ╰─── com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared
            """.trimIndent(),
            verifyMessages = false,
        )

        val node = root.distinctBfsSequence().filterIsInstance<MavenDependencyNode>().single()
        assertEquals(
            1, node.messages.size,
            "Expected exactly one error message instead of ${node.messages.size}: ${node.messages}"
        )
        
        val expectedErrorPath = Dirs.userCacheRoot.resolve(".m2.cache/com/fasterxml/jackson/core/jackson-core/2.17.2 - ../shared")

        assertEquals(
            node.messages.single().message,
            "Unable to resolve dependency com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared",
            "Unexpected error message"
        )

        assertTrue(
            // Windows
            node.messages.single().detailedMessage.contains("NoSuchFileException: $expectedErrorPath")
                    || node.messages.single().detailedMessage.contains("AccessDeniedException:")
                    // Linux, MacOS
                    || node.messages.single().detailedMessage.contains("java.lang.IllegalArgumentException: Illegal character in path at index"),
            "Unexpected detailed error message: \n ${node.messages.single().detailedMessage}"
        )
    }
}
