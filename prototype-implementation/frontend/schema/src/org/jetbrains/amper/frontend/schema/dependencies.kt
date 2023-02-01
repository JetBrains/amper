/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.SchemaBase
import java.nio.file.Path

class Dependencies : SchemaBase() {
    val deps = value<Collection<Dependency>>()
}

// TODO Add scopes.
sealed class Dependency : SchemaBase()

class ExternalMavenDependency : Dependency() {
    val coordinates = value<String>()
}

class InternalDependency  : Dependency() {
    val path = value<Path>()
}