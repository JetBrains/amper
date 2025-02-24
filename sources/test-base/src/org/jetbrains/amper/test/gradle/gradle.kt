/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.gradle

import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.processes.out
import org.junit.jupiter.api.TestReporter
import java.net.URI
import java.nio.file.Path

/**
 * Runs Gradle in the given [projectDir] with the given [args] using the Gradle Tooling API.
 *
 * The output is reported using the given [TestReporter], using [cmdName] in the prefix.
 */
fun runGradle(
    projectDir: Path,
    args: List<String>,
    cmdName: String = "gradle",
    testReporter: TestReporter,
    gradleVersion: String? = null,
    additionalEnv: Map<String, String> = emptyMap(),
) {
    GradleConnector.newConnector()
        .useGradleUserHomeDir(Dirs.sharedGradleHome.toFile())
        .forProjectDirectory(projectDir.toFile())
        .apply {
            if (gradleVersion != null) {
                // we use this instead of useGradleVersion() so that our tests benefit from the cache redirector and avoid timeouts
                useDistribution(URI("https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"))
            }
        }
        .connect()
        .use { connector ->
            connector.newBuild()
                .setJvmArguments("-Dorg.gradle.daemon=false")
                .withArguments(*args.toTypedArray())
                .setStandardOutput(testReporter.out(linePrefix = "[$cmdName out] "))
                .setStandardError(testReporter.out(linePrefix = "[$cmdName err] "))
                .setEnvironmentVariables(System.getenv() + additionalEnv)
                .run()
        }
}
