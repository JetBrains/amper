/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.api.SchemaNode

abstract class BuiltinSchemaObjectDeclarationBase<T : SchemaNode> : SchemaObjectDeclarationBase() {
    abstract override fun createInstance(): T
}

abstract class BuiltinSchemaEnumDeclarationBase<E : Enum<E>> : SchemaEnumDeclarationBase() {
    abstract override fun toEnumConstant(name: String): E
}

abstract class BuiltinVariantDeclarationBase<T : SchemaNode> : SchemaVariantDeclaration