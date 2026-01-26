/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.FromKeyAndTheRestIsNested
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.StringSemantics
import org.jetbrains.amper.frontend.types.SchemaType.StringType.Semantics

class MavenPlugin : SchemaNode() {
    
    @FromKeyAndTheRestIsNested
    @StringSemantics(Semantics.MavenCoordinates)
    @SchemaDoc("Coordinates of the maven plugin")
    val coordinates by value<String>()

    @ModifierAware
    @SchemaDoc("The list of dependencies added to the classpath of the maven mojos executions")
    val dependencies by nullableValue<List<UnscopedExternalMavenDependency>>(default = emptyList())
    
}