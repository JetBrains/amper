/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.frontend.customTaskSchema.AddTaskOutputToSourceSetNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskSourceSetType
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskType
import org.jetbrains.amper.frontend.customTaskSchema.PublishArtifactNode

context(Converter)
internal fun MappingNode.convertCustomTask() = CustomTaskNode().apply {
    convertChildEnum(::type, CustomTaskType)
    convertChildScalar(::module) { asAbsolutePath() }
    convertChildScalarCollection(::jvmArguments) { textValue }
    convertChildScalarCollection(::programArguments) { textValue }
    convertChild(::environmentVariables) {
        asMappingNode()?.keyValues?.associate { it.keyText to it.value?.asScalarNode()?.textValue }
    }
    convertChildScalarCollection(::dependsOn) { textValue }
    convertChildMapping(::addTaskOutputToSourceSet) { convertSourceSet() }
    convertChildCollectionOfMappings(::publishArtifact) { convertPublishArtifact() }
}

context(Converter)
private fun MappingNode.convertSourceSet() = AddTaskOutputToSourceSetNode().apply {
    convertChildEnum(::sourceSet, CustomTaskSourceSetType)
    convertChildScalar(::taskOutputSubFolder) { textValue }
    convertChildBoolean(::addToTestSources)
}

context(Converter)
private fun MappingNode.convertPublishArtifact() = PublishArtifactNode().apply {
    val it = this // workaround for Kotlin compiler internal error
    convertChildScalar(it::path) { textValue }
    convertChildScalar(it::artifactId) { textValue }
    convertChildScalar(it::classifier) { textValue }
    convertChildScalar(it::extension) { textValue }
}
