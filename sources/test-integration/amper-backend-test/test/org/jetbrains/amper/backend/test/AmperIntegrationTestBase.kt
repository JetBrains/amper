/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("SameParameterValue")

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AndroidHomeRoot
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.tasks.AllRunSettings
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.android.AndroidTools
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString

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
            explicitBuildOutputRoot = buildDir,
            runSettings = AllRunSettings(programArgs = programArgs),
            commandName = "integration-test-base",
            androidHomeRoot = androidHomeRoot,
            terminal = terminal,
        )
    }

    private fun Path.copyToTempRoot(): Path = (tempRoot / UUID.randomUUID().toString() / fileName.name).also { dir ->
        dir.createDirectories()
        copyToRecursively(target = dir, followLinks = true, overwrite = false)
    }
}
