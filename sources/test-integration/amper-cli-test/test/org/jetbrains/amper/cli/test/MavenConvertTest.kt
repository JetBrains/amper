/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
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
        assertEquals("""
            mavenPlugins:
              - org.apache.maven.plugins:maven-enforcer-plugin:3.6.1
              - io.spring.javaformat:spring-javaformat-maven-plugin:0.0.47
              - org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0
              - org.graalvm.buildtools:native-maven-plugin:0.11.0
              - org.jacoco:jacoco-maven-plugin:0.8.13
              - io.github.git-commit-id:git-commit-id-maven-plugin:9.0.2
              - org.cyclonedx:cyclonedx-maven-plugin:2.9.1

        """.trimIndent(), (buildResult.projectRoot / "project.yaml").readText())
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

            plugins:
              maven-enforcer-plugin.display-info: enabled
              maven-enforcer-plugin.enforce:
                enabled: true
                rules:
                  requireJavaVersion:
                    message: This build requires at least Java 25, update your JVM, and run the build again
                    version: 25
              maven-enforcer-plugin.help: enabled
              spring-javaformat-maven-plugin.apply: enabled
              spring-javaformat-maven-plugin.help: enabled
              spring-javaformat-maven-plugin.validate: enabled
              maven-checkstyle-plugin.check:
                enabled: true
                configLocation: src/checkstyle/nohttp-checkstyle.xml
                sourceDirectories:
                  - ${'$'}{basedir}
                includes: '**/*'
                excludes: '**/.git/**/*,**/.idea/**/*,**/target/**/,**/.flattened-pom.xml,**/*.class'
                propertyExpansion: config_loc=${'$'}{basedir}/src/checkstyle/
              maven-checkstyle-plugin.checkstyle: enabled
              maven-checkstyle-plugin.checkstyle-aggregate: enabled
              maven-checkstyle-plugin.help: enabled
              native-maven-plugin.add-reachability-metadata: enabled
              native-maven-plugin.build: enabled
              native-maven-plugin.compile: enabled
              native-maven-plugin.compile-no-fork: enabled
              native-maven-plugin.generateResourceConfig: enabled
              native-maven-plugin.generateTestResourceConfig: enabled
              native-maven-plugin.merge-agent-files: enabled
              native-maven-plugin.metadata-copy: enabled
              native-maven-plugin.test: enabled
              native-maven-plugin.write-args-file: enabled
              jacoco-maven-plugin.check: enabled
              jacoco-maven-plugin.dump: enabled
              jacoco-maven-plugin.help: enabled
              jacoco-maven-plugin.instrument: enabled
              jacoco-maven-plugin.merge: enabled
              jacoco-maven-plugin.prepare-agent: enabled
              jacoco-maven-plugin.prepare-agent-integration: enabled
              jacoco-maven-plugin.report: enabled
              jacoco-maven-plugin.report-aggregate: enabled
              jacoco-maven-plugin.report-integration: enabled
              jacoco-maven-plugin.restore-instrumented-classes: enabled
              git-commit-id-maven-plugin.revision:
                enabled: true
                failOnNoGitDirectory: false
                failOnUnableToExtractRepoInfo: false
                verbose: true
                generateGitPropertiesFile: true
                generateGitPropertiesFilename: ${'$'}{project.build.outputDirectory}/git.properties
              git-commit-id-maven-plugin.validateRevision: enabled
              cyclonedx-maven-plugin.makeAggregateBom:
                enabled: true
                projectType: application
                outputDirectory: ${'$'}{project.build.outputDirectory}/META-INF/sbom
                outputFormat: json
                outputName: application.cdx
              cyclonedx-maven-plugin.makeBom: enabled
              cyclonedx-maven-plugin.makePackageBom: enabled

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

        assertTrue((buildResult.projectRoot / "module.yaml").notExists())

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

    @Test
    fun `surefire-plugin`() = runSlowTest {
        val projectRoot = testProject("maven-convert/surefire-plugin")

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

        """.trimIndent(), (buildResult.projectRoot / "module.yaml").readText()
        )

        val converted = testProject(buildResult.projectRoot.pathString)

        runCli(converted, "test")
    }

    @Test
    fun `duplicate-executions`() = runSlowTest {
        val projectRoot = testProject("maven-convert/duplicate-executions")

        val buildResult = runCli(projectRoot, "tool", "convert-project", copyToTempDir = true)

        val pomPath = buildResult.projectRoot / "pom.xml"
        assertEquals("""
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

            plugins:
              # WARNING: Plugin io.github.git-commit-id:git-commit-id-maven-plugin has goals [revision] configured in multiple executions.
              # Execution [default] was translated to Amper, but manual configuration may be required for other executions.
              git-commit-id-maven-plugin.revision:
                enabled: true
                verbose: true
                generateGitPropertiesFile: true
                generateGitPropertiesFilename: ${'$'}{project.build.outputDirectory}/git.properties
                # The following executions were not translated automatically (only a single one was):
                # Reference: $pomPath:56:32
                # <execution>
                #   <id>git-full</id>
                #   <goals>
                #     <goal>revision</goal>
                #   </goals>
                #   <configuration>
                #     <generateGitPropertiesFile>true</generateGitPropertiesFile>
                #     <commitIdGenerationMode>full</commitIdGenerationMode>
                #   </configuration>
                # </execution>
                # Reference: $pomPath:66:32
                # <execution>
                #   <id>git-flat</id>
                #   <goals>
                #     <goal>revision</goal>
                #   </goals>
                #   <configuration>
                #     <generateGitPropertiesFile>true</generateGitPropertiesFile>
                #     <commitIdGenerationMode>flat</commitIdGenerationMode>
                #   </configuration>
                # </execution>
              git-commit-id-maven-plugin.validateRevision: enabled

            test-dependencies:
              - org.springframework.boot:spring-boot-starter-test:4.0.0

        """.trimIndent(), (buildResult.projectRoot / "module.yaml").readText())
    }
}
