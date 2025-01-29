/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("SameParameterValue")

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AndroidHomeRoot
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.CliEnvironmentInitializer
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.android.AndroidTools
import org.junit.jupiter.api.extension.RegisterExtension
import org.tinylog.Level
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.assertTrue

@Suppress("MemberVisibilityCanBePrivate")
abstract class AmperIntegrationTestBase {
    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    protected val tempRoot: Path by lazy {
        // Always run tests in a directory with space, tests quoting in a lot of places
        // Since TempDirExtension generates temp directory under TestUtil.tempDir
        // it should already contain a space in the part
        // assert it anyway
        val path = tempDirExtension.path
        check(path.pathString.contains(" ")) {
            "Temp path should contain a space: ${path.pathString}"
        }
        check(path.isDirectory()) {
            "Temp path is not a directory: $path"
        }
        path
    }

    private val userCacheRoot: AmperUserCacheRoot = AmperUserCacheRoot(Dirs.userCacheRoot)

    init {
        CliEnvironmentInitializer.setupCoroutinesInstrumentation()
    }

    protected suspend fun TestCollector.setupTestProject(
        testProjectPath: Path,
        copyToTemp: Boolean,
        programArgs: List<String> = emptyList(),
        useEmptyAndroidHome: Boolean = false,
    ): CliContext {
        require(testProjectPath.exists()) { "Test project is missing at $testProjectPath" }

        val projectRoot = if (copyToTemp) testProjectPath.copyToTempRoot() else testProjectPath
        val buildDir = tempRoot.resolve("build").also { it.createDirectories() }
        val androidHomeRoot = if (useEmptyAndroidHome) {
            // in temp dir so we get a fresh one in every build on the CI
            AndroidHomeRoot((Dirs.tempDir / "empty-android-sdk").also { it.createDirectories() })
        } else {
            AndroidHomeRoot(AndroidTools.getOrInstallForTests().androidSdkHome)
        }

        return CliContext.create(
            explicitProjectRoot = projectRoot,
            userCacheRoot = userCacheRoot,
            buildOutputRoot = AmperBuildOutputRoot(buildDir),
            commonRunSettings = CommonRunSettings(programArgs = programArgs),
            currentTopLevelCommand = "integration-test-base",
            backgroundScope = backgroundScope,
            androidHomeRoot = androidHomeRoot,
            terminal = terminal,
        )
    }

    private fun Path.copyToTempRoot(): Path = (tempRoot / UUID.randomUUID().toString() / fileName.name).also { dir ->
        dir.createDirectories()
        copyToRecursively(target = dir, followLinks = true, overwrite = false)
    }

    protected fun TestCollector.assertInfoLogStartsWith(msgPrefix: String) = assertLogStartsWith(msgPrefix, level = Level.INFO)

    protected fun TestCollector.assertLogStartsWith(msgPrefix: String, level: Level) {
        assertTrue("Log message with level=$level and starting with '$msgPrefix' was not found") {
            logEntries.any { it.level == level && it.message.startsWith(msgPrefix) }
        }
    }

    protected fun TestCollector.assertLogContains(text: String, level: Level) {
        assertTrue("Log message with level=$level and containing '$text' was not found") {
            logEntries.any { it.level == level && text in it.message }
        }
    }

    protected fun TestCollector.assertStdoutContains(text: String) {
        assertTrue("No line in stdout contains the text '$text':\n" + terminalRecorder.stdout().trim()) {
            terminalRecorder.stdout().lineSequence().any { text in it }
        }
    }

    protected fun TestCollector.assertStdoutTextContains(text: String) {
        assertTrue("No line in stdout contains the text '$text':\n" + terminalRecorder.stdout().trim()) {
            text in terminalRecorder.stdout()
        }
    }

    protected fun TestCollector.assertStdoutDoesNotContain(text: String) {
        assertTrue("No line in stdout should contain the text '$text':\n" + terminalRecorder.stdout().trim()) {
            terminalRecorder.stdout().lineSequence().none { text in it }
        }
    }

    fun AmperBackend.assertHasTasks(tasks: Iterable<String>, module: String? = null) {
        val taskNames = tasks().map { it.taskName.name }.toSortedSet()
        tasks.forEach { task ->
            val expectedTaskName = ":${module ?: context.projectRoot.path.name}:$task"
            assertTrue(
                "Task named '$expectedTaskName' should be present, but found only:\n" +
                        taskNames.joinToString("\n")
            ) {
                taskNames.contains(expectedTaskName)
            }
        }
    }
}

