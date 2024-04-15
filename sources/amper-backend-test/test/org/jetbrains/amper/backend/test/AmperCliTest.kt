/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.fileSize
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class AmperCliTest: AmperCliTestBase() {
    @Test
    fun smoke() = runTestInfinitely {
        runCli("jvm-kotlin-test-smoke", "tasks")
    }

    @Test
    fun `run command help prints dot dot`() = runTestInfinitely {
        val r = runCli("jvm-kotlin-test-smoke", "run", "--help")

        // Check that '--' is printed before program arguments
        val string = "Usage: amper run [<options>] -- [<program arguments>]..."

        assertTrue("There should be '$string' in `run --help` output") {
            r.stdout.lines().any { it == string }
        }
    }

    @Test
    fun publish() = runTestInfinitely {
        val m2repository = Path.of(System.getProperty("user.home"), ".m2/repository")
        val groupDir = m2repository.resolve("amper").resolve("test")
        groupDir.deleteRecursively()

        runCli("jvm-publish", "publish", "mavenLocal")

        val files = groupDir.walk()
            .onEach {
                check(it.fileSize() > 0) { "File should not be empty: $it" }
            }
            .map { it.relativeTo(groupDir).pathString.replace('\\', '/') }
            .sorted()
        assertEquals(
            """
                artifactName/2.2/_remote.repositories
                artifactName/2.2/artifactName-2.2-sources.jar
                artifactName/2.2/artifactName-2.2.jar
                artifactName/2.2/artifactName-2.2.pom
                artifactName/maven-metadata-local.xml
            """.trimIndent(), files.joinToString("\n")
        )
    }

    override val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")
}
