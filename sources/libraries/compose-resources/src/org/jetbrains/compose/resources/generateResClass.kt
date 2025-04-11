/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.compose.resources

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeSpec
import java.nio.file.Path

/**
 * Generates a `Res` class in the [given package][packageName]. The class contains objects to be used as receivers
 * to extension accessors for each resource type.
 *
 * This is intended to be generated into the "commonMain" source set.
 *
 * @param packageName kotlin package name to generate the class in.
 * @param packagingDir is a relative path for resource packaging to be used to load resources at runtime.
 * @param isPublic if `true` then the class is public, `internal` otherwise.
 * @param outputSourceDirectory the generated source directory; the package directories hierarchy will be created in the
 *  directory if needed.
 */
fun generateResClass(
    packageName: String,
    packagingDir: String,
    isPublic: Boolean,
    outputSourceDirectory: Path,
) {
    getResFileSpec(
        packageName = packageName,
        moduleDir = packagingDir,
        isPublic = isPublic,
    ).writeTo(outputSourceDirectory)
}

private fun getResFileSpec(
    packageName: String,
    moduleDir: String,
    isPublic: Boolean,
): FileSpec {
    val resModifier = if (isPublic) KModifier.PUBLIC else KModifier.INTERNAL
    return FileSpec.builder(packageName, "Res").also { file ->
        file.addAnnotation(
            AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                .addMember("org.jetbrains.compose.resources.InternalResourceApi::class")
                .addMember("org.jetbrains.compose.resources.ExperimentalResourceApi::class")
                .build()
        )
        file.addType(TypeSpec.objectBuilder("Res").also { resObject ->
            resObject.addModifiers(resModifier)

            //readFileBytes
            val readResourceBytes = MemberName("org.jetbrains.compose.resources", "readResourceBytes")
            resObject.addFunction(
                FunSpec.builder("readBytes")
                    .addKdoc(
                        """
                    Reads the content of the resource file at the specified path and returns it as a byte array.
                    
                    Example: `val bytes = Res.readBytes("files/key.bin")`
                    
                    @param path The path of the file to read in the compose resource's directory.
                    @return The content of the file as a byte array.
                """.trimIndent()
                    )
                    .addAnnotation(experimentalAnnotation)
                    .addParameter("path", String::class)
                    .addModifiers(KModifier.SUSPEND)
                    .returns(ByteArray::class)
                    .addStatement("""return %M(%S + path)""", readResourceBytes, moduleDir)
                    .build()
            )

            //getUri
            val getResourceUri = MemberName("org.jetbrains.compose.resources", "getResourceUri")
            resObject.addFunction(
                FunSpec.builder("getUri")
                    .addKdoc(
                        """
                    Returns the URI string of the resource file at the specified path.
                    
                    Example: `val uri = Res.getUri("files/key.bin")`
                    
                    @param path The path of the file in the compose resource's directory.
                    @return The URI string of the file.
                """.trimIndent()
                    )
                    .addAnnotation(experimentalAnnotation)
                    .addParameter("path", String::class)
                    .returns(String::class)
                    .addStatement("""return %M(%S + path)""", getResourceUri, moduleDir)
                    .build()
            )

            ResourceType.entries.forEach { type ->
                resObject.addType(TypeSpec.objectBuilder(type.accessorName).build())
            }
        }.build())
    }.build()
}
