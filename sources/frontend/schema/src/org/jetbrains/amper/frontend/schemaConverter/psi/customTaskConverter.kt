/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.customTaskSchema.AddTaskOutputToSourceSetNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskSourceSetType
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskType
import org.jetbrains.amper.frontend.customTaskSchema.PublishArtifactNode

context(Converter)
internal fun PsiElement.convertCustomTask() = CustomTaskNode().apply {
    (asMappingNode() ?: return@apply).apply {
        convertChildEnum(::type, CustomTaskType)
        convertChildScalar(::module) { asAbsolutePath() }
        convertChildScalarCollection(::jvmArguments) { textValue }
        convertChildScalarCollection(::programArguments) { textValue }
        convertChild(::environmentVariables) {
            asMappingNode()?.keyValues?.associate { it.keyText to it.value?.asScalarNode()?.textValue }
        }
        convertChildScalarCollection(::dependsOn) { textValue }
        convertChildCollection(::addTaskOutputToSourceSet) { convertSourceSet() }
        convertChildCollection(::publishArtifact) { convertPublishArtifact() }
    }
}

context(Converter)
private fun PsiElement.convertSourceSet() = AddTaskOutputToSourceSetNode().apply {
    (asMappingNode() ?: return@apply).apply {
        convertChildEnum(::sourceSet, CustomTaskSourceSetType)
        convertChildScalar(::taskOutputSubFolder) { textValue }
        convertChildBoolean(::addToTestSources)
    }
}

context(Converter)
private fun PsiElement.convertPublishArtifact() = PublishArtifactNode().apply {
    (asMappingNode() ?: return@apply).apply {
        convertChildScalar(::path) { textValue }
        convertChildScalar(::artifactId) { textValue }
        convertChildScalar(::classifier) { textValue }
        convertChildScalar(::extension) { textValue }
    }
}
