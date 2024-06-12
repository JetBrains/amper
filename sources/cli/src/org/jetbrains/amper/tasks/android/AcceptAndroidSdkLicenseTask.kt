/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.concurrency.withDoubleLock
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.TaskName
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.writeText

class AcceptAndroidSdkLicenseTask(
    private val androidSdkPath: Path,
    override val taskName: TaskName
): Task {
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        val licensesPath = (androidSdkPath / "licenses").also { it.createDirectories() }
        val androidSdkLicensePath = licensesPath / "android-sdk-license"
        withDoubleLock(androidSdkLicensePath.hashCode(), androidSdkPath / "licenses.lock") {
            if (!androidSdkLicensePath.exists()) {
                androidSdkLicensePath.writeText("""
                8933bad161af4178b1185d1a37fbf41ea5269c55
                d56f5187479451eabf01fb78af6dfcb131a6481e
                24333f8a63b6825ea9c5514f83c2829b004d1fee
            """.trimIndent())
            }
        }

        return TaskResult(dependenciesResult, listOf(androidSdkLicensePath))
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val licenses: List<Path>,
    ) : org.jetbrains.amper.tasks.TaskResult
}
