/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import kotlinx.serialization.json.Json
import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jic.JicCompilationRequest
import org.jetbrains.amper.jps.JicOutputAutoFlushWorkaround.deserializeJpsCompilerOutput
import org.jetbrains.amper.processes.ArgsMode
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.slf4j.Logger
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

internal suspend fun compileJavaWithJic(
    processRunner: ProcessRunner,
    jdk: Jdk,
    module: AmperModule,
    isTest: Boolean,
    javaSourceFiles: List<Path>,
    jicJavacArgs: List<String>,
    javaCompilerOutputRoot: Path,
    jicDataDir: Path,
    classpath: List<Path>,
    logger: Logger,
): Boolean {
    val distributionRoot = Path(checkNotNull(System.getProperty("amper.dist.path")) {
        "Missing `amper.dist.path` system property. Ensure your wrapper script integrity."
    })
    val toolClasspath = distributionRoot.resolve("amper-jic-runner").listDirectoryEntries("*.jar")

    val request = JicCompilationRequest(
        amperModuleName = module.userReadableName,
        amperModuleDir = module.source.moduleDir,
        isTest = isTest,
        javaSourceFiles = javaSourceFiles,
        jicJavacArgs = jicJavacArgs,
        javaCompilerOutputRoot = javaCompilerOutputRoot,
        jicDataDir = jicDataDir,
        classpath = classpath,
    )

    val isDebugAgentPresent = ManagementFactory.getRuntimeMXBean().inputArguments.any { it.startsWith("-agentlib:jdwp") }
    val jvmArgs = buildList {
        // When debugging Amper, we run the external JIC process with jdwp to be able to attach a debugger to it as well.
        // The JIC process will wait for a debugger to attach on a dynamic port. You can click "Attach debugger"
        // in the IDEA console to automatically launch and attach a remote debugger.
        if (isDebugAgentPresent) {
            add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y")
        }
    }

    val result = processRunner.runJava(
        jdk = jdk,
        workingDir = Path("."),
        mainClass = "org.jetbrains.amper.jic.JicMainKt",
        programArgs = emptyList(),
        argsMode = ArgsMode.CommandLine,
        jvmArgs = jvmArgs,
        classpath = toolClasspath,
        outputListener = object: ProcessOutputListener {
            override fun onStdoutLine(line: String, pid: Long) {
                logger.info(deserializeJpsCompilerOutput(line))
            }

            override fun onStderrLine(line: String, pid: Long) {
                logger.error(deserializeJpsCompilerOutput(line))
            }},
        // Input request is passed via STDIN
        input = ProcessInput.Text(Json.encodeToString(request))
    )
    return result.exitCode == 0
}
