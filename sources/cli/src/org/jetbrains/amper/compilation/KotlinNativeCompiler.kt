/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.Jdk
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.downloadAndExtractKotlinNative
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.processes.setProcessResultAttributes
import org.jetbrains.amper.util.ShellQuoting
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.file.Path
import kotlin.io.path.div

suspend fun downloadNativeCompiler(
    kotlinVersion: String,
    userCacheRoot: AmperUserCacheRoot
): KotlinNativeCompiler {
    val kotlinNativeHome = downloadAndExtractKotlinNative(kotlinVersion)
        ?: error("kotlin native compiler is not available for the current platform")

    // TODO this the is JDK to run konanc, what are the requirements?
    val jdk = JdkDownloader.getJdk(userCacheRoot)

    return KotlinNativeCompiler(kotlinNativeHome, kotlinVersion, jdk)
}

class KotlinNativeCompiler(
    val kotlinNativeHome: Path,
    private val kotlinVersion: String,
    private val jdk: Jdk,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(KotlinNativeCompiler::class.java)
    }

    val commonizedPath by lazy {
        val encodedVersion = URLEncoder.encode(kotlinVersion, Charsets.UTF_8.name())
        kotlinNativeHome / "klib" / "commonized" / encodedVersion
    }

    suspend fun compile(
        args: List<String>,
        tempRoot: AmperProjectTempRoot,
        module: PotatoModule,
    ) {
        spanBuilder("konanc")
            .setAmperModule(module)
            .setListAttribute("args", args)
            .setAttribute("version", kotlinVersion)
            .useWithScope { span ->
                logger.info("Calling konanc ${ShellQuoting.quoteArgumentsPosixShellWay(args)}")

                withKotlinCompilerArgFile(args, tempRoot) { argFile ->
                    val konanLib = kotlinNativeHome / "konan" / "lib"

                    // We call konanc via java because the konanc command line doesn't support spaces in paths:
                    // https://youtrack.jetbrains.com/issue/KT-66952
                    // TODO in the future we'll switch to kotlin tooling api and remove this raw java exec anyway
                    val result = jdk.runJava(
                        workingDir = kotlinNativeHome,
                        mainClass = "org.jetbrains.kotlin.cli.utilities.MainKt",
                        classpath = listOf(
                            konanLib / "kotlin-native-compiler-embeddable.jar",
                            konanLib / "trove4j.jar",
                        ),
                        programArgs = listOf("konanc", "@${argFile}"),
                        // JVM args partially copied from <kotlinNativeHome>/bin/run_konan
                        jvmArgs = listOf(
                            "-ea",
                            "-XX:TieredStopAtLevel=1",
                            "-Dfile.encoding=UTF-8",
                            "-Dkonan.home=$kotlinNativeHome",
                        ),
                        outputListener = LoggingProcessOutputListener(logger),
                        tempRoot = tempRoot,
                    )

                    // TODO this is redundant with the java span of the external process run. Ideally, we
                    //  should extract higher-level information from the raw output and use that in this span.
                    span.setProcessResultAttributes(result)

                    if (result.exitCode != 0) {
                        val errors = result.stderr
                            .lines()
                            .filter { it.startsWith("error: ") || it.startsWith("exception: ") }
                            .joinToString("\n")
                        val errorsPart = if (errors.isNotEmpty()) ":\n\n$errors" else ""
                        userReadableError("Kotlin native compilation failed$errorsPart")
                    }
                }
            }
    }
}
