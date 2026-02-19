/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.assertEqualsIgnoreLineSeparator
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Execution(ExecutionMode.CONCURRENT)
class MavenConvertTest : AmperCliTestBase() {
    @Test
    fun `spring-boot`() = runSlowTest {
        val projectRoot = testProject("maven-convert/spring-boot")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
        assertTrue((buildResult.projectDir / "project.yaml").readText().isBlank())
        assertTrue((buildResult.projectDir / "module.yaml").exists())
        assertEquals(
            """
            product: jvm/app

            layout: maven-like

            settings:
              publishing:
                enabled: true
                name: demo
                group: com.example
                version: 0.0.1-SNAPSHOT
              jvm:
                storeParameterNames: true
              springBoot:
                enabled: true
                version: 4.0.0

            dependencies:
              - bom: org.springframework.boot:spring-boot-starter-parent:4.0.0
              - org.springframework.boot:spring-boot-starter:4.0.0: exported

            test-dependencies:
              - org.springframework.boot:spring-boot-starter-test:4.0.0

        """.trimIndent(), (buildResult.projectDir / "module.yaml").readText()
        )

        val converted = testProject(buildResult.projectDir.pathString)

        runCli(
            converted,
            "test",
            // warning about mockito loaded dynamically
            assertEmptyStdErr = false,
        )
    }

    @Test
    fun `convert overwrites existing files when overwrite is enabled`() = runSlowTest {
        val projectRoot = testProject("maven-convert/spring-boot")

        val firstRun = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        val moduleYaml = firstRun.projectDir / "module.yaml"
        assertTrue(moduleYaml.exists())

        moduleYaml.writeText("CHANGED")

        runCli(firstRun.projectDir, "tool", "convert-project", "--overwrite-existing")

        assertTrue(moduleYaml.exists())
        assertNotEquals("CHANGED", moduleYaml.readText())
    }

