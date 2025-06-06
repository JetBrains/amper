/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.telemetry.setProcessResultAttributes
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.downloadAndExtractKotlinNative
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.ShellQuoting
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

suspend fun downloadNativeCompiler(
    kotlinVersion: String,
    userCacheRoot: AmperUserCacheRoot
): KotlinNativeCompiler {
    val kotlinNativeHome = downloadAndExtractKotlinNative(kotlinVersion, userCacheRoot)
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
        private const val KONAN_DATA_DIR = "KONAN_DATA_DIR"
        private val logger = LoggerFactory.getLogger(KotlinNativeCompiler::class.java)
    }

    val commonizedPath by lazy {
        val explicitKonanDataDir = System.getenv(KONAN_DATA_DIR)
        val konanDataDir = if (explicitKonanDataDir != null) Path(explicitKonanDataDir) else kotlinNativeHome
        val encodedVersion = URLEncoder.encode(kotlinVersion, Charsets.UTF_8.name())
        konanDataDir / "klib" / "commonized" / encodedVersion
    }

    suspend fun compile(
        args: List<String>,
        tempRoot: AmperProjectTempRoot,
        module: AmperModule,
    ) {
        spanBuilder("konanc")
            .setAmperModule(module)
            .setListAttribute("args", args)
            .setAttribute("version", kotlinVersion)
            .use { span ->
                logger.debug("konanc ${ShellQuoting.quoteArgumentsPosixShellWay(args)}")

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
