/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.customTaskSchema

import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode

class PublishArtifactNode: SchemaNode() {
    @SchemaDoc("Wildcard that should match one and only file in task output")
    var path by value<String>()

    @SchemaDoc("Maven artifact id. Group and version will be reused from current module")
    var artifactId by value<String>()

    @SchemaDoc("Maven artifact classifier")
    var classifier by value<String>()

    @SchemaDoc("Maven artifact extension")
    var extension by value<String>()
}
