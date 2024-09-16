/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

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
}