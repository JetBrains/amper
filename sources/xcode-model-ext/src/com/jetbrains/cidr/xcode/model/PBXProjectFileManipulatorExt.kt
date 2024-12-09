/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.jetbrains.cidr.xcode.model

/**
 * Adds a special `PBXFileSystemSynchronizedRootGroup` group to the project.
 *
 * @param addToTargets targets that are going to be built from the sources in the group
 * @param membershipExceptions paths relative to the group [path] that are not going to belong to the group
 */
fun PBXProjectFileManipulator.addFileSystemSynchronizedRootGroup(
    sourceTree: String?,
    name: String?,
    path: String,
    addToTargets: List<PBXTarget>,
    membershipExceptions: List<String> = emptyList(),
    parent: PBXGroup? = null,
    forceAdd: Boolean = false,
) {
    val group = addGroup(sourceTree, name, path, parent, forceAdd)
    group["isa"] = "PBXFileSystemSynchronizedRootGroup"
    group.remove("children")

    addToTargets.forEach { target ->
        target.addToAttributeList("fileSystemSynchronizedGroups", group.createReference())

        if (membershipExceptions.isNotEmpty()) {
            val pbxProjectFile = group.file
            val exceptionSet = PBXObject(pbxProjectFile)
            exceptionSet["isa"] = "PBXFileSystemSynchronizedBuildFileExceptionSet"
            exceptionSet["membershipExceptions"] = membershipExceptions.toList()
            exceptionSet["target"] = target.createReference()

            // This API is package-private
            pbxProjectFile.addObject(exceptionSet, null)

            group.addToAttributeList("exceptions", exceptionSet.createReference())
        }
    }
}
