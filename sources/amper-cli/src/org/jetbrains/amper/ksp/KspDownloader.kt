/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.ksp

import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.resolver.MavenResolver
import java.nio.file.Path

suspend fun MavenResolver.downloadKspJars(kspVersion: String, repositories: List<Repository>): List<Path> = resolve(
    // Copying the KSP Gradle plugin's classpath
    // https://github.com/google/ksp/blob/ee43116745ff921018bfe70344b8b21c590c2c16/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt#L137-L144
    coordinates = listOf(
        "com.google.devtools.ksp:symbol-processing-api:$kspVersion",
        "com.google.devtools.ksp:symbol-processing-aa-embeddable:$kspVersion",
        "com.google.devtools.ksp:symbol-processing-common-deps:$kspVersion",
    ),
    repositories = repositories,
    scope = ResolutionScope.RUNTIME,
    platform = ResolutionPlatform.JVM,
    resolveSourceMoniker = "KSP command-line version $kspVersion",
)
