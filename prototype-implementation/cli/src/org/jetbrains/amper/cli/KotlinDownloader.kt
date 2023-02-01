/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.downloader.downloadFileToCacheLocation
import org.jetbrains.amper.downloader.extractFileToCacheLocation
import org.jetbrains.amper.downloader.getUriForMavenArtifact
import java.nio.file.Path

private const val MAVEN_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"
private const val ARTIFACT_GROUP_ID = "org.jetbrains.kotlin"

suspend fun downloadAndExtractKotlinCompiler(version: String, userCacheRoot: AmperUserCacheRoot): Path {
    val kotlinDistUrl = getUriForMavenArtifact(
        mavenRepository = MAVEN_REPOSITORY_URL,
        groupId = ARTIFACT_GROUP_ID,
        artifactId = "kotlin-dist-for-ide",
        version = version, packaging = "jar").toString()
    val kotlinDistJar = downloadFileToCacheLocation(kotlinDistUrl, userCacheRoot)
    return extractFileToCacheLocation(kotlinDistJar, userCacheRoot)
}
