/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.types.instrumentation

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import java.util.Locale
import kotlin.reflect.KClass

internal sealed class ParsedDeclaration(
    /**
     * Generated declaration name, e.g. `foo.generated.DeclarationOfKotlinSettings`.
     */
    val declarationName: ClassName,
    /**
     * Original class name, e.g. `foo.bar.KotlinSettings`
     */
    val name: ClassName,
) {
    class SchemaNode(
        declarationName: ClassName,
        name: ClassName,
        /**
         * If the schema contains types annotated with [org.jetbrains.amper.frontend.api.CustomSchemaDeclaration],
         *  declartions for such types must be provided by the user.
         *
         * When this happens, the declartion is generated as a `class` instead of an `object`, and custom declarations
         *  are passed as parameters to the constructor.
         *
         * If such custom declaration is nested, e.g. `PluginYamlRoot` requires the `Task` declaration, but the `Task`
         * has a custom `TaskAction` declaration. Then the `Task` will accept the `TaskAction` declaration, and the
         * `PluginYamlRoot` will accept the `Task` declaration. This way the user could instantiate these declarations
         * like this:
         * ```kotlin
         * val pluginYamlRoot = DeclarationOfPluginYamlRoot(  // generated
         *     taskDeclaration = DeclarationOfTask(           // generated
         *         taskActionDeclaration = SomeCustomImplementationHere(),
         *     ),
         *  )
         * ```
         */
        val declarationParameters: List<Parameter>,
    ) : ParsedDeclaration(declarationName, name) {

        internal class Parameter(
            schemaNodeClass: KClass<out org.jetbrains.amper.frontend.api.SchemaNode>,
            /**
             * Parameter type, usually a [org.jetbrains.amper.frontend.types.SchemaObjectDeclaration], or something
             * more precise.
             */
            val parameterType: TypeName,
        ) {
            val parameterName: String =
                schemaNodeClass.simpleName!!.replaceFirstChar { it.lowercase(Locale.ROOT) } + "Declaration"
        }

    }

    class Enum(
        declarationName: ClassName,
        name: ClassName,
    ) : ParsedDeclaration(declarationName, name)

    class SealedSchemaNode(
        declarationName: ClassName,
        name: ClassName,
    ) : ParsedDeclaration(declarationName, name)
}