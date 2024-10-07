/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.api.CustomSchemaDef
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString

class KspSettings : SchemaNode() {

    @SchemaDoc("The version of KSP to use")
    var version by value(UsedVersions.kspVersion)

    @SchemaDoc("The list of KSP processors to use. Each item can be a path to a local module, a catalog reference, or maven coordinates.")
    var processors by value<List<KspProcessorDeclaration>>(default = emptyList())

    @SchemaDoc("Some options to pass to KSP processors. Refer to each processor documentation for details.")
    var processorOptions by value<Map<TraceableString, TraceableString>>(default = emptyMap())
}

@CustomSchemaDef(""""type": "string"""")
sealed interface KspProcessorDeclaration

data class MavenKspProcessorDeclaration(val coordinates: TraceableString) : KspProcessorDeclaration

data class ModuleKspProcessorDeclaration(val path: TraceablePath) : KspProcessorDeclaration

data class CatalogKspProcessorDeclaration(val catalogKey: CatalogKey) : KspProcessorDeclaration

/**
 * Whether KSP should be run.
 */
val KspSettings.enabled: Boolean
    get() = processors.isNotEmpty()
