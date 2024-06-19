/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.customTaskSchema.AddTaskOutputToSourceSetNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskType
import org.jetbrains.amper.frontend.customTaskSchema.PublishArtifactNode
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLValue

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLDocument.convertCustomTask() = CustomTaskNode().apply {
    val documentMapping = getTopLevelValue()?.asMappingNode() ?: return@apply
    with(documentMapping) {
        ::type.convertChildEnum(CustomTaskType)
        ::module.convertChildScalar { asAbsolutePath() }
        ::jvmArguments.convertChildScalarCollection { textValue }
        ::programArguments.convertChildScalarCollection { textValue }
        ::environmentVariables.convertChild {
            asMappingNode()?.keyValues?.associate { it.keyText to it.valueText }
        }
        ::dependsOn.convertChildScalarCollection { textValue }
        ::addTaskOutputToSourceSet.convertChildCollection { convertSourceSet() }
        ::publishArtifact.convertChildCollection { convertPublishArtifact() }
    }
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLValue.convertSourceSet() = AddTaskOutputToSourceSetNode().apply {
    with(asMappingNode() ?: return@apply) {
        ::taskOutputSubFolder.convertChildScalar { textValue }
        ::addToTestSources.convertChildBoolean()
    }
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLValue.convertPublishArtifact() = PublishArtifactNode().apply {
    with(asMappingNode() ?: return@apply) {
        ::path.convertChildScalar { textValue }
        ::artifactId.convertChildScalar { textValue }
        ::classifier.convertChildScalar { textValue }
        ::extension.convertChildScalar { textValue }
    }
}