    @Test
    fun `convert fails when files already exist and overwrite is not enabled`() = runSlowTest {
        val projectRoot = testProject("maven-convert/spring-boot")

        val firstRun = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        val secondRun = runCli(
            firstRun.projectDir,
            "tool", "convert-project",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        secondRun.assertStderrContains("File already exists")
    }

    @Test
    fun `spring-boot-kotlin`() = runSlowTest {
        val projectRoot = testProject("maven-convert/spring-boot-kotlin")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
        assertTrue((buildResult.projectDir / "module.yaml").exists())
        assertEquals(
            """
            product: jvm/app

            layout: maven-like

            settings:
              publishing:
                enabled: true
                name: demo
                group: com.example
                version: 0.0.1-SNAPSHOT
              jvm:
                storeParameterNames: true
                release: 17
              kotlin:
                version: 2.2.21
                freeCompilerArgs:
                  - -Xjsr305=strict
                  - -Xannotation-default-target=param-property
              springBoot:
                enabled: true
                version: 4.0.0

            dependencies:
              - bom: org.springframework.boot:spring-boot-starter-parent:4.0.0
              - org.springframework.boot:spring-boot-starter:4.0.0: exported
              - org.jetbrains.kotlin:kotlin-reflect:2.2.21: exported
              - org.jetbrains.kotlin:kotlin-stdlib:2.2.21: exported

            test-dependencies:
              - org.springframework.boot:spring-boot-starter-test:4.0.0
              - org.jetbrains.kotlin:kotlin-test-junit5:2.2.21

        """.trimIndent(), (buildResult.projectDir / "module.yaml").readText()
        )

        val converted = testProject(buildResult.projectDir.pathString)

        runCli(
            converted,
            "test",
            // warning about mockito loaded dynamically
            assertEmptyStdErr = false,
        )
    }

    @Test
    fun `spring-petclinic`() = runSlowTest {
        val projectRoot = testProject("maven-convert/spring-petclinic")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        val expectedProjectFile = projectRoot / "expected-project.yaml"
        val actualProjectFile = buildResult.projectDir.resolve("project.yaml")
        assertTrue(actualProjectFile.exists())
        assertEqualsIgnoreLineSeparator(
            expectedContent = expectedProjectFile.readText(),
            actualContent = actualProjectFile.readText(),
            originalFile = expectedProjectFile,
        )

        val expectedModuleFile = projectRoot.resolve("expected-module.yaml")
        val actualModuleFile = buildResult.projectDir / "module.yaml"
        assertTrue(actualModuleFile.exists())
        assertEqualsIgnoreLineSeparator(
            expectedContent = expectedModuleFile.readText(),
            actualContent = actualModuleFile.readText(),
            originalFile = expectedModuleFile,
        )

        // TODO: until we fix AMPER-5023 PlexusConfiguration type isn't supported
//        val converted = testProject(buildResult.projectRoot.pathString)
//
//        runCli(
//            converted,
//            "test",
//            // warning about mockito loaded dynamically
//            assertEmptyStdErr = false,
//        )
    }

    @Test
    fun `annotation-processing`() = runSlowTest {
        val projectRoot = testProject("maven-convert/annotation-processing")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
        assertTrue((buildResult.projectDir / "module.yaml").exists())
        assertEquals(
            """
            product: jvm/app
            
            layout: maven-like
            
            settings:
              publishing:
                enabled: true
                name: demo18
                group: org.example
                version: 0.0.1-SNAPSHOT
              java:
                annotationProcessing:
                  processors:
                    - org.springframework.boot:spring-boot-configuration-processor
                    - org.projectlombok:lombok
              jvm:
                storeParameterNames: true
              springBoot:
                enabled: true
                version: 4.0.0
            
            dependencies:
              - bom: org.springframework.boot:spring-boot-starter-parent:4.0.0
              - org.springframework.boot:spring-boot-starter:4.0.0: exported
              - org.projectlombok:lombok:1.18.42: exported
            
            test-dependencies:
              - org.springframework.boot:spring-boot-starter-test:4.0.0

        """.trimIndent(), (buildResult.projectDir / "module.yaml").readText()
        )

        val converted = testProject(buildResult.projectDir.pathString)

        val testsResult = runCli(
            converted,
            "test",
            // warning about mockito loaded dynamically
            assertEmptyStdErr = false,
        )

        testsResult.assertStdoutContains("post construct")
    }

    @Test
    fun `multi-module`() = runSlowTest {
        val projectRoot = testProject("maven-convert/multi-module")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
        assertEquals(
            """
            modules:
              - lib
              - app

        """.trimIndent(), (buildResult.projectDir / "project.yaml").readText()
        )
        assertTrue((buildResult.projectDir / "app" / "module.yaml").exists())
        assertEquals(
            """
            product: jvm/lib
            
            layout: maven-like
            
            settings:
              publishing:
                enabled: true
                name: app
                group: com.example
                version: 1.0.0
              jvm:
                release: 17
                storeParameterNames: true
            
            dependencies:
              - bom: org.springframework.boot:spring-boot-starter-parent:3.5.6
              - ../lib: exported
            
            test-dependencies:
              - org.junit.jupiter:junit-jupiter:5.12.2

        """.trimIndent(), (buildResult.projectDir / "app" / "module.yaml").readText()
        )
        assertTrue((buildResult.projectDir / "lib" / "module.yaml").exists())
        assertEquals(
            """
            product: jvm/lib

            layout: maven-like

            settings:
              publishing:
                enabled: true
                name: lib
                group: com.example
                version: 1.0.0
              jvm:
                release: 17
                storeParameterNames: true

            dependencies:
              - bom: org.springframework.boot:spring-boot-starter-parent:3.5.6

        """.trimIndent(), (buildResult.projectDir / "lib" / "module.yaml").readText()
        )

        assertTrue((buildResult.projectDir / "module.yaml").notExists())

        val converted = testProject(buildResult.projectDir.pathString)

        runCli(converted, "test")
    }

    @Test
    fun `not-a-maven-project`() = runSlowTest {
        val projectRoot = testProject("maven-convert/not-a-maven-project")

        val buildResult = runCli(
            projectRoot,
            "tool",
            "convert-project",
            expectedExitCode = 1,
            assertEmptyStdErr = false
        )

        buildResult.assertStderrContains("ERROR: pom.xml file not found")
    }

    @Test
    fun `surefire-plugin`() = runSlowTest {
        val projectDir = testProject("maven-convert/surefire-plugin")

        val buildResult = runCli(projectDir, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
        assertTrue((buildResult.projectDir / "module.yaml").exists())

        assertEquals(
            """
            product: jvm/app

            layout: maven-like

            settings:
              publishing:
                enabled: true
                name: surefire-demo
                group: com.example
                version: 0.0.1-SNAPSHOT
              jvm:
                storeParameterNames: true
              springBoot:
                enabled: true
                version: 4.0.0

            dependencies:
              - bom: org.springframework.boot:spring-boot-starter-parent:4.0.0
              - org.springframework.boot:spring-boot-starter:4.0.0: exported

            test-settings:
              jvm:
                test:
                  freeJvmArgs:
                    - -Xmx512m
                    - -ea
                  extraEnvironment:
                    MY_TEST_ENV: test-value
                    ANOTHER_ENV: another-value
                  systemProperties:
                    my.test.prop: prop-value
                    another.prop: another-prop-value
                  # WARNING: The following configuration is not supported in Amper:
                  # <includes>
                  #   <include>**/*Test.java</include>
                  # </includes>

            test-dependencies:
              - org.springframework.boot:spring-boot-starter-test:4.0.0

        """.trimIndent(), (buildResult.projectDir / "module.yaml").readText()
        )

        val converted = testProject(buildResult.projectDir.pathString)

        runCli(converted, "test")
    }

    @Test
    fun `duplicate-executions`() = runSlowTest {
        val projectRoot = testProject("maven-convert/duplicate-executions")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        val pomPath = buildResult.projectDir / "pom.xml"
        val actualModuleFile = buildResult.projectDir / "module.yaml"
        val actualModuleSubstitutedPom = actualModuleFile
            .readText()
            .replace(pomPath.absolutePathString(), $$"$pom")
        val expectedModuleFile = projectRoot / "expected-module.yaml"
        assertEqualsIgnoreLineSeparator(
            expectedContent = expectedModuleFile.readText(),
            actualContent = actualModuleSubstitutedPom,
            originalFile = expectedModuleFile,
        )
    }
}
