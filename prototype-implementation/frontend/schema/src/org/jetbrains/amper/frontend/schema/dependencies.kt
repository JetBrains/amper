/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.SchemaNode
import java.nio.file.Path

// TODO Add scopes.
sealed class Dependency : SchemaNode()

class ExternalMavenDependency : Dependency() {
    val coordinates = value<String>()
}

class InternalDependency  : Dependency() {
    val path = value<Path>()
}