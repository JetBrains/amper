/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class ResolverTest: BaseDRTest() {

    @Test
    fun `junit-jupiter-params resolved in two contexts (COMPILE, RUNTIME`() {
        val jupiterParamsCoordinates = "org.junit.jupiter:junit-jupiter-params:5.7.2"

        val nodeInCompileContext = jupiterParamsCoordinates.toMavenNode(context(ResolutionScope.COMPILE))
        val nodeInRuntimeContext = jupiterParamsCoordinates.toMavenNode(context(ResolutionScope.RUNTIME))

        val root = DependencyNodeHolder("root", listOf(nodeInCompileContext, nodeInRuntimeContext))

        doTest(
            root,
            expected = """root
                |+--- org.junit.jupiter:junit-jupiter-params:5.7.2
                ||    +--- org.junit:junit-bom:5.7.2
                ||    +--- org.apiguardian:apiguardian-api:1.1.0
                ||    \--- org.junit.jupiter:junit-jupiter-api:5.7.2
                ||         +--- org.junit:junit-bom:5.7.2
                ||         +--- org.apiguardian:apiguardian-api:1.1.0
                ||         +--- org.opentest4j:opentest4j:1.2.0
                ||         \--- org.junit.platform:junit-platform-commons:1.7.2
                ||              +--- org.junit:junit-bom:5.7.2
                ||              \--- org.apiguardian:apiguardian-api:1.1.0
                |\--- org.junit.jupiter:junit-jupiter-params:5.7.2
                |     +--- org.junit:junit-bom:5.7.2
                |     +--- org.apiguardian:apiguardian-api:1.1.0
                |     \--- org.junit.jupiter:junit-jupiter-api:5.7.2
                |          +--- org.junit:junit-bom:5.7.2
                |          +--- org.apiguardian:apiguardian-api:1.1.0
                |          +--- org.opentest4j:opentest4j:1.2.0
                |          \--- org.junit.platform:junit-platform-commons:1.7.2
                |               +--- org.junit:junit-bom:5.7.2
                |               \--- org.apiguardian:apiguardian-api:1.1.0
            """.trimMargin(),
            verifyMessages = false
        )

       runBlocking {
            downloadAndAssertFiles(
                """apiguardian-api-1.1.0.jar
                |junit-jupiter-api-5.7.2.jar
                |junit-jupiter-params-5.7.2-all.jar
                |junit-jupiter-params-5.7.2.jar
                |junit-platform-commons-1.7.2.jar
                |opentest4j-1.2.0.jar""".trimMargin(),
                root
            )
        }
    }

    @Test
    fun `kmp library sources downloaded`(testInfo: TestInfo) {
        val kmpLibrary = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0"

        val root = doTest(
            testInfo,
            dependency = listOf(kmpLibrary),
            platform = setOf(ResolutionPlatform.IOS_ARM64, ResolutionPlatform.IOS_SIMULATOR_ARM64),
            expected = """root
                |\--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
                |     +--- org.jetbrains.kotlinx:atomicfu:0.23.1
                |     |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21
                |     |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21
                |     |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21
                |     \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21
            """.trimMargin(),
            verifyMessages = false,
        )

        runBlocking {
            downloadAndAssertFiles(
                withSources = true,
                files = """
                    |atomicfu-commonMain-0.23.1-sources.jar
                    |atomicfu-commonMain-0.23.1.klib
                    |atomicfu-nativeMain-0.23.1-sources.jar
                    |atomicfu-nativeMain-0.23.1.klib
                    |kotlin-stdlib-commonMain-1.9.21-sources.jar
                    |kotlin-stdlib-commonMain-1.9.21.klib
                    |kotlinx-coroutines-core-commonMain-1.8.0-sources.jar
                    |kotlinx-coroutines-core-commonMain-1.8.0.klib
                    |kotlinx-coroutines-core-concurrentMain-1.8.0-sources.jar
                    |kotlinx-coroutines-core-concurrentMain-1.8.0.klib
                    |kotlinx-coroutines-core-nativeDarwinMain-1.8.0-sources.jar
                    |kotlinx-coroutines-core-nativeDarwinMain-1.8.0.klib
                    |kotlinx-coroutines-core-nativeMain-1.8.0-sources.jar
                    |kotlinx-coroutines-core-nativeMain-1.8.0.klib""".trimMargin(),
                root = root
            )
        }
    }

    @Test
    fun `sources downloaded even if variant is not defined in Gradle metadata`(testInfo: TestInfo) {
        val library = "com.fasterxml.jackson.core:jackson-core:2.17.2"

        val root = doTest(
            testInfo,
            dependency = listOf(library),
            platform = setOf(ResolutionPlatform.JVM),
            expected = """root
                |\--- com.fasterxml.jackson.core:jackson-core:2.17.2
                |     \--- com.fasterxml.jackson:jackson-bom:2.17.2
            """.trimMargin(),
            verifyMessages = false,
        )

        runBlocking {
            downloadAndAssertFiles(
                withSources = true,
                files = """
                    |jackson-core-2.17.2-sources.jar
                    |jackson-core-2.17.2.jar""".trimMargin(),
                root = root
            )
        }
    }
}