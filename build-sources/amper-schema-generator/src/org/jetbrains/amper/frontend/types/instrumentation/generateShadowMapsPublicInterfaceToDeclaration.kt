/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.types.instrumentation

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.asTypeName
import org.jetbrains.amper.frontend.plugins.generated.ShadowMaps
import org.jetbrains.amper.frontend.types.SchemaTypeDeclaration

/**
 * Generates a `Map<String, SchemaTypeDeclaration>` that maps public interface names to their corresponding
 * "shadow" declarations.
 *
 * The inputs are from [ShadowMaps.PublicInterfaceToShadowNodeClass].
 *
 * The receiver is [ShadowMaps] for consistency with the similar properties there.
 */
context(generator: Generator)
internal fun generateShadowMapsPublicInterfaceToDeclaration() {
    val propertyType = MAP.parameterizedBy(
        STRING,
        SchemaTypeDeclaration::class.asTypeName(),
    )
    generator.writeFile(
        FileSpec.builder(TARGET_PACKAGE, "PublicInterfaceToDeclaration")
            .addGeneratedComment()
            .addSuppressRedundantVisibilityModifier()
            .addProperty(
                PropertySpec.builder("_publicInterfaceToDeclaration", propertyType, KModifier.PRIVATE)
                    .initializer(CodeBlock.builder().apply {
                        beginControlFlow("buildMap")
                        ShadowMaps.PublicInterfaceToShadowNodeClass.forEach { (publicInterfaceName, klass) ->
                            addStatement("put(%S, %T)", publicInterfaceName, ensureParsed(klass).declarationName)
                        }
                        endControlFlow()
                    }.build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("PublicInterfaceToDeclaration", propertyType)
                    .receiver(ShadowMaps::class)
                    .getter(FunSpec.getterBuilder().addCode("return _publicInterfaceToDeclaration").build())
                    .build()
            )
            .build()
    )
}