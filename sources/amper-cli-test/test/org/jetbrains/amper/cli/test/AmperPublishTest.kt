/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperPublishTest : AmperCliTestBase() {

    @Test
    fun publish() = runSlowTest {
        val mavenLocalForTest = tempRoot.resolve(".m2.test").also { it.createDirectories() }
        val groupDir = mavenLocalForTest.resolve("amper/test/jvm-publish")

        runCli(
            projectRoot = testProject("jvm-publish"),
            "publish", "mavenLocal",
            amperJvmArgs = listOf("-Dmaven.repo.local=\"${mavenLocalForTest.absolutePathString()}\""),
        )

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
}