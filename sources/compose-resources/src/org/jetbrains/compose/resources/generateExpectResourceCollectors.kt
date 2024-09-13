/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.compose.resources

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import java.nio.file.Path

/**
 * Generates `expect` implementations for resource collectors generated with
 * [generateExpectResourceCollectors].
 *
 * This is intended to be generated into "commonMain" source set.
 *
 * @param packageName kotlin package name to generate the code in.
 * @param makeAccessorsPublic if `true` then the code is public, `internal` otherwise.
 * @param outputSourceDirectory the generated source directory; the package directories hierarchy will be created in the
 *  directory if needed.
 */
fun generateExpectResourceCollectors(
    packageName: String,
    makeAccessorsPublic: Boolean,
    outputSourceDirectory: Path,
) {
    getExpectResourceCollectorsFileSpec(
        packageName = packageName,
        fileName = "ExpectResourceCollectors",
        isPublic = makeAccessorsPublic,
    ).writeTo(outputSourceDirectory)
}

private fun getExpectResourceCollectorsFileSpec(
    packageName: String,
    fileName: String,
    isPublic: Boolean
): FileSpec {
    val resModifier = if (isPublic) KModifier.PUBLIC else KModifier.INTERNAL
    return FileSpec.builder(packageName, fileName).also { file ->
        ResourceType.values().forEach { type ->
            val typeClassName = type.getClassName()
            file.addProperty(
                PropertySpec
                    .builder(
                        "all${typeClassName.simpleName}s",
                        MAP.parameterizedBy(String::class.asClassName(), typeClassName),
                        KModifier.EXPECT,
                        resModifier
                    )
                    .addAnnotation(experimentalAnnotation)
                    .receiver(ClassName(packageName, "Res"))
                    .build()
            )
        }
    }.build()
}