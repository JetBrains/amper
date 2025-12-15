/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.writeText

internal class ProjectTreeMaterializer(private val amperForest: AmperForest) {

    fun materialize(overwrite: Boolean = false) {
        if (amperForest.projectPath.exists() && !overwrite) {
            throw FileAlreadyExistsException(amperForest.projectPath.toFile())
        }

        val projectTree = amperForest.projectTree

        val projectContent = projectTree.serializeToYaml()
        amperForest.projectPath.writeText(
            projectContent,
            options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
        )

        amperForest.modules.forEach { (modulePath, moduleTree) ->
            if (modulePath.exists() && !overwrite) {
                throw FileAlreadyExistsException(modulePath.toFile())
            }

            modulePath.writeText(
                moduleTree.serializeToYaml(),
                options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
            )
        }
    }
}
