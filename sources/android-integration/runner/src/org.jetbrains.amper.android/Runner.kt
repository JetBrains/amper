/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android

import com.android.builder.model.v2.models.AndroidProject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.tooling.BuildAction
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.GradleProject
import org.jetbrains.amper.core.AmperBuild
import java.io.BufferedOutputStream
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream


fun runAndroidBuild(
    buildRequest: AndroidBuildRequest,
    buildPath: Path,
    gradleLogStdoutPath: Path,
    gradleLogStderrPath: Path,
    debug: Boolean = false,
    eventHandler: (ProgressEvent) -> Unit,
): List<Path> {
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

    val stdout = gradleLogStdoutPath
        .outputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
        .buffered()
    val stderr = gradleLogStderrPath
        .outputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
        .buffered()

    connection.use {
        val androidProjects = it.extractAndroidProjectModelsFromBuild(debug)
        val lazyArtifacts = buildList {
            for (target in buildRequest.targets) {
                val androidProject = androidProjects[target] ?: continue
                val tasks = androidProject.taskList(it, buildRequest, target)
                androidProject.lazyArtifacts(buildRequest).also { addAll(it) }
                try {
                    stdout.use { stdoutStream ->
                        stderr.use { stderrStream ->
                            it.runBuild(tasks, eventHandler, stdoutStream, stderrStream, buildRequest, debug)
                        }
                    }
                } catch (t: RuntimeException) {
                    throw IllegalStateException("Error during Gradle build", t)
                }

            }
        }

        return lazyArtifacts.map { it.value }
    }
}

private fun AndroidProject.lazyArtifacts(
    buildRequest: AndroidBuildRequest,
): List<LazyArtifact> = buildList {
    for (buildType in buildRequest.buildTypes) {
        variants.filter { it.name == buildType.value }.forEach { variant ->
            when (buildRequest.phase) {
                AndroidBuildRequest.Phase.Prepare -> variant.mainArtifact.classesFolders.map { it.toPath() }
                    .forEach { add(DirectLazyArtifact(it)) }

                AndroidBuildRequest.Phase.Build -> add(
                    redirect(
                        variant.mainArtifact.assembleTaskOutputListingFile ?: error("File must exist")
                    )
                )

                AndroidBuildRequest.Phase.Bundle -> add(
                    redirect(
                        variant.mainArtifact.bundleInfo?.bundleTaskOutputListingFile
                            ?: error("File must exist")
                    )
                )
            }
        }
    }
}

private fun AndroidProject.taskList(
    connection: ProjectConnection,
    buildRequest: AndroidBuildRequest,
    projectPath: String
): List<String> = buildList {
    for (buildType in buildRequest.buildTypes) {
        for (variant in variants.filter { it.name == buildType.value }) {
            val taskName = when (buildRequest.phase) {
                AndroidBuildRequest.Phase.Prepare -> {
                    val processResourcesProviderData = connection.model<ProcessResourcesProviderData>(ProcessResourcesProviderData::class.java).get()
                    processResourcesProviderData.data[projectPath]?.get(variant.name)
                        ?: error("Incorrect ProcessResourcesProviderData for variant: ${variant.displayName}, data: $processResourcesProviderData")
                }
                AndroidBuildRequest.Phase.Build -> variant.mainArtifact.assembleTaskName
                AndroidBuildRequest.Phase.Bundle -> variant.mainArtifact.bundleInfo?.bundleTaskName
                    ?: error("Bundle info not found for variant: ${variant.displayName}")
            }

            for (target in buildRequest.targets) {
                if (target == ":") {
                    add(":$taskName")
                } else {
                    add("$target:$taskName")
                }
            }
        }
    }
}

private fun ProjectConnection.runBuild(
    tasks: List<String>,
    eventHandler: (ProgressEvent) -> Unit,
    stdoutStream: BufferedOutputStream,
    stderrStream: BufferedOutputStream,
    buildRequest: AndroidBuildRequest,
    debug: Boolean
) {
    val buildLauncher = newBuild()
        .forTasks(*tasks.toTypedArray())
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

    buildLauncher.run()
}

private fun ProjectConnection.extractAndroidProjectModelsFromBuild(debug: Boolean): Map<String, AndroidProject> {
    val actionLauncher = action(BuildAction { controller ->
        val gradleProject = controller.findModel(GradleProject::class.java)
        val q = ArrayDeque<GradleProject>()
        q.add(gradleProject)
        buildMap {
            while (q.isNotEmpty()) {
                val project = q.removeFirst()
                project.children.forEach { q.add(it) }
                val androidProject = controller.findModel<AndroidProject>(project, AndroidProject::class.java)
                if (androidProject != null) {
                    put(project.path, androidProject)
                }
            }
        }
    })

    if (debug) {
        actionLauncher.addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
    }

    return actionLauncher.run()
}
