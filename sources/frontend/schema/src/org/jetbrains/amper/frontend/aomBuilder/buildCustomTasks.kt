/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.BuildProblemImpl
import org.jetbrains.amper.core.messages.BuildProblemSource
import org.jetbrains.amper.core.messages.FileBuildProblemSource
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.messages.WholeFileBuildProblemSource
import org.jetbrains.amper.frontend.AddToModuleRootsFromCustomTask
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.CompositeString
import org.jetbrains.amper.frontend.CompositeStringPart
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.KnownCurrentTaskProperty
import org.jetbrains.amper.frontend.KnownModuleProperty
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskSourceSetType
import org.jetbrains.amper.frontend.project.customTaskName
import org.jetbrains.amper.frontend.tree.RefinedTree
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.frontend.tree.reading.readTree
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString


@OptIn(NonIdealDiagnostic::class)
internal fun BuildCtx.buildCustomTask(
    customTaskFile: VirtualFile,
    allModules: List<ModuleBuildCtx>,
) {
    val dir2module = allModules.associateBy { it.moduleFile.parent.toNioPath().absolute() }

    val module = dir2module[customTaskFile.parent.toNioPath().absolute()] ?: run {
        problemReporter.reportMessage(
            buildProblemId = "INVALID_CUSTOM_TASK_FILE_PATH",
            source = WholeFileBuildProblemSource(customTaskFile.toNioPath()),
            message = "Unable to find module for custom task file: $customTaskFile",
        )
        return
    }

    val taskTree = readTree(customTaskFile, types.getDeclaration<CustomTaskNode>()) ?: return
    // We can cast here only because no contexts are available inside the task definition.
    val node = createSchemaNode<CustomTaskNode>(taskTree as RefinedTree)
    val customTask = buildCustomTask(customTaskFile, node, module.module) {
        val modulePathPart = it.toNioPathOrNull() ?: return@buildCustomTask null
        module.moduleDirPath.resolve(modulePathPart).absolute().normalize().let(dir2module::get)?.module
    } ?: return

    module.module.customTasks += customTask
}

private fun BuildCtx.buildCustomTask(
    virtualFile: VirtualFile,
    node: CustomTaskNode,
    module: AmperModule,
    moduleResolver: (String) -> AmperModule?,
): CustomTaskDescription? {
    val buildProblemSource = object : FileBuildProblemSource {
        override val file: Path
            get() = virtualFile.toNioPath()
    }

    val codeModule = moduleResolver(node.module.pathString)
    if (codeModule == null) {
        problemReporter.reportMessage(
            buildProblemId = "UNKNOWN_MODULE",
            source = buildProblemSource,
            message = "Unresolved module reference: ${node.module.pathString}",
        )
        return null
    }

    return DefaultCustomTaskDescription(
        name = TaskName.moduleTask(module, virtualFile.customTaskName()),
        source = virtualFile.toNioPath(),
        origin = node,
        type = node.type,
        module = module,
        jvmArguments = node.jvmArguments.orEmpty()
            .map { parseStringWithReferences(it, buildProblemSource, moduleResolver) },
        programArguments = node.programArguments.orEmpty()
            .map { parseStringWithReferences(it, buildProblemSource, moduleResolver) },
        environmentVariables = node.environmentVariables.orEmpty()
            .mapValues { parseStringWithReferences(it.value, buildProblemSource, moduleResolver) },
        dependsOn = node.dependsOn.orEmpty().map { TaskName(it) },
        publishArtifacts = node.publishArtifact.orEmpty().map {
            DefaultPublishArtifactFromCustomTask(
                pathWildcard = it.path,
                artifactId = it.artifactId,
                classifier = it.classifier,
                extension = it.extension,
            )
        },
        customTaskCodeModule = codeModule,
        addToModuleRootsFromCustomTask = node.addTaskOutputToSourceSet.orEmpty().mapNotNull {
            val relativePath = it.taskOutputSubFolder.toNioPathOrNull()
            if (relativePath == null) {
                problemReporter.reportMessage(
                    buildProblemId = "INVALID_TASK_OUTPUT_SUBFOLDER",
                    source = buildProblemSource,
                    message = "'taskOutputSubFolder' property is not a relative path",
                )
                return@mapNotNull null
            }

            DefaultAddToModuleRootsFromCustomTask(
                taskOutputRelativePath = relativePath,
                isTest = it.addToTestSources,
                type = when (it.sourceSet) {
                    CustomTaskSourceSetType.SOURCES -> AddToModuleRootsFromCustomTask.Type.SOURCES
                    CustomTaskSourceSetType.RESOURCES -> AddToModuleRootsFromCustomTask.Type.RESOURCES
                },
                platform = Platform.JVM, // TODO
            )
        },
    )
}

