/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path
import kotlin.io.path.div

class CheckAndroidSdkLicenseTask(
    private val androidSdkPath: Path,
    private val userCacheRoot: AmperUserCacheRoot,
    override val taskName: TaskName
): Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val unacceptedLicenses = SdkInstallManager(userCacheRoot, androidSdkPath).findUnacceptedSdkLicenses()
        if (unacceptedLicenses.isNotEmpty()) {
            val licensesListText = unacceptedLicenses.joinToString("\n") { " - ${it.id}" }
            val licensesCommand = "${androidSdkPath / "cmdline-tools" / "latest" / "bin" / "sdkmanager"} --licenses"
            userReadableError("Some licenses have not been accepted for Android SDK:\n" +
                    "$licensesListText\n" +
                    "Run \"$licensesCommand\" to review and accept them")
        }
        return Result()
    }

    class Result() : TaskResult
}
