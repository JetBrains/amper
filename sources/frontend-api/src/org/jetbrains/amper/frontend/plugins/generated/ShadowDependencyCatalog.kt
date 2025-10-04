/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins.generated

import org.jetbrains.amper.frontend.api.FromKeyAndTheRestIsNested

/**
 * A version catalog dependency node for [ShadowDependency].
 */
class ShadowDependencyCatalog : ShadowDependency() {
    @FromKeyAndTheRestIsNested
    val catalogKey: String by value()
}