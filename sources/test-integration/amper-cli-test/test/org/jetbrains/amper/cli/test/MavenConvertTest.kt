/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.div
import kotlin.io.path.exists
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

        assertTrue((buildResult.projectRoot / "project.yaml").exists())
        assertTrue((buildResult.projectRoot / "project.yaml").readText().isBlank())
        assertTrue((buildResult.projectRoot / "module.yaml").exists())
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
              springBoot:
                enabled: true
                version: 4.0.0

            dependencies:
              - bom: org.springframework.boot:spring-boot-starter-parent:4.0.0
              - org.springframework.boot:spring-boot-starter:4.0.0: exported

            test-dependencies:
              - org.springframework.boot:spring-boot-starter-test:4.0.0

        """.trimIndent(), (buildResult.projectRoot / "module.yaml").readText()
        )

        val converted = testProject(buildResult.projectRoot.pathString)

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

        val moduleYaml = firstRun.projectRoot / "module.yaml"
        assertTrue(moduleYaml.exists())

        moduleYaml.writeText("CHANGED")

        runCli(firstRun.projectRoot, "tool", "convert-project", "--overwrite-existing")

        assertTrue(moduleYaml.exists())
        assertNotEquals("CHANGED", moduleYaml.readText())
    }

    @Test
    fun `convert fails when files already exist and overwrite is not enabled`() = runSlowTest {
        val projectRoot = testProject("maven-convert/spring-boot")

        val firstRun = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        val secondRun = runCli(
            firstRun.projectRoot,
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

        assertTrue((buildResult.projectRoot / "project.yaml").exists())
        assertTrue((buildResult.projectRoot / "module.yaml").exists())
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
              kotlin:
                version: 2.2.21
                freeCompilerArgs:
                  - -Xjsr305=strict
                  - -Xannotation-default-target=param-property
              jvm:
                release: 17
                storeParameterNames: true
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

        """.trimIndent(), (buildResult.projectRoot / "module.yaml").readText()
        )

        val converted = testProject(buildResult.projectRoot.pathString)

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

        assertTrue((buildResult.projectRoot / "project.yaml").exists())
        assertTrue((buildResult.projectRoot / "module.yaml").exists())
        assertEquals(
            """
            product: jvm/app

            layout: maven-like

            settings:
              publishing:
                enabled: true
                name: spring-petclinic
                group: org.springframework.samples
                version: 4.0.0-SNAPSHOT
              java:
                freeCompilerArgs:
                  - -XDcompilePolicy=simple
                  - --should-stop=ifError=FLOW
                  - -Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR -XepOpt:NullAway:OnlyNullMarked=true -XepOpt:NullAway:CustomContractAnnotations=org.springframework.lang.Contract -XepOpt:NullAway:JSpecifyMode=true
                annotationProcessing:
                  processors:
                    - com.google.errorprone:error_prone_core:2.42.0
                    - com.uber.nullaway:nullaway:0.12.10
              jvm:
                storeParameterNames: true
              springBoot:
                enabled: true
                version: 4.0.0-M3

            dependencies:
              - bom: org.springframework.boot:spring-boot-starter-parent:4.0.0-M3
              - org.springframework.boot:spring-boot-starter-actuator:4.0.0-M3: exported
              - org.springframework.boot:spring-boot-starter-cache:4.0.0-M3: exported
              - org.springframework.boot:spring-boot-starter-data-jpa:4.0.0-M3: exported
              - org.springframework.boot:spring-boot-starter-web:4.0.0-M3: exported
              - org.springframework.boot:spring-boot-starter-validation:4.0.0-M3: exported
              - org.springframework.boot:spring-boot-starter-thymeleaf:4.0.0-M3: exported
              - com.h2database:h2:2.3.232: runtime-only
              - com.mysql:mysql-connector-j:9.4.0: runtime-only
              - org.postgresql:postgresql:42.7.7: runtime-only
              - javax.cache:cache-api:1.1.1: exported
              - com.github.ben-manes.caffeine:caffeine:3.2.2: exported
              - org.webjars:webjars-locator-lite:1.1.1: exported
              - org.webjars.npm:bootstrap:5.3.8: exported
              - org.webjars.npm:font-awesome:4.7.0: exported
              - jakarta.xml.bind:jakarta.xml.bind-api:4.0.2: exported

            test-dependencies:
              - org.springframework.boot:spring-boot-starter-test:4.0.0-M3
              - org.springframework.boot:spring-boot-starter-restclient:4.0.0-M3
              - com.h2database:h2:2.3.232: exported
              - com.mysql:mysql-connector-j:9.4.0: exported
              - org.postgresql:postgresql:42.7.7: exported
              - org.springframework.boot:spring-boot-devtools:4.0.0-M3
              - org.springframework.boot:spring-boot-testcontainers:4.0.0-M3
              - org.springframework.boot:spring-boot-docker-compose:4.0.0-M3
              - org.testcontainers:junit-jupiter:1.21.3
              - org.testcontainers:mysql:1.21.3

        """.trimIndent(), (buildResult.projectRoot / "module.yaml").readText()
        )

        // TODO: until we support error_prone annotation processor
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

        assertTrue((buildResult.projectRoot / "project.yaml").exists())
        assertTrue((buildResult.projectRoot / "module.yaml").exists())
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

        """.trimIndent(), (buildResult.projectRoot / "module.yaml").readText()
        )

        val converted = testProject(buildResult.projectRoot.pathString)

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

        assertTrue((buildResult.projectRoot / "project.yaml").exists())
        assertEquals(
            """
            modules:
              - lib
              - app

        """.trimIndent(), (buildResult.projectRoot / "project.yaml").readText()
        )
        assertTrue((buildResult.projectRoot / "app" / "module.yaml").exists())
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

        """.trimIndent(), (buildResult.projectRoot / "app" / "module.yaml").readText()
        )
        assertTrue((buildResult.projectRoot / "lib" / "module.yaml").exists())
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

        """.trimIndent(), (buildResult.projectRoot / "lib" / "module.yaml").readText()
        )

        val converted = testProject(buildResult.projectRoot.pathString)

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
}
