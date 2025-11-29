/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.assertStdoutDoesNotContain
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.normalizeLineSeparators
import java.io.File
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class PluginsTest : AmperCliTestBase() {
    @Test
    fun `single plugin - contributes source file when enabled`() = runSlowTest {
        val r1 = runCli(
            projectRoot = testProject("extensibility/single-local-plugin"),
            "show", "tasks",
            copyToTempDir = true,
        )

        with(r1) {
            assertStdoutContains("task :app1:generate-konfig@build-konfig -> :build-konfig-plugin:runtimeClasspathJvm")
            assertStdoutContains(
                "task :app1:print-generated-sources@build-konfig -> " +
                        ":build-konfig-plugin:runtimeClasspathJvm, :app1:generate-konfig@build-konfig"
            )
        }

        val r2 = runCli(
            projectRoot = r1.projectRoot,
            "run", "-m", "app1",
        )

        r2.assertStdoutContains("version: 1.0+hello; id: table-green-geese")
    }

    @Test
    fun `distribution plugin`() = runSlowTest {
        val taskName = ":app:build@distribution-plugin"
        val r1 = runCli(
            projectRoot = testProject("extensibility/distribution"),
            "task", taskName,
            copyToTempDir = true,
        )

        val buildDir = tempRoot / "build"
        val projectRoot = r1.projectRoot

        // We expect Kotlin 2.2.10 specifically in the 'from-catalog' and 'compile' classpaths because the 'app' module
        // overrides settings.kotlin.version.
        // In the 'core' and 'lib' modules, the Kotlin version is not overridden, so we expect the default in the
        // corresponding classpaths.
        // In 'base', the runtime classpath gets the default Kotlin version transitively from core/lib because it's
        // higher, so we expect the default Kotlin version even though it sets Kotlin to 2.2.10 explicitly.
        r1.assertCustomTaskStdoutContains(
            taskName = taskName,
            output = """
            Hello from distribution
            classpath base.dependencies = [{modulePath: $projectRoot/app}]
            classpath base.dependencies[0] = {modulePath: $projectRoot/app}
            classpath base.dependencies[0].modulePath = $projectRoot/app
            classpath base.resolvedFiles = [$buildDir/tasks/_app_jarJvm/app-jvm.jar, $buildDir/tasks/_lib_jarJvm/lib-jvm.jar, $buildDir/tasks/_core_jarJvm/core-jvm.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/kotlin/kotlin-stdlib/${UsedVersions.defaultKotlinVersion}/kotlin-stdlib-${UsedVersions.defaultKotlinVersion}.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/annotations/13.0/annotations-13.0.jar]
            classpath core.dependencies = [{modulePath: $projectRoot/core}]
            classpath core.dependencies[0] = {modulePath: $projectRoot/core}
            classpath core.dependencies[0].modulePath = $projectRoot/core
            classpath core.resolvedFiles = [$buildDir/tasks/_core_jarJvm/core-jvm.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/kotlin/kotlin-stdlib/${UsedVersions.defaultKotlinVersion}/kotlin-stdlib-${UsedVersions.defaultKotlinVersion}.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/annotations/13.0/annotations-13.0.jar]
            classpath lib.dependencies = [{modulePath: $projectRoot/lib}]
            classpath lib.dependencies[0] = {modulePath: $projectRoot/lib}
            classpath lib.dependencies[0].modulePath = $projectRoot/lib
            classpath lib.resolvedFiles = [$buildDir/tasks/_lib_jarJvm/lib-jvm.jar, $buildDir/tasks/_core_jarJvm/core-jvm.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/kotlin/kotlin-stdlib/${UsedVersions.defaultKotlinVersion}/kotlin-stdlib-${UsedVersions.defaultKotlinVersion}.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/annotations/13.0/annotations-13.0.jar]
            classpath kotlin-poet.dependencies = [{coordinates: com.squareup:kotlinpoet:2.2.0}]
            classpath kotlin-poet.dependencies[0] = {coordinates: com.squareup:kotlinpoet:2.2.0}
            classpath kotlin-poet.dependencies[0].coordinates = com.squareup:kotlinpoet:2.2.0
            classpath kotlin-poet.resolvedFiles = [${Dirs.userCacheRoot}/.m2.cache/com/squareup/kotlinpoet-jvm/2.2.0/kotlinpoet-jvm-2.2.0.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/kotlin/kotlin-stdlib/2.1.21/kotlin-stdlib-2.1.21.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/kotlin/kotlin-reflect/2.1.21/kotlin-reflect-2.1.21.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/annotations/13.0/annotations-13.0.jar]
            classpath from-catalog.dependencies = [{coordinates: org.jetbrains.kotlin:kotlin-reflect:2.2.10}]
            classpath from-catalog.dependencies[0] = {coordinates: org.jetbrains.kotlin:kotlin-reflect:2.2.10}
            classpath from-catalog.dependencies[0].coordinates = org.jetbrains.kotlin:kotlin-reflect:2.2.10
            classpath from-catalog.resolvedFiles = [${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/kotlin/kotlin-reflect/2.2.10/kotlin-reflect-2.2.10.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/kotlin/kotlin-stdlib/2.2.10/kotlin-stdlib-2.2.10.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/annotations/13.0/annotations-13.0.jar]
            classpath compile.dependencies = [{modulePath: $projectRoot/app}]
            classpath compile.dependencies[0] = {modulePath: $projectRoot/app}
            classpath compile.dependencies[0].modulePath = $projectRoot/app
            classpath compile.resolvedFiles = [$buildDir/tasks/_app_jarJvm/app-jvm.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/kotlin/kotlin-stdlib/2.2.10/kotlin-stdlib-2.2.10.jar, ${Dirs.userCacheRoot}/.m2.cache/org/jetbrains/annotations/13.0/annotations-13.0.jar]
            compilation result: {from: {modulePath: $projectRoot/app}}
            compilation result path: $buildDir/tasks/_app_jarJvm/app-jvm.jar
        """.trimIndent().replace('/', File.separatorChar))
    }

    @Test
    fun `sources injection test`() = runSlowTest {
        val r1 = runCli(
            projectRoot = testProject("extensibility/sources"),
            "task", ":app1:consume@consume-sources-plugin",
            copyToTempDir = true,
        )

        val projectRoot = r1.projectRoot
        val buildDir = tempRoot / "build"
        r1.assertCustomTaskStdoutContains(
            taskName = ":app1:consume@consume-sources-plugin",
            output = """
            Consuming sources: 1
            Got source path: ${projectRoot / "app1" / "src"} - [main.kt]
        """.trimIndent())

        runCli(
            projectRoot = projectRoot,
            "task", ":app2:consume@consume-sources-plugin",
        ).assertCustomTaskStdoutContains(
            taskName = ":app2:consume@consume-sources-plugin",
            output = """
            Consuming sources: 4
            Got source path: ${projectRoot / "app2" / "src"} - [main.kt]
            Got source path: ${buildDir / "generated" / "app2" / "main" / "src" / "ksp" / "kotlin"} - [kspGenerated.kt]
            Got source path: ${buildDir / "generated" / "app2" / "main" / "src" / "ksp" / "java"} - []
            Got source path: ${buildDir / "tasks" / "_app2_produceSources@produce-sources-plugin" / "kotlin"} - [generated.kt]
        """.trimIndent())

        runCli(
            projectRoot = projectRoot,
            "task", ":app3:consume@consume-sources-plugin",
        ).assertCustomTaskStdoutContains(
            taskName = ":app3:consume@consume-sources-plugin",
            output = """
            Consuming sources: 3
            Got source path: ${projectRoot / "app3" / "resources"} - [hello]
            Got source path: ${buildDir / "generated" / "app3" / "main" / "resources" / "ksp" } - [com.example.amper.app.Greeter]
            Got source path: ${buildDir / "tasks" / "_app3_produceSources@produce-sources-plugin" / "resources"} - [generated.properties]
        """.trimIndent())

        runCli(
            projectRoot = projectRoot,
            "task", ":kmp-lib:consume@consume-sources-plugin",
        ).assertCustomTaskStdoutContains(
            taskName = ":kmp-lib:consume@consume-sources-plugin",
            output = """
            Consuming sources: 2
            Got source path: ${projectRoot / "kmp-lib" / "src"} - null
            Got source path: ${projectRoot / "kmp-lib" / "src@jvm"} - null
        """.trimIndent())

        runCli(
            projectRoot = projectRoot,
            "task", ":kmp-lib2:consume@consume-sources-plugin",
        ).assertCustomTaskStdoutContains(
            taskName = ":kmp-lib2:consume@consume-sources-plugin",
            output = """
            Consuming sources: 2
            Got source path: ${projectRoot / "kmp-lib2" / "resources"} - null
            Got source path: ${projectRoot / "kmp-lib2" / "resources@jvm"} - null
        """.trimIndent())
    }

    @Test
    fun `execution avoidance - enabled by default, disabled for no-outputs tasks`() = runSlowTest {
        val r1 = runCli(
            projectRoot = testProject("extensibility/single-local-plugin"),
            "task", ":app1:print-generated-sources@build-konfig",
            copyToTempDir = true,
        )

        with(r1) {
            assertStdoutContains("Generating Build Konfig...")
            assertStdoutContains("Printing generated Build Konfig sources...")
        }

        // Incremental re-run, two times
        repeat(2) {
            println("test: run print sources #$it")

            val r2 = runCli(
                projectRoot = r1.projectRoot,
                "task", ":app1:print-generated-sources@build-konfig"
            )

            with(r2) {
                assertStdoutDoesNotContain("Generating Build Konfig...")
                assertStdoutContains("Printing generated Build Konfig sources...")
            }
        }
    }

    @Test
    fun `execution avoidance - disabled when explicitly opted-out`() = runSlowTest {
        val r1 = runCli(
            projectRoot = testProject("extensibility/multiple-local-plugins"),
            "show", "tasks",
            copyToTempDir = true,
        )

        repeat(3) {
            println("test: run print sources #$it")
            val r2 = runCli(
                projectRoot = r1.projectRoot,
                "task", ":app:print-generated-sources@build-konfig",
            )

            with(r2) {
                assertStdoutContains("Generating Build Konfig...")
            }
        }
    }

    @Test
    fun `inferTaskDependency disabled`() = runSlowTest {
        // 1. check fails at first because the baseline is outdated.
        val r1 = runCli(
            projectRoot = testProject("extensibility/multiple-local-plugins"),
            "task", ":app:checkBaseline@build-konfig",
            copyToTempDir = true,
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )
        r1.assertStderrContains("Baseline check failed! Current = {VERSION=1.0, ID=chair-red-dog}, Baseline = {invalid=value}")

        // 1.1 check again - fails because failures are not cached
        runCli(
            projectRoot = r1.projectRoot,
            "task", ":app:checkBaseline@build-konfig",
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        ).assertStderrContains("Baseline check failed!")

        // 2. Update the baseline
        runCli(
            projectRoot = r1.projectRoot,
            "task", ":app:generate-konfig@build-konfig",
        )

        // 3. Check again - doesn't fail
        runCli(
            projectRoot = r1.projectRoot,
            "task", ":app:checkBaseline@build-konfig",
        ).assertStdoutContains("Baseline check successful!")
    }

    @Test
    fun `crash inside a task is correctly reported`() = runSlowTest {
        val r1 = runCli(
            projectRoot = testProject("extensibility/multiple-local-plugins"),
            "task", ":app:crash@hello",
            copyToTempDir = true,
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        with(r1) {
            assertStdoutDoesNotContain("Internal error")
            assertStderrContains(
                """ERROR: Task ':app:crash@hello' failed: java.lang.RuntimeException: Crashing on purpose
                    |        at org.jetbrains.amper.plugins.hello.PluginKt.crash(plugin.kt:28)
                    |Caused by: java.lang.RuntimeException: Nested
                    |        at org.jetbrains.amper.plugins.hello.PluginKt.someFunction(plugin.kt:20)
                    |        at org.jetbrains.amper.plugins.hello.PluginKt.crash(plugin.kt:26)
                    |
                """.trimMargin()
            )
        }
    }

    @Test
    fun `single plugin - no effect when no enabled`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("extensibility/single-local-plugin"),
            "build", "-m", "app2",
            copyToTempDir = true,
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        r.assertStderrContains("Unresolved reference 'Konfig'")

        val app3 = r.projectRoot / "app3" / "module.yaml"
        r.assertStdoutContains("""
            Plugin `build-konfig` is not enabled, but has some explicit configuration.
            ╰─ Values explicitly set at:
               ╰─ $app3:6:5
               ╰─ $app3:9:5
        """.trimIndent())
    }

    @Test
    fun `two plugins - enabled`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("extensibility/multiple-local-plugins"),
            "task", ":app:say@hello",
            copyToTempDir = true,
        )
        val slash = r.projectRoot.fileSystem.separator

        with(r) {
            assertStdoutContains("Hello!")
            assertStdoutContains("tasks${slash}_app_say@hello")
            assertStdoutContains("multiple-local-plugins${slash}app")
        }

        val r2 = runCli(
            projectRoot = r.projectRoot,
            "task", ":build-konfig-plugin:say@hello",
        )

        with(r2) {
            assertStdoutContains("Hello!")
            assertStdoutContains("tasks${slash}_build-konfig-plugin_say@hello")
            assertStdoutContains("multiple-local-plugins${slash}build-konfig-plugin")
        }

        val r3 = runCli(
            projectRoot = r.projectRoot,
            "task", ":hello-plugin:say@hello",
        )

        with(r3) {
            assertStdoutContains("Hello!")
            assertStdoutContains("tasks${slash}_hello-plugin_say@hello")
            assertStdoutContains("multiple-local-plugins${slash}hello-plugin")
        }

        runCli(
            projectRoot = r.projectRoot,
            "run", "-m", "app",
        )

        assertContains(
            charSequence = (r.projectRoot / "app" / "konfig.properties").readText().normalizeLineSeparators(),
            other = """
                ID=chair-red-dog
                VERSION=1.0
            """.trimIndent()
        )
    }

    @Test
    fun `invalid plugins`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility/invalid-plugins"),
            "show", "tasks",
            copyToTempDir = true,
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )

        with(result) {
            assertErrors(
                """
                Plugin id must be unique across the project
                ╰─ There are multiple plugins with the id `hello`:
                   ╰─ ${projectRoot / "plugin-a" / "module.yaml"}:4:7
                   ╰─ ${projectRoot / "plugin-b" / "module.yaml"}:4:7
                   ╰─ ${projectRoot / "hello"}
                """.trimIndent(),
                "${projectRoot / "not-a-plugin" / "module.yaml"}:1:1: Unexpected product type for plugin. Expected jvm/amper-plugin, got jvm/app",
                "${projectRoot / "plugin-empty-id" / "module.yaml"}:5:18: Plugin settings class `com.example.Settings` is not found",
            )
            assertStdoutContains("Processing local plugin schema for [plugin-empty-id, plugin-no-plugin-block, hello]...")
        }
    }

    @Test
    fun `missing plugins`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility/missing-plugins"),
            "show", "tasks",
            copyToTempDir = true,
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )

        with(result) {
            assertErrors(
                "${projectRoot / "project.yaml"}:6:5: Plugin module `existing-but-not-included` is not included in the project `modules` list",
                "${projectRoot / "project.yaml"}:7:5: Plugin module `non-existing` is not found",
            )
            assertStdoutDoesNotContain("Processing local plugin schema for")
        }
    }

    @Test
    fun `incomplete plugins`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility/incomplete-plugins"),
            "show", "tasks",
            copyToTempDir = true,
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )

        with(result) {
            assertWarnings(
                "${projectRoot / "empty-plugin" / "module.yaml" }:2:3: `plugin.yaml` file is missing in the plugins module directory, so it will have no effect when applied",
                "${projectRoot / "no-tasks-plugin" / "plugin.yaml"}: Plugin doesn't register any tasks, so it will have no effect when applied",
            )
            assertErrors(
                "${projectRoot / "invalid-plugin-yaml" / "plugin.yaml"}:2:3: Expected a value",
                "${projectRoot / "invalid-plugin-yaml" / "plugin.yaml"}:3:16: Unexpected custom YAML type tag",
                "${projectRoot / "invalid-plugin-yaml" / "plugin.yaml"}:3:3: Expected a value",
                "${projectRoot / "invalid-plugin-yaml" / "plugin.yaml"}:4:22: Unexpected custom YAML type tag",
                "${projectRoot / "invalid-plugin-yaml" / "plugin.yaml"}:4:3: Expected a value",
                "${projectRoot / "invalid-plugin-yaml" / "plugin.yaml"}:6:13: Unexpected type specified. Expected one of: []",
                "${projectRoot / "invalid-plugin-yaml" / "plugin.yaml"}:8:13: Unexpected type specified. Expected one of: []",
            )
        }
    }

    @Test
    fun `invalid task graph`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility/invalid-task-graph"),
            "show", "tasks",
            copyToTempDir = true,
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )

        val projectRoot = result.projectRoot
        val pluginYamlForInvalidInputs = projectRoot / "tasks-with-invalid-inputs" / "plugin.yaml"
        val pluginYamlForLoops = projectRoot / "tasks-with-loops" / "plugin.yaml"
        val sep = File.separatorChar
        result.assertErrors(
            """
            Output path `${projectRoot / "app" / "foo" / "bar"}` is declared to be produced by multiple tasks: task `withSameOutputA` in module `app` from plugin `tasks-with-invalid-inputs`, task `withSameOutputB` in module `app` from plugin `tasks-with-invalid-inputs`
            ╰─ The output path is specified at:
               ╰─ $pluginYamlForInvalidInputs:22:7
               ╰─ $pluginYamlForInvalidInputs:26:7
            """.trimIndent(),
            """
            Task input/output paths that are children/parents/duplicates of each other are not allowed (reserved for potential future use). The conflicting path is `<project-root-dir>${sep}app`
            ╰─ Conflicting paths are specified at:
               ╰─ $pluginYamlForInvalidInputs:5:7
               ╰─ $pluginYamlForInvalidInputs:6:7
               ╰─ $pluginYamlForInvalidInputs:7:7
            """.trimIndent(),
            """
            Task input/output paths that are children/parents/duplicates of each other are not allowed (reserved for potential future use). The conflicting path is `<project-root-dir>${sep}app`
            ╰─ Conflicting paths are specified at:
               ╰─ $pluginYamlForInvalidInputs:11:7
               ╰─ $pluginYamlForInvalidInputs:12:7
               ╰─ $pluginYamlForInvalidInputs:13:7
            """.trimIndent(),
            """
            Task input/output paths that are children/parents/duplicates of each other are not allowed (reserved for potential future use). The conflicting path is `<project-build-dir>${sep}tasks${sep}_app_withConflictingOutputs@tasks-with-invalid-inputs`
            ╰─ Conflicting paths are specified at:
               ╰─ $pluginYamlForInvalidInputs:17:7
               ╰─ $pluginYamlForInvalidInputs:18:7
            """.trimIndent(),
            """
            Task dependency loop detected:
            1. task `task3` in module `tasks-with-loops` from plugin `tasks-with-loops` (*)
               ╰───> consumes `<project-build-dir>${sep}tasks${sep}_tasks-with-loops_task2@tasks-with-loops${sep}converted.dat` produced by
            2. task `task2` in module `tasks-with-loops` from plugin `tasks-with-loops` <──────────────────────────────────╯
               ╰───> consumes `<project-build-dir>${sep}tasks${sep}_tasks-with-loops_task1@tasks-with-loops${sep}output.bin` produced by
            3. task `task1` in module `tasks-with-loops` from plugin `tasks-with-loops` <───────────────────────────────╯
               ╰───> consumes `<project-root-dir>${sep}tasks-with-loops${sep}source.txt` produced by ───╮
            4. task `task3` in module `tasks-with-loops` from plugin `tasks-with-loops` (*) <─╯
            ╰─ Related configuration elements that may have caused the loop:
               ╰─ $pluginYamlForLoops:10:7
               ╰─ $pluginYamlForLoops:6:7
               ╰─ $pluginYamlForLoops:14:7
            """.trimIndent(),
            """
            Task dependency loop detected:
            1. task `taskThatGeneratesSources` in module `tasks-with-loops` from plugin `tasks-with-loops` (*)
               ╰───> depends on the compilation of its source code
            2. compilation of module `tasks-with-loops` <────────╯
               ╰───> needs sources from ─────────────────────────╮
            3. source generation for module `tasks-with-loops` <─╯
               ╰───> includes the directory `<project-build-dir>${sep}tasks${sep}_tasks-with-loops_taskThatGeneratesSources@tasks-with-loops` generated by
            4. task `taskThatGeneratesSources` in module `tasks-with-loops` from plugin `tasks-with-loops` (*) <───────────────────────────────╯
            ╰─ Related configuration elements that may have caused the loop:
               ╰─ $pluginYamlForLoops:20:9
            """.trimIndent(),
            """
            Task dependency loop detected:
            1. task `consumesResources` in module `app` from plugin `tasks-with-invalid-inputs` (*)
               ╰───> requests resources including those generated in
            2. resource generation for module `app` <──────────────╯
               ╰───> includes the directory `<project-build-dir>${sep}tasks${sep}_app_generatesResources@tasks-with-invalid-inputs` generated by
            3. task `generatesResources` in module `app` from plugin `tasks-with-invalid-inputs` <───────────────────────────────────╯
               ╰───> consumes `<project-build-dir>${sep}tasks${sep}_app_consumesResources@tasks-with-invalid-inputs` produced by
            4. task `consumesResources` in module `app` from plugin `tasks-with-invalid-inputs` (*) <────────────────╯
            ╰─ Related configuration elements that may have caused the loop:
               ╰─ $pluginYamlForInvalidInputs:41:9
               ╰─ $pluginYamlForInvalidInputs:34:9
               ╰─ $pluginYamlForInvalidInputs:42:7
            """.trimIndent()
        )
    }

    @Test
    fun `invalid references`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility/invalid-references"),
            "show", "tasks",
            copyToTempDir = true,
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )
        with(result) {
            val pluginYaml = projectRoot / "plugin1" / "plugin.yaml"
            assertErrors(
                "${pluginYaml}:19:5: Cannot assign to property `taskOutputDir` - it is a built-in property available for reference only",
                "${pluginYaml}:18:11: Expected `Dependency.Maven ( maven-coordinates | maven-coordinates: {..} )`, but got `sequence []`",
                "${pluginYaml}:21:1: Cannot assign to property `module` - it is a built-in property available for reference only",
                "${pluginYaml}:17:11: Referencing `markOutputsAs` is not allowed",
                "${pluginYaml}:14:11: Maven coordinates should not contain slashes",
                "${pluginYaml}:15:11: Maven coordinates one-part should contain at least two parts separated by ':', but got 1",
                "${pluginYaml}:11:7: Referencing `module` is not allowed",
                "${pluginYaml}:12:7: The value of type `mapping {string : Element}` cannot be assigned to the type `Nested`",
                "${pluginYaml}:6:7: The value of type `string` cannot be assigned to the type `boolean`",
                "${pluginYaml}:9:7: The value of type `Settings` cannot be assigned to the type `path`",
                "${pluginYaml}:7:7: The value of type `boolean` cannot be used in string interpolation",
                "${pluginYaml}:4:5: No value for required property 'int'.",
                "${pluginYaml}:4:5: No value for required property 'classpath.dependencies'.",
            )
            assertWarnings(
                "${pluginYaml}:16:11: Maven classifiers are currently not supported",
            )
        }
    }

    private fun AmperCliResult.assertErrors(
        vararg expectedErrors: String,
    ) {
        val actual = CliErrorLikeRegex.findAll(stderr).map {
            it.groups["error"]!!.value.trim() + '\n'
        }.toSortedSet()

        assertEquals(
            expected = expectedErrors.mapTo(sortedSetOf()) { it + '\n' },
            actual = actual,
        )
    }

    private fun AmperCliResult.assertWarnings(
        vararg expectedWarnings: String,
    ) {
        val actual = CliWarningLikeRegex.findAll(stdout).map {
            it.groups["warning"]!!.value.trim() + '\n'
        }.toSortedSet()

        assertEquals(
            expected = expectedWarnings.mapTo(sortedSetOf()) { it + '\n' },
            actual = actual,
        )
    }

    private fun AmperCliResult.assertCustomTaskStdoutContains(
        taskName: String,
        output: String,
    ) {
        val taskOutputLineRegex = """^.{9}\s+INFO\s+${Regex.escape(taskName)}\s+(.*)$""".toRegex(RegexOption.MULTILINE)
        assertContains(
            charSequence = stdoutClean.replace(taskOutputLineRegex) { it.groupValues[1] },
            other = output,
        )
    }
}

private val CliErrorLikeRegex = """^\d{2}:\d{2}\.\d{3} ERROR\s+(?<error>.*?)(?=^\d{2}:\d{2}\.\d{3}|ERROR:|\z)"""
    .toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
private val CliWarningLikeRegex = """^\d{2}:\d{2}\.\d{3} WARN\s+(?<warning>.*?)(?=^\d{2}:\d{2}\.\d{3}|ERROR:|\z)"""
    .toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))