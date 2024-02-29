/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.tooling.GradleConnector
import org.jetbrains.amper.core.AmperBuild
import java.nio.file.Path
import kotlin.io.path.createDirectories

inline fun <reified R : AndroidBuildResult> runAndroidBuild(
    buildRequest: AndroidBuildRequest,
    debug: Boolean = false,
    sourcesPath: Path = Path.of("../../../../").toAbsolutePath().normalize(),
    userCacheDir: Path
): R {
    val projectSha1 = buildRequest.root.toAbsolutePath().toString().sha1
    val tempDir = userCacheDir
        .resolve("android/build/$projectSha1")
        .createDirectories()

    val settingsGradle = tempDir.resolve("settings.gradle.kts")
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

configure<AmperAndroidIntegrationExtension> {
    jsonData = ${"\"\"\""}${Json.encodeToString(buildRequest)}${"\"\"\""}
}
""".trimIndent()
    )

    val connection = GradleConnector
        .newConnector()
        .forProjectDirectory(settingsGradleFile.parentFile)
        .connect()

    val tasks = buildList {
        for (buildType in buildRequest.buildTypes) {
            val taskPrefix = when (buildRequest.phase) {
                AndroidBuildRequest.Phase.Prepare -> "prepare"
                AndroidBuildRequest.Phase.Build -> "build"
            }
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

    val buildLauncher = connection
        .action { controller -> controller.getModel(R::class.java) }
        .forTasks(*tasks)
        .withArguments("--stacktrace")
        .setStandardOutput(System.out)
        .setStandardError(System.err)



    buildRequest.sdkDir?.let {
        buildLauncher.setEnvironmentVariables(System.getenv() + mapOf("ANDROID_HOME" to it.toAbsolutePath().toString()))
    }

    if (debug) {
        buildLauncher.addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
    }
    return buildLauncher.run()
}