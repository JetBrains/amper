/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.util.headlessEmulatorModePropertyName
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AmperAndroidProjectsTest : IntegrationTestBase() {
    private val androidProjectsPath: Path = TestUtil.amperCheckoutRoot.resolve("android-projects")
    private val userCacheRoot: AmperUserCacheRoot = AmperUserCacheRoot(TestUtil.userCacheRoot)

    private fun setupAndroidTestProject(testProjectName: String): ProjectContext {
        val projectContext = setupTestProject(androidProjectsPath.resolve(testProjectName), copyToTemp = true)
        projectContext.projectRoot.path.deleteGradleFiles()
        return projectContext
    }

    @Test
    fun `simple-app`() = runTest(timeout = 15.minutes) {
        val projectContext = setupAndroidTestProject("simple-app")
        System.setProperty(headlessEmulatorModePropertyName, "true")
        val job = async { AmperBackend(projectContext).runTask(TaskName.fromHierarchy(listOf("simple-app", "logcat"))) }
        waitForSubstringInLineOrFailAfterTimeout("My Application")
        job.cancelAndJoin()
    }

    @OptIn(FlowPreview::class)
    private suspend fun waitForSubstringInLineOrFailAfterTimeout(substring: String, timeout: Duration = 5.minutes) {
        try {
            stdoutCollector.lines.takeWhile { !it.contains(substring) }.count()
        } catch (e: TimeoutCancellationException) {
            fail("'$substring' was not found in $timeout")
        }
    }
}