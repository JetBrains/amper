/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jic

import com.intellij.tools.build.bazel.jvmIncBuilder.ExitCode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.amper.telemetry.ChildProcessTelemetry.withChildProcessTelemetrySpan
import kotlin.system.exitProcess

@OptIn(ExperimentalSerializationApi::class)
suspend fun main() {
    val exitCode = withChildProcessTelemetrySpan("jic") {
        val request = Json.decodeFromStream<JicCompilationRequest>(System.`in`)
        JicJavaBuilder(
            request.amperModuleName,
            request.amperModuleDir,
            request.javaSourceFiles,
            request.jicJavacArgs,
            request.javaCompilerOutputRoot,
            request.jicDataDir,
            request.classpath,
        ).build()
    }

    when (exitCode) {
        ExitCode.OK -> exitProcess(0)
        ExitCode.CANCEL -> exitProcess(130)
        ExitCode.ERROR -> exitProcess(1)
    }
}
