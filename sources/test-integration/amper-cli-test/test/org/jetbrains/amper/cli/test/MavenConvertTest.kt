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
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Ignore
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
              - bom: org.springframework.boot:spring-boot-dependencies:4.0.0
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
              - bom: org.springframework.boot:spring-boot-dependencies:4.0.0
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
        // todo: uncomment when AMPER-5025 will be fixed
//        val converted = testProject(buildResult.projectDir.pathString)
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
              - bom: org.springframework.boot:spring-boot-dependencies:4.0.0
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
              - bom: org.springframework.boot:spring-boot-dependencies:3.5.6
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
              - bom: org.springframework.boot:spring-boot-dependencies:3.5.6
              - bom: org.springframework.boot:spring-boot-starter-parent:3.5.6

        """.trimIndent(), (buildResult.projectDir / "lib" / "module.yaml").readText()
        )

        assertTrue((buildResult.projectDir / "module.yaml").notExists())

        val converted = testProject(buildResult.projectDir.pathString)

        runCli(converted, "test")
    }

    @Test
    fun `multi-module-nested`() = runSlowTest {
        val projectRoot = testProject("maven-convert/multi-module-nested")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
        assertEquals(
            """
            modules:
              - parent-only/nested-module

            """.trimIndent(), (buildResult.projectDir / "project.yaml").readText()
        )
        assertTrue((buildResult.projectDir / "parent-only" / "nested-module" / "module.yaml").exists())
        assertTrue((buildResult.projectDir / "module.yaml").notExists())
        assertTrue((buildResult.projectDir / "parent-only" / "module.yaml").notExists())
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
    fun `pom-dependency-type-local`() = runSlowTest {
        val projectRoot = testProject("maven-convert/pom-dependency-type")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
        assertEquals(
            """
            modules:
              - app
              - deps-pom

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

            dependencies:
              - ../deps-pom: exported

            """.trimIndent(), (buildResult.projectDir / "app" / "module.yaml").readText()
        )

        assertTrue((buildResult.projectDir / "deps-pom" / "module.yaml").exists())
        assertEquals(
            """
            product: jvm/lib
            
            layout: maven-like

            settings:
              publishing:
                enabled: true
                name: deps-pom
                group: com.example
                version: 1.0.0

            dependencies:
              - org.slf4j:slf4j-api:2.0.9: exported
              - com.google.guava:guava:32.1.3-jre: runtime-only

            """.trimIndent(), (buildResult.projectDir / "deps-pom" / "module.yaml").readText()
        )
    }

    @Test
    fun `pom-dependency-type-external`() = runSlowTest {
        val projectRoot = testProject("maven-convert/pom-dependency-type-external")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
        assertTrue((buildResult.projectDir / "module.yaml").exists())

        val pomPath = buildResult.projectDir / "pom.xml"

        assertEquals(
            """
            product: jvm/lib

            layout: maven-like

            settings:
              publishing:
                enabled: true
                name: pom-dependency-type-external-test
                group: com.example
                version: 1.0.0

            dependencies:
              - org.apache.commons:commons-lang3:3.20.0: exported
              # WARNING: Amper does not support external POM dependencies with scopes different than import, manual configuration may be required.
              # Reference: $pomPath:18:21
              # <dependency>
              #   <groupId>org.apache.hadoop</groupId>
              #   <artifactId>hadoop-client-check-invariants</artifactId>
              #   <version>3.3.6</version>
              #   <type>pom</type>
              #   <scope>compile</scope>
              # </dependency>

            """.trimIndent(), (buildResult.projectDir / "module.yaml").readText()
        )
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
              - bom: org.springframework.boot:spring-boot-dependencies:4.0.0
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
    fun `parent with repository`() = runSlowTest {
        val projectRoot = testProject("maven-convert/parent-with-repository")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
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

            repositories:
              -
                id: test-custom-repo
                url: https://custom.example.com/maven

        """.trimIndent(), (buildResult.projectDir / "app" / "module.yaml").readText()
        )
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

    @Test
    fun `transitive compile classpath visibility`() = runSlowTest {
        val projectRoot = testProject("maven-convert/transitive-compile-classpath-visibility")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        assertTrue((buildResult.projectDir / "project.yaml").exists())
        assertEquals(
            """
            modules:
              - core
              - app

            """.trimIndent(), (buildResult.projectDir / "project.yaml").readText()
        )
        assertTrue((buildResult.projectDir / "core" / "module.yaml").exists())
        assertEquals(
            """
            product: jvm/lib

            layout: maven-like

            settings:
              publishing:
                enabled: true
                name: core
                group: com.example
                version: 1.0-SNAPSHOT

            dependencies:
              - com.fasterxml.jackson.core:jackson-databind:2.17.2: exported

            """.trimIndent(), (buildResult.projectDir / "core" / "module.yaml").readText()
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
                version: 1.0-SNAPSHOT

            dependencies:
              - ../core: exported

            """.trimIndent(), (buildResult.projectDir / "app" / "module.yaml").readText()
        )
        assertTrue((buildResult.projectDir / "module.yaml").notExists())

        val converted = testProject(buildResult.projectDir.pathString)

        runCli(converted, "build")
    }

}
