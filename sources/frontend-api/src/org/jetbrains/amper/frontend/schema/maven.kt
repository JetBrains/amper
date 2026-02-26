/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

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

class MavenPluginSettings : SchemaNode() {

    // This field is added dynamically in the schema after loading of maven plugins.
    // This is done because for every mojo schema definition for [MavenMojoSettings] differs.
    // Keys are strictly defined as "${plugin.id}.${mojo.name}".
    //
    // val mojos by value<Map<String, MavenMojoSettings>>(default = emptyMap())
}

class MavenMojoSettings : SchemaNode() {

    @Shorthand
    @SchemaDoc("Enabled corresponding maven mojo execution")
    val enabled by value(default = false)
    
    @SchemaDoc("The list of dependencies added to the classpath of the maven mojo execution")
    val dependencies by nullableValue<List<UnscopedExternalMavenDependency>>(default = emptyList())
    
    // This field is added dynamically in the schema after loading of maven plugins.
    // This is done because for every mojo this type differs.
    //
    // val configuration by nullableValue<SchemaNode>()
}