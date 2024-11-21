/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package iosUtils

import TestBase
import org.jetbrains.amper.processes.runProcess
import org.jetbrains.amper.test.SimplePrintOutputListener
import org.jetbrains.amper.test.TestUtil
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * Utility class for assembling iOS modules in Gradle-based projects.
 */
object GradleAssembleHelper {

    /**
     * Configures and builds the iOS app in the specified project directory using Gradle.
     * Runs only the iOS assembly if the project is multiplatform; otherwise, assembles full target app.
     */
    suspend fun buildiOSAppGradle(
        projectDir: Path,
        runWithPluginClasspath: Boolean,
        multiplatform: Boolean
    ) {
        TestBase().putAmperToGradleFile(projectDir, runWithPluginClasspath)
        if (multiplatform) {
            assembleiOSOnly(projectDir)
        } else {
            TestBase().assembleTargetApp(projectDir)
        }
    }

    /**
     * Builds only the iOS module. This function is
     * intended for use when the project is configured as a multiplatform project
     */
    private suspend fun assembleiOSOnly(projectDir: Path) {
        val gradlewFileName = if (TestBase.isWindows) "gradlew.bat" else "gradlew"
        val gradlewPath = TestUtil.amperCheckoutRoot.resolve(gradlewFileName)

        check(gradlewPath.exists()) {
            "gradlew file does not exist in ${gradlewPath.absolutePathString()}"
        }
        val task = "ios-app:assemble"

        try {
            val exitCode = runProcess(
                workingDir = projectDir,
                command = listOf(gradlewPath.absolutePathString(), task),
                outputListener = SimplePrintOutputListener
            )

            check(exitCode == 0) {
                "Execution of '$task' failed in ${projectDir.name}"
            }
        } catch (e: IOException) {
            throw RuntimeException("IOException during '$task' in ${projectDir.name}", e)
        }
    }
}
