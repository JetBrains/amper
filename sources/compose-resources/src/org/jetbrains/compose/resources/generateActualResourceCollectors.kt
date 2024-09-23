/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.compose.resources

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.withIndent
import org.slf4j.Logger
import java.nio.file.Path

/**
 * Generates implementations for resource collectors.
 * Pairs with the [generateExpectResourceCollectors], if [useActualModifier] is set.
 *
 * This is intended to be generated into every leaf platform-specific source set.
 *
 * Depends on the results of [generateResourceAccessors].
 *
 * All parameter values must be in sync with the [expect generation call][generateExpectResourceCollectors],
 *  if [useActualModifier] is set.
 *
 * @param packageName kotlin package name to generate the code in.
 * @param makeAccessorsPublic if `true` then the code is public, `internal` otherwise.
 * @param useActualModifier if `true` then the `actual` modifier is used for generated implementations.
 * @param accessorDirectories the output directory of the [generateResourceAccessors] step.
 * @param outputSourceDirectory the generated source directory; the package directories hierarchy will be created in the
 *  directory if needed.
 * @param logger an optional user-facing logger to display warnings/information messages.
 */
fun generateActualResourceCollectors(
    packageName: String,
    makeAccessorsPublic: Boolean,
    useActualModifier: Boolean,
    accessorDirectories: List<Path>,
    outputSourceDirectory: Path,
    logger: Logger? = null,
) {
    val inputFiles = accessorDirectories.flatMap { dir ->
        dir.toFile().walkTopDown().filter { !it.isHidden && it.isFile && it.extension == "kt" }.toList()
    }

    val funNames = inputFiles.mapNotNull { inputFile ->
        if (inputFile.nameWithoutExtension.contains('.')) {
            val (fileName, suffix) = inputFile.nameWithoutExtension.split('.')
            val type = ResourceType.values().firstOrNull { fileName.startsWith(it.accessorName, true) }
            val name = "_collect${suffix.uppercaseFirstChar()}${fileName}Resources"

            if (type == null) {
                logger?.warn("Unknown resources type: `$inputFile`")
                null
            } else if (!inputFile.readText().contains(name)) {
                logger?.warn("A function '$name' is not found in the `$inputFile` file!")
                null
            } else {
                logger?.info("Found collector function: `$name`")
                type to name
            }
        } else {
            logger?.warn("Unknown file name: `$inputFile`")
            null
        }
    }.groupBy({ it.first }, { it.second })

    getActualResourceCollectorsFileSpec(
        packageName = packageName,
        isPublic = makeAccessorsPublic,
        fileName = "ActualResourceCollectors",
        useActualModifier = useActualModifier,
        typeToCollectorFunctions = funNames,
    ).writeTo(outputSourceDirectory)
}


internal fun getActualResourceCollectorsFileSpec(
    packageName: String,
    fileName: String,
    isPublic: Boolean,
    useActualModifier: Boolean, //e.g. java only project doesn't need actual modifiers
    typeToCollectorFunctions: Map<ResourceType, List<String>>
): FileSpec = FileSpec.builder(packageName, fileName).also { file ->
    val resModifier = if (isPublic) KModifier.PUBLIC else KModifier.INTERNAL

    file.addAnnotation(
        AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
            .addMember("org.jetbrains.compose.resources.InternalResourceApi::class")
            .build()
    )

    ResourceType.values().forEach { type ->
        val typeClassName = type.getClassName()
        val initBlock = CodeBlock.builder()
            .addStatement("lazy {").withIndent {
                addStatement("val map = mutableMapOf<String, %T>()", typeClassName)
                typeToCollectorFunctions.get(type).orEmpty().forEach { item ->
                    addStatement("%N(map)", item)
                }
                addStatement("return@lazy map")
            }
            .addStatement("}")
            .build()

        val mods = if (useActualModifier) {
            listOf(KModifier.ACTUAL, resModifier)
        } else {
            listOf(resModifier)
        }

        val property = PropertySpec
            .builder(
                "all${typeClassName.simpleName}s",
                MAP.parameterizedBy(String::class.asClassName(), typeClassName),
                mods
            )
            .addAnnotation(experimentalAnnotation)
            .receiver(ClassName(packageName, "Res"))
            .delegate(initBlock)
            .build()
        file.addProperty(property)
    }
}.build()
