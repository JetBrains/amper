/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.asAmperSuccess
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.ReaderCtx
import java.io.Reader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.reader

class SchemaBasedModelImport : ModelInit {
    override val name = "schema-based"

    context(ProblemReporterContext)
    override fun getModel(root: Path): Result<Model> {
        val rootDir = if (root.isDirectory()) root else root.parent

        // TODO Replace default reader by something other.
        // TODO Report non existing file.
        val path2Reader: (Path) -> Reader? = {
            it.takeIf { it.exists() }?.reader()
        }

        val toIgnore = rootDir.parseAmperIgnorePaths()
        val amperModuleFiles = rootDir.findAmperModuleFiles(toIgnore)
        val dumbGradleGradleModules = rootDir.findGradleModules(toIgnore, amperModuleFiles)

        val resultModules = doBuild(
            ReaderCtx(path2Reader),
            amperModuleFiles,
            dumbGradleGradleModules,
        ) ?: return amperFailure()

        // Propagate parts from fragment to fragment.
        return DefaultModel(resultModules + dumbGradleGradleModules.values).resolved.asAmperSuccess()
    }
}