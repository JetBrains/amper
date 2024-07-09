/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


package org.jetbrains.amper.dependency.resolution.org.jetbrains.amper.android

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.amper.core.extract.extractFileToLocation
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension


/**
 * @return the list of paths which is pointed to extracted folders
 */
suspend fun DependencyNode.extractAars(): List<Path> = coroutineScope {
    distinctBfsSequence()
        .filterIsInstance<MavenDependencyNode>()
        .flatMap { it.dependency.files() }
        .map { async { it.getPath() } }
        .toList()
        .awaitAll()
        .filterNotNull()
        .extractAars()
}

suspend fun List<Path>.extractAars(): List<Path> = coroutineScope {
    filter { it.extension == "aar" }
        .map {
            async {
                val targetFolder = it.parent / it.nameWithoutExtension
                extractFileToLocation(it, targetFolder)
                targetFolder
            }
        }.awaitAll()
}
