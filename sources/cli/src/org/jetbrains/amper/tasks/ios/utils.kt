/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.cidr.xcode.model.PBXGroup
import com.jetbrains.cidr.xcode.model.PBXProjectFileManipulator
import com.jetbrains.cidr.xcode.model.PBXTarget
import com.jetbrains.cidr.xcode.plist.Plist
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.util.BuildType
import java.io.File
import java.util.*

val BuildType.variantName get() = value.lowercase().replaceFirstChar { it.titlecase() }

internal val Platform.architecture
    get() = when (this) {
        Platform.IOS_ARM64 -> "arm64"
        Platform.IOS_X64 -> "x86_64"
        Platform.IOS_SIMULATOR_ARM64 -> "arm64"
        else -> error("Cannot determine apple architecture for $this")
    }

internal val Platform.sdk
    get() = when (this) {
        Platform.IOS_ARM64, Platform.IOS_X64 -> "iphoneos"
        Platform.IOS_SIMULATOR_ARM64 -> "iphonesimulator"
        else -> error("Cannot determine apple platform for $this")
    }

internal fun MutableMap<String, Any>.mergeListSetting(setting: String, searchPaths: List<String>) {
    compute(setting) { _, oldValue ->
        when (oldValue) {
            null -> listOf("$(inherited)")
            is List<*> -> oldValue
            is String -> listOf(oldValue)
            else -> return@compute oldValue
        }.plus(searchPaths)
    }
}

internal fun Map<String, *>.toPlist(): Plist = Plist().also { plist ->
    for ((k, v) in this) {
        @Suppress("UNCHECKED_CAST")
        plist[k] = when (v) {
            is Map<*, *> -> (v as Map<String, *>).toPlist()
            else -> v
        }
    }
}

internal class FictionFile : File("")

fun PBXProjectFileManipulator.addTargetFiles(
    targetMemberships: Array<PBXTarget>,
    group: PBXGroup,
    sourceDirs: Set<File>
) {
    val groupStack = ArrayDeque<PBXGroup>()
    groupStack.push(group)
    val fileStack = ArrayDeque<File>()

    fun addChildrenToFileStack(parentFile: File) {
        parentFile.listFiles()?.asSequence()?.flatMap { file ->
            // flatten storyboards from lproj bundles
            when (file.extension) {
                "lproj" -> file.listFiles()?.asSequence() ?: emptySequence()
                else -> sequenceOf(file)
            }
        }?.mapNotNull { file ->
            val lowerCasedName = file.name.toLowerCase()
            if (lowerCasedName.startsWith(".")) null
            else Pair(lowerCasedName, file)
        }?.sortedByDescending { (lowerCasedName, _) ->
            lowerCasedName
        }?.forEach { (_, file) ->
            fileStack.push(file)
        }
    }

    sourceDirs.forEach { addChildrenToFileStack(it) }

    while (fileStack.isNotEmpty()) {
        val file = fileStack.pop()

        if (file is FictionFile) {
            groupStack.pop()
            continue
        }

        val parentGroup = groupStack.peek()
        val referenceOnly = FileUtil.extensionEquals(file.path, "xcassets")
        val addResult = addFile(file.path, targetMemberships, parentGroup, !referenceOnly)
        val createdGroup = addResult.reference as? PBXGroup

        if (!addResult.childrenProcessed && createdGroup != null) {
            groupStack.push(createdGroup)
            fileStack.push(FictionFile())
            addChildrenToFileStack(file)
        }
    }
}