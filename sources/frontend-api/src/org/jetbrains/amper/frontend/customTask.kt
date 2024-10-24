/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskType
import java.nio.file.Path

interface PublishArtifactFromCustomTask {
    val pathWildcard: String
    val artifactId: String
    val classifier: String
    val extension: String
}

interface AddToModuleRootsFromCustomTask {
    val taskOutputRelativePath: Path
    val type: Type
    val isTest: Boolean
    val platform: Platform

    enum class Type {
        RESOURCES,
        SOURCES,
    }
}

enum class KnownModuleProperty(
    val propertyName: String,
    val dependsOnModuleTask: List<String> = emptyList(),
) {
    VERSION("version"),
    NAME("name"),
    JVM_RUNTIME_CLASSPATH("runtimeClasspathJvm", dependsOnModuleTask = listOf("runtimeClasspathJvm"));

    companion object {
        val namesMap = entries.associateBy { it.propertyName }
    }
}

enum class KnownCurrentTaskProperty(
    val propertyName: String,
) {
    OUTPUT_DIRECTORY("outputDirectory");

    companion object {
        val namesMap = entries.associateBy { it.propertyName }
    }
}

sealed class CompositeStringPart {
    data class Literal(val value: String): CompositeStringPart() {
        init {
            check(value.isNotEmpty()) {
                "Value must not be empty"
            }
        }
    }

    data class ModulePropertyReference(
        val referencedModule: AmperModule,
        val property: KnownModuleProperty,
        val originalReferenceText: String,
    ): CompositeStringPart() {
        init {
            check(originalReferenceText.isNotEmpty()) {
                "Reference text should not be empty"
            }
        }
    }

    data class CurrentTaskProperty(
        val property: KnownCurrentTaskProperty,
        val originalReferenceText: String,
    ): CompositeStringPart() {
        init {
            check(originalReferenceText.isNotEmpty()) {
                "Reference text should not be empty"
            }
        }
    }

    /*
        data class TaskPropertyReference(
            val taskName: TaskName,
            val propertyName: KnownTaskProperty,
            val originalReferenceText: String,
        ): CompositeStringPart() {
            init {
                check(originalReferenceText.isNotEmpty()) {
                    "Reference text should not be empty"
                }
            }
        }
    */
}

data class CompositeString(val parts: List<CompositeStringPart>)

interface CustomTaskDescription {
    val name: TaskName
    val source: Path
    val origin: CustomTaskNode
    val type: CustomTaskType
    val module: AmperModule
    val customTaskCodeModule: AmperModule
    val jvmArguments: List<CompositeString>
    val programArguments: List<CompositeString>
    val environmentVariables: Map<String, CompositeString>
    val dependsOn: List<TaskName>
    val publishArtifacts: List<PublishArtifactFromCustomTask>
    val addToModuleRootsFromCustomTask: List<AddToModuleRootsFromCustomTask>
}
