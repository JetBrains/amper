/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class ProjectFileTest : AmperCliTestBase() {

    @Test
    fun modules() = runSlowTest {
        val r = runCli(testProject("simple-multiplatform-cli"), "show", "modules")

        assertModulesList(r, listOf(
            "jvm-cli",
            "linux-cli",
            "macos-cli",
            "shared",
            "utils",
            "windows-cli",
        ))
    }

    @Test
    fun `single-module project under an unrelated project`() = runSlowTest {
        val resultNested = runCli(testProject("nested-project-root") / "nested-project", "show", "modules")
        assertModulesList(resultNested, listOf("nested-project"))

        val resultRoot = runCli(testProject("nested-project-root"), "show", "modules")
        assertModulesList(resultRoot, listOf("included-module"))
    }

    @Test
    fun `empty project file and no module file`() = runSlowTest {
        val projectRoot = testProject("project-root-no-modules")
        val result = runCli(projectRoot, "build")
        result.assertStdoutContains("${projectRoot.resolve("project.yaml")}: Project has no modules: no root module file and no modules listed in the project file")
    }

    @Test
    fun `project including a deep module`() = runSlowTest {
        val result = runCli(testProject("project-root-deep-inclusion"), "show", "modules")
        assertModulesList(result, listOf("deep-module"))
    }

    @Test
    fun `project with denormalized globs`() = runSlowTest {
        val result = runCli(testProject("project-root-denormalized-globs"), "show", "modules")
        assertModulesList(result, listOf("deep", "deep2", "sub1", "sub2", "sub3", "sub4"))
    }

    @Test
    fun `project with both top-level and nested modules`() = runSlowTest {
        val result = runCli(testProject("top-level-and-nested-modules"), "show", "modules")
        assertModulesList(result, listOf("deep-module", "top-level-and-nested-modules"))
    }

    @Test
    fun `project file with path errors`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("project-file-with-errors"),
            "show", "tasks",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        assertContains(r.stdout, "project.yaml:7:5: Glob pattern \"glob-with-no-matches-at-all/*\" doesn't match any Amper module directory under the project root")
        assertContains(r.stdout, "project.yaml:8:5: Glob pattern \"not-a-modul?\" doesn't match any Amper module directory under the project root")
        assertContains(r.stdout, "project.yaml:14:5: The root module is included by default")

        assertContains(r.stderr, "project.yaml:3:5: Unresolved path \"./does-not-exist\"")
        assertContains(r.stderr, "project.yaml:4:5: Unresolved path \"./does/not/exist\"")
        assertContains(r.stderr, "project.yaml:5:5: \"not-a-dir\" is not a directory")
        assertContains(r.stderr, "project.yaml:6:5: Directory \"not-a-module\" doesn't contain an Amper module file")
        assertContains(r.stderr, "project.yaml:9:5: Invalid glob pattern \"broken[syntax\": Missing '] near index 12\n" +
                "broken[syntax\n" +
                "            ^")
        assertContains(r.stderr, "project.yaml:10:5: Invalid glob pattern \"broken[z-a]syntax\": Invalid range near index 7\n" +
                "broken[z-a]syntax\n" +
                "       ^")
        assertContains(r.stderr, "project.yaml:11:5: Invalid glob pattern \"broken[syntax/with/**\": Explicit 'name separator' in class near index 13\n" +
                "broken[syntax/with/**\n" +
                "             ^")
        assertContains(r.stderr, "project.yaml:12:5: Unsupported \"**\" in module glob pattern \"forbidden/**/recursive\". Use multiple single-level \"*\" segments instead to specify the depth exactly.")
        assertContains(r.stderr, "project.yaml:13:5: Directory \"../jvm-default-compiler-settings\" is not under the project root")
        assertContains(r.stderr, "ERROR: aborting because there were errors in the Amper project file, please see above")
    }

    @Test
    fun `invalid project root`() = runSlowTest {
        val explicitRoot = testProject("invalid-project-root")
        val r = runCli(
            projectRoot = explicitRoot,
            "--root",
            explicitRoot.pathString,
            "show", "tasks",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        val expected = "ERROR: The given path '$explicitRoot' is not a valid Amper project root " +
                "directory. Make sure you have a project file or a module file at the root of your Amper project."
        assertEquals(expected, r.stderr.trim())
    }

    private fun assertModulesList(modulesCommandResult: AmperCliResult, expectedModules: List<String>) {
        // TODO should we have a machine-readable output format without banner/logs location messages?
        // Sometimes there are output lines about waiting for other processes or downloading the distribution or JRE.
        // There are also the output banner and the "logs are there" lines.
        // There may be empty lines in this first part, and there is always an empty line after the logs location line.
        val modules = modulesCommandResult.stdout.lines()
            .dropLastWhile { it.isEmpty() }
            .takeLastWhile { it.isNotEmpty() }
        return assertEquals(expectedModules, modules)
    }
}