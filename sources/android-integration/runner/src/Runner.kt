/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.tooling.GradleConnector
import org.jetbrains.amper.core.AmperBuild
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@OptIn(ExperimentalPathApi::class)
inline fun <reified R : AndroidBuildResult> runAndroidBuild(
    buildRequest: AndroidBuildRequest,
    debug: Boolean = false,
    sourcesPath: Path = Path.of("../../../../").toAbsolutePath().normalize()
): R {
    val homeDir = System.getProperty("user.home") ?: error("Cannot find user home directory")
    val homePath = Paths.get(homeDir)

    val tempDir = homePath
        .resolve(".android/build/${buildRequest.root.toAbsolutePath().toString().sha1}")
        .createDirectories()

    val settingsGradle = tempDir.resolve("settings.gradle.kts")
    val settingsGradleFile = settingsGradle.toFile()
    settingsGradleFile.createNewFile()

    val fromSources = AmperBuild.isSNAPSHOT

    val tempDirForSources = if (fromSources) {
        val tempDirForSources = homePath
            .resolve(".android/build/amper-sources-copy")
            .createDirectories()

        val followLinks = false
        val ignore = setOf(".gradle", "build", "caches", ".git")
        sourcesPath.copyToRecursively(tempDirForSources, followLinks = followLinks) { src, dst ->
            if (src.name in ignore) CopyActionResult.SKIP_SUBTREE
            else {
                val options = arrayOf(LinkOption.NOFOLLOW_LINKS)
                val dstIsDirectory = dst.isDirectory(LinkOption.NOFOLLOW_LINKS)
                val srcIsDirectory = src.isDirectory(*options)
                if ((srcIsDirectory && dstIsDirectory).not()) {
                    if (dstIsDirectory)
                        dst.deleteRecursively()

                    src.copyTo(dst, *options, StandardCopyOption.REPLACE_EXISTING)
                }

                // else: do nothing, the destination directory already exists
                CopyActionResult.CONTINUE
            }
        }
        tempDirForSources.toString().replace("\\", "\\\\")
    } else null

    settingsGradleFile.writeText(
        """
pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        maven("https://packages.jetbrains.team/maven/p/amper/amper")
        ${if (fromSources) "includeBuild(\"$tempDirForSources\")" else ""}
    }
}


plugins {
    id("org.jetbrains.amper.android.settings.plugin")${if (!fromSources) ".version(\"${AmperBuild.BuildNumber}\")" else ""}
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
                AndroidBuildRequest.Phase.Build -> "assemble"
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