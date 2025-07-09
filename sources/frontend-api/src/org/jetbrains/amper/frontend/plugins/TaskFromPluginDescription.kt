/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import java.nio.file.Path

interface TaskFromPluginDescription {
    val name: TaskName

    val actionClassJvmName: String
    val actionFunctionJvmName: String

    val actionArguments: Map<String, Any?>

    val explicitDependsOn: List<String>
    val inputs: List<Path>
    val outputs: List<Path>

    val codeSource: AmperModule

    val pluginId: String

    val outputMarks: List<OutputMark>

    interface OutputMark {
        val path: Path
        val kind: GeneratedPathKind
        val associateWith: Fragment
    }
}