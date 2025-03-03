/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.core.UsedVersions
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.pathString
import kotlin.io.path.readText
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

        val pom = groupDir.resolve("artifactName/2.2/artifactName-2.2.pom")
        assertEquals("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <modelVersion>4.0.0</modelVersion>
              <groupId>amper.test.jvm-publish</groupId>
              <artifactId>artifactName</artifactId>
              <version>2.2</version>
              <name>jvm-publish</name>
              <dependencies>
                <dependency>
                  <groupId>org.jetbrains.kotlin</groupId>
                  <artifactId>kotlin-stdlib</artifactId>
                  <version>${UsedVersions.kotlinVersion}</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>io.ktor</groupId>
                  <artifactId>ktor-client-core-jvm</artifactId>
                  <version>2.3.9</version>
                  <scope>compile</scope>
                </dependency>
                <dependency>
                  <groupId>io.ktor</groupId>
                  <artifactId>ktor-client-java-jvm</artifactId>
                  <version>2.3.9</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-coroutines-core-jvm</artifactId>
                  <version>1.7.1</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-serialization-core-jvm</artifactId>
                  <version>1.6.3</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-serialization-json-jvm</artifactId>
                  <version>1.6.3</version>
                  <scope>provided</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-serialization-cbor-jvm</artifactId>
                  <version>1.6.3</version>
                  <scope>compile</scope>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent(), pom.readText().trim())
    }
}