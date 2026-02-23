/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types.instrumentation

import com.squareup.kotlinpoet.MemberName
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.plugins.MinimalPluginModule
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.reflect.KClass

/**
 * Declarations will be generated for these types and all those reachable from them.
 */
internal val RootTypes: List<KClass<*>> = listOf(
    Module::class,
    Template::class,
    Project::class,
    MinimalModule::class,
    MinimalPluginModule::class,
    PluginYamlRoot::class,
)

@TaskAction
fun generateSchemaImplementation(
    @Output outputDirectory: Path,
) {
    outputDirectory.createParentDirectories()
    outputDirectory.deleteRecursively()

    context(Generator(outputDirectory)) {
        RootTypes.forEach { klass ->
            ensureParsed(klass)
        }

        // Shadow types are handled here
        generateShadowMapsPublicInterfaceToDeclaration()
    }
}

internal const val TARGET_PACKAGE = "org.jetbrains.amper.frontend.types.generated"

internal val PathConstructor = MemberName("kotlin.io.path", "Path")
