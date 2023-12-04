/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.*

enum class ProductType(
    vararg val defaultPlatforms: Platform
) {
    LIB(Platform.COMMON);
}

class ModuleProduct  : SchemaNode() {
    val type = value<ProductType>()
    val platforms = nullableValue<Collection<Platform>>()
}