/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.customTaskSchema.AddTaskOutputToSourceSetNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskSourceSetType
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskType
import org.jetbrains.amper.frontend.customTaskSchema.PublishArtifactNode
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx

context(ProblemReporterContext, ConvertCtx)
internal fun PsiElement.convertCustomTask() = CustomTaskNode().apply {
    val documentMapping = asMappingNode() ?: return@apply
    with(documentMapping) {
        ::type.convertChildEnum(CustomTaskType)
        ::module.convertChildScalar { asAbsolutePath() }
        ::jvmArguments.convertChildScalarCollection { textValue }
        ::programArguments.convertChildScalarCollection { textValue }
        ::environmentVariables.convertChild {
            asMappingNode()?.keyValues?.associate { it.keyText to it.value?.asScalarNode()?.textValue }
        }
        ::dependsOn.convertChildScalarCollection { textValue }
        ::addTaskOutputToSourceSet.convertChildCollection { convertSourceSet() }
        ::publishArtifact.convertChildCollection { convertPublishArtifact() }
    }
}

context(ProblemReporterContext, ConvertCtx)
private fun PsiElement.convertSourceSet() = AddTaskOutputToSourceSetNode().apply {
    with(asMappingNode() ?: return@apply) {
        ::sourceSet.convertChildEnum(CustomTaskSourceSetType)
        ::taskOutputSubFolder.convertChildScalar { textValue }
        ::addToTestSources.convertChildBoolean()
    }
}

context(ProblemReporterContext, ConvertCtx)
private fun PsiElement.convertPublishArtifact() = PublishArtifactNode().apply {
    with(asMappingNode() ?: return@apply) {
        ::path.convertChildScalar { textValue }
        ::artifactId.convertChildScalar { textValue }
        ::classifier.convertChildScalar { textValue }
        ::extension.convertChildScalar { textValue }
    }
}
