/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStderrDoesNotContain
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

@Execution(ExecutionMode.CONCURRENT)
class AnnotationProcessingTest: AmperCliTestBase() {

    @Test
    fun `lombok project compiles and runs`() = runSlowTest {
        val projectRoot = testProject("lombok")
        val result = runCli(projectRoot, "run")

        result.assertStdoutContains("John")
        result.assertStdoutContains("25")
    }

    @Test
    fun `mapstruct project compiles and runs`() = runSlowTest {
        val projectRoot = testProject("mapstruct")
        val buildResult = runCli(projectRoot, "build")

        val generatedJavaFile = buildResult.buildOutputRoot / "generated" / "mapstruct" / "main" / "src" / "apt" / "java" / "UserMapperImpl.java"
        assertTrue(generatedJavaFile.exists(), "Generated UserMapperImpl.java source file not found")

        val result = runCli(projectRoot, "run")

        result.assertStdoutContains("UserDto{name='John', email='john@email'}")
    }


    @Test
    fun `ap-from-sources project compiles and runs`() = runSlowTest {
        val projectRoot = testProject("ap-from-sources")

        val result = runCli(projectRoot, "run", assertEmptyStdErr = false)

        result.assertStderrContains("Hello, Annotation Processor! from Main")
    }

    @Test
    fun `ap-with-params compiles and runs`() = runSlowTest {
        val projectRoot = testProject("ap-with-params")

        val result = runCli(projectRoot, "run", assertEmptyStdErr = false)

        result.assertStdoutContains("Do something")
        result.assertStderrContains("[com.google.auto.service.AutoService]")
    }

    @Test
    fun `re-compilation happens after processorOptions changes`() = runSlowTest {
        // we're going to modify project files => copy the project instead of running in place
        val projectRoot = copyProjectToTempDir(testProject("ap-with-params"))

        /*
          This test checks that the module is recompiled after a change in processorOptions (AMPER-4581).

          The '-Adebug' parameter is active and produces some additional debug output in stderr,
          only when the annotation processor is being run.

          Suppose that we:
          - keep the '-Adebug' parameter initially,
          - run the app,
          - check that debug information was printed to stderr,
          - remove the '-Adebug' parameter,
          - run the app again
          - do not see the debug output.
          The end result might indicate either:
          - that the module was recompiled, and the processor option change took effect,
          - or it can also indicate that the module was not recompiled, and the annotation processor
          was not run at all (and thus did not produce the debug output).
          So, this test scenario would not test what we want to test (recompilation).

          To overcome this, we invert the '-Adebug' parameter value:
          keep it off initially and introduce it after the first run.
        */

        val moduleYaml = (projectRoot / "module.yaml")
        moduleYaml.writeText(moduleYaml.readText().replace("debug: \"\"", "unknown: true"))

        val result = runCli(projectRoot, "run", assertEmptyStdErr = false)
        result.assertStdoutContains("Do something")
        result.assertStderrDoesNotContain("[com.google.auto.service.AutoService]")

        moduleYaml.writeText(moduleYaml.readText().replace("unknown: true", "debug: \"\""))

        val result2 = runCli(projectRoot, "run", assertEmptyStdErr = false)
        result2.assertStdoutContains("Do something")
        result2.assertStderrContains("[com.google.auto.service.AutoService]")

        // and let's run once again to verify that if we change nothing, then annotation processors are not re-run
        val result3 = runCli(projectRoot, "run", assertEmptyStdErr = false)
        result3.assertStdoutContains("Do something")
        result3.assertStderrDoesNotContain("[com.google.auto.service.AutoService]")
    }

    @Test
    fun `re-compilation is triggered if annotation processor output is removed`() = runSlowTest {
        val projectRoot = testProject("mapstruct")
        val buildResult = runCli(projectRoot, "build")

        val generatedFileRelativePath = Path("generated") / "mapstruct" / "main" / "src" / "apt" / "java" / "UserMapperImpl.java"
        val generatedJavaFile = buildResult.buildOutputRoot / generatedFileRelativePath
        assertTrue(generatedJavaFile.exists(), "Generated UserMapperImpl.java source file not found")

        generatedJavaFile.deleteExisting()

        val buildResult2 = runCli(projectRoot, "build")
        val generatedJavaFile2 = buildResult2.buildOutputRoot / generatedFileRelativePath
        assertTrue(generatedJavaFile2.exists(), "Generated UserMapperImpl.java source file was not regenerated")
    }

    @Test
    fun `lombok-kotlin project with interop works when lombok is enabled`() = runSlowTest {
        val projectRoot = testProject("lombok-kotlin")

        val result = runCli(projectRoot, "run")

        result.assertStdoutContains("John")
        result.assertStdoutContains("32")
    }

}
