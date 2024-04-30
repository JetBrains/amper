/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.jetbrains.amper.core.AmperBuild
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

fun <R : AndroidBuildResult> runAndroidBuild(
    buildRequest: AndroidBuildRequest,
    buildPath: Path,
    gradleLogStdoutPath: Path,
    gradleLogStderrPath: Path,
    resultClass: Class<R>,
    debug: Boolean = false,
    eventHandler: (ProgressEvent) -> Unit,
): R {
    buildPath.createDirectories()
    val settingsGradle = buildPath.resolve("settings.gradle.kts")
    val settingsGradleFile = settingsGradle.toFile()
    settingsGradleFile.createNewFile()

    val fromSources = AmperBuild.isSNAPSHOT

    settingsGradleFile.writeText(
        """
pluginManagement {
    repositories {
        ${if (fromSources) "mavenLocal()" else ""}
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        ${if (fromSources) "" else "maven(\"https://packages.jetbrains.team/maven/p/amper/amper\")"}
    }
}

plugins {
    id("org.jetbrains.amper.android.settings.plugin").version("${AmperBuild.BuildNumber}")
}

configure<org.jetbrains.amper.android.gradle.AmperAndroidIntegrationExtension> {
    jsonData = ${"\"\"\""}${Json.encodeToString(buildRequest)}${"\"\"\""}
}
""".trimIndent()
    )

    val connection = GradleConnector
        .newConnector()
        .forProjectDirectory(settingsGradleFile.parentFile)
        .connect()

    val taskPrefix = when (buildRequest.phase) {
        AndroidBuildRequest.Phase.Prepare -> "prepare"
        AndroidBuildRequest.Phase.Build -> "build"
    }

    val stdout = gradleLogStdoutPath
        .outputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
        .buffered()
    val stderr = gradleLogStderrPath
        .outputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
        .buffered()

    connection.use {
        val tasks = buildList {
            for (buildType in buildRequest.buildTypes) {
                val taskBuildType = buildType.name
                val taskName = "$taskPrefix$taskBuildType"
                if (buildRequest.targets.isEmpty()) {
                    add(taskName)
                } else {
                    for (target in buildRequest.targets) {
                        if (target == ":") {
                            add(":$taskName")
                        } else {
                            add("$target:$taskName")
                        }
                    }
                }
            }
        }.toTypedArray()

        try {
            stdout.use { stdoutStream ->
                stderr.use { stderrStream ->
                    val buildLauncher = connection
                        .action { controller -> controller.getModel(resultClass) }
                        .forTasks(*tasks)
                        .withArguments("--stacktrace")
                        .addJvmArguments("-Xmx4G", "-XX:MaxMetaspaceSize=1G")
                        .addProgressListener(ProgressListener { eventHandler(it) })
                        .setStandardOutput(stdoutStream)
                        .setStandardError(stderrStream)

                    buildRequest.sdkDir?.let {
                        buildLauncher.setEnvironmentVariables(
                            System.getenv() + mapOf(
                                "ANDROID_HOME" to it.toAbsolutePath().toString()
                            )
                        )
                    }

                    if (debug) {
                        buildLauncher.addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
                    }

                    return buildLauncher.run()
                }
            }
        } catch (t: RuntimeException) {
            throw IllegalStateException("Error during Gradle build", t)
        }
    }
}
