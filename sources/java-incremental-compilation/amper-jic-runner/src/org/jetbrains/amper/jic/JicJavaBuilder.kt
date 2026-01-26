/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jic

import com.intellij.tools.build.bazel.jvmIncBuilder.BazelIncBuilder
import com.intellij.tools.build.bazel.jvmIncBuilder.CLFlags
import com.intellij.tools.build.bazel.jvmIncBuilder.ExitCode
import org.jetbrains.amper.jps.JicOutputAutoFlushWorkaround.serializeJpsCompilerOutput
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class JicJavaBuilder(
    val amperModuleName: String,
    val amperModuleDir: Path,
    val javaSourceFiles: List<Path>,
    val javacArgs: List<String>,
    val compilerOutputRoot: Path,
    val jicDataDir: Path,
    val classpath: List<Path>,
) {

    fun build(): ExitCode {
        // TODO reuse existing digests from the incremental.state database
        val inputDigests = javaSourceFiles.map { path ->
            Files.getLastModifiedTime(path).toMillis().toString().toByteArray()
        }

        val clFlags = buildMap {
            put(CLFlags.TARGET_LABEL, listOf(amperModuleName))
            put(CLFlags.NON_INCREMENTAL, listOf("false"))
            put(CLFlags.CP, classpath.map { amperModuleDir.absolutePathString() })
            put(CLFlags.OTHER, javacArgs)
        }

        val buildContext = AmperJicBuildContext(
            amperModuleName = amperModuleName,
            amperModuleDir = amperModuleDir,
            javaSourceFiles = javaSourceFiles,
            inputDigests = inputDigests,
            compilerOutputRoot = compilerOutputRoot,
            clFlags = clFlags,
            jicDataDir = jicDataDir,
            classpath = classpath,
            javacArgs = javacArgs,
            printToStdout = {
                System.out.println(serializeJpsCompilerOutput(it))
            },
            printToStderr = {
                System.err.println(serializeJpsCompilerOutput(it))
            }
        )

        return BazelIncBuilder().build(buildContext)
    }
}