private val propertyReferenceRegex = Regex("\\$\\{(module\\(([./0-9a-zA-Z\\-_]+)\\)\\.)?([0-9a-zA-Z]+)}")
private val unresolvedReferenceRegex1 = Regex("(?<!\\\\)\\$")
private val unresolvedReferenceRegex2 = Regex("\\\\\\\\\\$")

// TODO: This is not a real parser and it won't provide a good IDE support either
// Please decide on an appropriate references syntax and rewrite
internal fun ProblemReporterContext.parseStringWithReferences(
    value: String,
    source: BuildProblemSource,
    moduleResolver: (String) -> AmperModule?,
): CompositeString {
    var pos = 0
    val result = mutableListOf<CompositeStringPart>()

    fun addLiteralPart(part: String) {
        if (unresolvedReferenceRegex1.containsMatchIn(part) || unresolvedReferenceRegex2.containsMatchIn(part)) {
            problemReporter.reportMessage(
                buildProblemId = "STR_REF_UNRESOLVED_TYPE",
                source = source,
                message = "Contains unresolved reference: $part",
            )
        }

        val unescaped = part
            .replace("\\\\", "\u0000")
            .replace("\\$", "$")
            .replace("\u0000", "\\")

        result.add(CompositeStringPart.Literal(unescaped))
    }

    propertyReferenceRegex.findAll(value).forEach { match ->
        val literalPart = value.substring(pos, match.range.first)
        if (literalPart.isNotEmpty()) addLiteralPart(literalPart)
        pos = match.range.last + 1

        val (_, _, modulePath, propertyName) = match.groupValues

        if (modulePath.isEmpty()) {
            // current task property reference
            val knownProperty = KnownCurrentTaskProperty.namesMap[propertyName] ?: run {
                problemReporter.reportMessage(
                    buildProblemId = "STR_REF_UNKNOWN_CURRENT_TASK_PROPERTY",
                    source = source,
                    message = "Unknown current task property '$propertyName': ${match.value}",
                )
                return@forEach
            }

            result += CompositeStringPart.CurrentTaskProperty(
                property = knownProperty,
                originalReferenceText = match.value,
            )
        } else {
            // module property reference
            val knownPropertyName = KnownModuleProperty.namesMap[propertyName] ?: run {
                problemReporter.reportMessage(
                    buildProblemId = "STR_REF_UNKNOWN_MODULE_PROPERTY",
                    source = source,
                    message = "Unknown property name '$propertyName': ${match.value}",
                )
                return@forEach
            }

            val resolvedModule = moduleResolver(modulePath) ?: run {
                problemReporter.reportMessage(
                    buildProblemId = "STR_REF_UNKNOWN_MODULE",
                    source = source,
                    message = "Unknown module '$modulePath' referenced from '${match.value}'",
                )
                return@forEach
            }

            result += CompositeStringPart.ModulePropertyReference(
                referencedModule = resolvedModule,
                property = knownPropertyName,
                originalReferenceText = match.value,
            )
        }
    }

    if (pos < value.length) addLiteralPart(value.substring(pos))

    return CompositeString(parts = result)
}

private fun ProblemReporter.reportMessage(
    buildProblemId: String, source: BuildProblemSource, message: String, level: Level = Level.Error,
) = reportMessage(BuildProblemImpl(buildProblemId, source, message, level))