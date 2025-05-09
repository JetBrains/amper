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
import kotlin.test.Test
import kotlin.test.assertTrue

@Execution(ExecutionMode.CONCURRENT)
class AnnotationProcessingTest: AmperCliTestBase() {

    @Test
    fun `lombok project compiles and runs`() = runSlowTest {
        val projectRoot = testProject("lombok")
        val buildResult = runCli(projectRoot, "build")

        val classFilePath = buildResult.buildOutputRoot / "tasks" / "_lombok_compileJvm" / "Person.class"
        assertTrue(classFilePath.exists(), "Generated Person class file not found")

        val result = runCli(projectRoot, "run")

        result.assertStdoutContains("John")
        result.assertStdoutContains("25")
    }

    @Test
    fun `mapstruct project compiles and runs`() = runSlowTest {
        val projectRoot = testProject("mapstruct")
        val buildResult = runCli(projectRoot, "build")

        val generatedJavaFile = buildResult.buildOutputRoot / "tasks" / "_mapstruct_compileJvm" / "generated" / "mapstruct" / "common" / "src" / "apt" / "java" / "UserMapperImpl.java"
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
    fun `lombok-kotlin project with interop works when lombok is enabled`() = runSlowTest {
        val projectRoot = testProject("lombok-kotlin")

        val result = runCli(projectRoot, "run")

        result.assertStdoutContains("John")
        result.assertStdoutContains("32")
    }

}
