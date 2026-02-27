/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.CustomSchemaDeclaration
import org.jetbrains.amper.frontend.api.FromKeyAndTheRestIsNested
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.StringSemantics
import org.jetbrains.amper.frontend.types.SchemaType.StringType.Semantics

class MavenPlugin : SchemaNode() {
    
    @FromKeyAndTheRestIsNested
    @StringSemantics(Semantics.MavenCoordinates)
    @SchemaDoc("Coordinates of the maven plugin")
    val coordinates by value<String>()
}

@CustomSchemaDeclaration(MavenMojoSettings::class)
class MavenPluginSettings : SchemaNode()

class MavenMojoSettings : SchemaNode() {

    @Shorthand
    @SchemaDoc("Enabled corresponding maven mojo execution")
    val enabled by value(default = false)
    
    @SchemaDoc("The list of dependencies added to the classpath of the maven mojo execution")
    val dependencies by nullableValue<List<UnscopedExternalMavenDependency>>(default = emptyList())

    @SchemaDoc("The configuration for mojo execution")
    val configuration by nullableValue<MavenMojoConfiguration>()
    
}

@CustomSchemaDeclaration
class MavenMojoConfiguration : SchemaNode()